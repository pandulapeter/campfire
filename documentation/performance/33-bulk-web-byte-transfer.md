<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 33 — Bulk web byte and text transfer

| | |
|---|---|
| Lane | E |
| Impact | high |
| Confidence | high (mechanism verified in the library source); medium (size of the win, not measured) |
| Platforms | web |
| Files | `data/source/local/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.wasmJs.kt`, `data/source/local/implementation/CLAUDE.md` |
| Depends on / conflicts with | Conflicts with 34 and 41 (same file). Land 33 first; 34 builds on its text reader. |
| Commit message | `Move file contents across the Wasm boundary in bulk instead of one byte at a time.` |

## Problem
The OPFS storage converts every file it reads or writes with the kotlinx-browser helpers:

- `FileStorage.wasmJs.kt:84` (`readText`): `readFileBytes(it).await()?.toByteArray()?.decodeLibraryText()`
- `FileStorage.wasmJs.kt:90` (`readBytes`): `readFileBytes(it).await()?.toByteArray()`
- `FileStorage.wasmJs.kt:100` (`writeBytes`): `bytes.toInt8Array()`

**Verified in kotlinx-browser 0.5.0**, the version this project uses (`libs.kotlin.browser`):
- The klib at `~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-browser-wasm-js/0.5.0/…/kotlinx-browser-wasm-js-0.5.0.klib` lists `arrayCopy.wasm.kt` and `getMethodImplForInt8Array` in its IR.
- The matching sources jar has:
  ```kotlin
  // wasmJsMain/arrayCopy.wasm.kt:28-38
  public actual fun Int8Array.toByteArray(): ByteArray = ByteArray(this.length) { this[it] }
  public actual fun ByteArray.toInt8Array(): Int8Array { … for (index in this.indices) result[index] = this[index] … }
  // wasmJsMain/org.w3c/org.khronos.webgl.kt:934-939
  internal fun getMethodImplForInt8Array(obj: Int8Array, index: Int): Byte = js("obj[index]")
  internal fun setMethodImplForInt8Array(obj: Int8Array, index: Int, value: Byte) { js("obj[index] = value;") }
  ```
- So every byte is one call from Wasm into JavaScript.

**How often it runs, for a library of about 2000 songs at about 3 KB each (roughly 6 MB):**
- About 6 M boundary calls on every cold scan and every rescan (`SongLocalSourceImpl.readSong` → `readText`).
- The same again on every sync run's preparation (`SyncEngine.readLocalState` → `LibraryFileLocalSource.readLibraryFile` → `readBytes`).
- Again for every sync download (`writeLibraryFile` → `writeBytes`).
- All of it on the page's only thread, the one that also draws the UI.
- At tens of nanoseconds per call, that is on the order of 0.1–0.3 s per full pass, spent on copying alone.

## Fix
1. **Text reads: decode in JavaScript and hand one string across.**
   - Add a `js()` function for this:
     ```js
     // readFileText(handle): Promise<JsString?>
     // null = not found (NotFoundError, as readFileBytes does); "" prefix convention below for the fallback
     handle.getFile().then(f => f.arrayBuffer()).then(buffer => {
       var text;
       try { text = new TextDecoder('utf-8', { fatal: true }).decode(buffer); }   // strips one leading BOM
       catch (e) { return '\u0001'; }                    // not valid UTF-8: Kotlin falls back to the byte path
       return text.indexOf('\u0000') >= 0 ? '\u0001' : '\u0000' + text;   // NUL anywhere: may be BOM-less UTF-16
     }).catch(error => { if (error && error.name === 'NotFoundError') return null; throw error; })
     ```
   - In Kotlin, `readText` then does:
     - null → null;
     - a string starting with `\u0000` → `drop(1).trimStart('﻿')`;
     - `\u0001` → the existing `readBytes(...)?.decodeLibraryText()` path, which is then only used for code-page and UTF-16 files.
   - This must give exactly what `decodeLibraryText` gives today:
     - Valid UTF-8 without a NUL takes the `invalid == 0 -> decodeToString()` branch there, and TextDecoder produces the same string.
     - Any NUL sends the file back to the Kotlin path, which is where `utf16ByteOrder` decides.
     - `.trimStart('﻿')` covers a doubled BOM, since TextDecoder strips only one.
   - A JsString → String conversion is a single bulk copy.
2. **Byte reads and writes: stop using `toByteArray()` / `toInt8Array()`.**
   - Carry the bytes across as a Latin-1 JS string (one UTF-16 code unit per byte), built and taken apart in chunks on the JS side:
     ```js
     // bytesToLatin1(u8): for (i = 0; i < n; i += 0x8000) parts.push(String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000))); return parts.join('')
     // latin1ToBytes(s): var u8 = new Uint8Array(s.length); for (i…) u8[i] = s.charCodeAt(i); return u8
     ```
     ```kotlin
     private fun JsString.latin1Bytes(): ByteArray = toString().let { s -> ByteArray(s.length) { s[it].code.toByte() } }   // loop stays in Wasm
     private fun ByteArray.toLatin1JsString(): JsString = CharArray(size) { (this[it].toInt() and 0xFF).toChar() }.concatToString().toJsString()
     ```
   - A Wasm linear-memory view (`kotlin.wasm.unsafe`) would be faster still, but it depends on reaching the module's `memory` export from `js()`. Take the string route unless measuring shows it is not enough.
3. **Leave the rest alone:**
   - The null-vs-throw contract (null only for `NotFoundError`, everything else `LibraryStorageException` through `failingAsStorage`).
   - The fallback worker path's input shape (it takes bytes; text still goes in as a `JsString`).
   - The file format: text is still written as UTF-8.
4. **Out of this plan's files:** `presentation/src/wasmJsMain/.../FilePicker.wasmJs.kt:48,159` uses the same two helpers for exports and imported files. Note it for the presentation lane rather than editing it here.

## Verification
- Build: `./gradlew :app:web:wasmJsBrowserDevelopmentRun`. Then `./gradlew :data:source:local:implementation:desktopTest`, since the common `decodeLibraryText` tests must still pass.
- **Manual checks** (web build, a library of about 2000 songs, e.g. an exported archive imported twice under different names):
  - Measure time to a full list from a cold reload with the Performance panel, before and after.
  - Import a Windows-1252 `.cho`, a Hungarian Windows-1250 one, a UTF-16LE one without a BOM and one with a doubled BOM. Each must read exactly as before.
  - Run a sync download on a Dropbox test account and check the downloaded bytes hash the same.
- **Docs:** update the "Text is written as UTF-8 and read through `decodeLibraryText`" bullet in `data/source/local/implementation/CLAUDE.md` so it says the web decodes valid UTF-8 in the browser and falls back to `decodeLibraryText` otherwise.
