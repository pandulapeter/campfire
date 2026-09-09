# Step 02: `FileStorage` for all platforms

**Goal:** one small file-system abstraction in `:data:source:local:implementation` with an `actual` for Android,
desktop, iOS and web (OPFS). It is the only place in the app that touches a file system. Nothing uses it yet.

**Depends on:** nothing. Can run in parallel with steps 01 and 03. Step 05 wires it in.

## 1. Where it lives

`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/`
already exists (Room lives next to it in `roomMain` until step 05). Add a new `file/` sub-package there so the two do
not collide:

- `storage/file/FileStorage.kt` (commonMain): the interface and the `expect` factory.
- `storage/file/FileStorage.android.kt` in `androidMain`, `FileStorage.desktop.kt` in `desktopMain`,
  `FileStorage.ios.kt` in `iosMain`, `FileStorage.wasmJs.kt` in `wasmJsMain`.

The module's `build.gradle.kts` still declares the `roomMain` hierarchy template. `androidMain`, `desktopMain`,
`iosMain` and `wasmJsMain` are all still valid leaf source sets under it, so no build file change is needed for this
step. (Step 05 deletes the template.)

## 2. The interface (commonMain)

```kotlin
package com.pandulapeter.campfire.data.source.local.implementation.storage.file

/** The folders the app keeps its data in. Each maps to one platform directory, created on first use. */
enum class StorageDirectory { SONGS, SETLISTS, PREFERENCES }

data class StoredFileInfo(
    val name: String,          // file name with extension, no path
    val size: Long,
    val lastModified: Long     // milliseconds since the epoch, 0 if the platform cannot tell
)

/**
 * Flat file access inside the app-private data directory. No sub-directories, no paths: every operation is
 * (directory, file name). Names are validated by the callers (see step 05), the storage only refuses names that
 * contain "/" or "\\" or are "." / "..", throwing IllegalArgumentException.
 *
 * All functions are suspend and run on Dispatchers.IO (or the wasm equivalent, see below); errors surface as
 * exceptions of the platform (IOException & co), callers map them to DataState.Failure.
 */
interface FileStorage {
    suspend fun list(directory: StorageDirectory): List<StoredFileInfo>
    suspend fun exists(directory: StorageDirectory, name: String): Boolean
    suspend fun readText(directory: StorageDirectory, name: String): String?     // null if missing
    suspend fun readBytes(directory: StorageDirectory, name: String): ByteArray? // null if missing
    suspend fun writeText(directory: StorageDirectory, name: String, text: String)   // create or overwrite, UTF-8
    suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray)
    suspend fun delete(directory: StorageDirectory, name: String)                 // no-op if missing
    suspend fun rename(directory: StorageDirectory, from: String, to: String)     // fails if `to` exists
}

internal expect fun org.koin.core.scope.Scope.createFileStorage(): FileStorage
```

Add to `commonMain`'s `Module.kt` of this module (the `expect val dataLocalSourceModule` stays as it is for now; the
binding goes into **both** actual modules, `roomMain` and `wasmJsMain`): `single<FileStorage> { createFileStorage() }`.

Text is always UTF-8. A UTF-8 BOM at the start of a read text file is stripped (Windows editors add one).

## 3. Platform directories

| Platform | Root | Sub-folders |
| --- | --- | --- |
| Android | `context.filesDir` (Koin: `get<Context>().applicationContext`, as `StorageManagerBuilder.android.kt` does today) | `library/songs`, `library/setlists`, `preferences` |
| Desktop | macOS: `~/Library/Application Support/Campfire`; Windows: `%APPDATA%\Campfire`; Linux: `$XDG_DATA_HOME/campfire` or `~/.local/share/campfire` (detect with `System.getProperty("os.name")`; `java.io.File` is fine in `desktopMain`) | same |
| iOS | `NSDocumentDirectory` (see `StorageManagerBuilder.ios.kt` for the lookup) for `library/…` so step 10 can expose it in the Files app; `NSApplicationSupportDirectory` (create it, `create = true`) for `preferences` | `library/songs`, `library/setlists` in Documents; `preferences` in Application Support |
| Web | OPFS root (`navigator.storage.getDirectory()`) | `library/songs`, `library/setlists`, `preferences` as nested directory handles |

Directories are created lazily (`mkdirs` / `createDirectory` / `getDirectoryHandle(name, {create: true})`).

## 4. Platform notes

**Android / desktop (JVM):** `java.io.File` + `withContext(Dispatchers.IO)`. Write atomically: write to `<name>.tmp`
in the same directory, then `renameTo` the target (delete the target first on Windows). `lastModified` from
`File.lastModified()`.

**iOS:** `NSFileManager` + `NSString.stringWithContentsOfFile` / `writeToFile(atomically = true, encoding = NSUTF8StringEncoding)`.
For bytes use `NSData.dataWithContentsOfFile` / `writeToFile` and convert with `usePinned` / `memcpy` (search the repo
history or the Kotlin docs for the standard `NSData <-> ByteArray` helpers and put them in `iosMain`). Run on
`Dispatchers.IO` (`import kotlinx.coroutines.IO` works on native). `lastModified` from
`attributesOfItemAtPath(...)[NSFileModificationDate] as NSDate` → `timeIntervalSince1970 * 1000`.

**Web (wasmJs):** Every OPFS call returns a JS `Promise`. Use `kotlin.js.Promise` and `kotlinx.coroutines.await()`
(available on wasmJs from `kotlinx-coroutines-core`, already a dependency). Declare the browser API through small
`external`/`js(...)` helpers in `wasmJsMain`, for example:

```kotlin
private fun getOpfsRoot(): Promise<JsAny?> = js("navigator.storage.getDirectory()")
private fun getDirectoryHandle(parent: JsAny, name: String): Promise<JsAny?> = js("parent.getDirectoryHandle(name, { create: true })")
private fun getFileHandle(parent: JsAny, name: String, create: Boolean): Promise<JsAny?> = js("parent.getFileHandle(name, { create: create })")
private fun readFileText(handle: JsAny): Promise<JsAny?> = js("handle.getFile().then(function (f) { return f.text(); })")
private fun writeFileText(handle: JsAny, text: String): Promise<JsAny?> = js("handle.createWritable().then(function (w) { return w.write(text).then(function () { return w.close(); }); })")
private fun listEntries(dir: JsAny): Promise<JsAny?> = js("(async function () { var names = []; for await (var [name, handle] of dir.entries()) { if (handle.kind === 'file') { var f = await handle.getFile(); names.push(name + '\\u0000' + f.size + '\\u0000' + f.lastModified); } } return names.join('\\u0001'); })()")
```

Bytes: read via `getFile().then(f => f.arrayBuffer())` into a `Uint8Array`, copy into a `ByteArray` with an
`org.khronos.webgl` loop (or `Int8Array`); write by building a `Uint8Array` from the `ByteArray`. `rename`: OPFS has
`FileSystemHandle.move()` only in Chromium; implement as read + write + delete. `readText` of a missing file: catch the
`NotFoundError` DOMException and return `null`. There is no `Dispatchers.IO` on wasm: run on `Dispatchers.Default`.

OPFS needs a secure context (https or `localhost`); the dev server is `localhost`, the production site is https.
`navigator.storage.getDirectory` missing → throw an `IllegalStateException("OPFS unavailable")`; step 07 turns that into
an error empty state on the Songs screen.

## 5. Sanity check before wiring

Add a `desktopTest` in this module (`data/source/local/implementation/src/desktopTest/kotlin/...`) that creates a
`FileStorage` against a temporary directory: to make that possible, structure the JVM implementation as
`internal class JvmFileStorage(private val root: java.io.File) : FileStorage` in `desktopMain` with the `actual`
factory just choosing the root, and put the Android one in `androidMain` as a copy (or share it through a small
`jvmMain`-like intermediate source set: **do not** do that now, the hierarchy template changes in step 05; a duplicated
100-line file is fine until then). Test: write, list, read, exists, rename, delete, missing file returns null, name
with `/` throws. Add `commonTest.dependencies { implementation(kotlin("test")) }` (the `desktopTest` source set inherits
it).

## Verify

- Full build command from the README succeeds for all four platforms.
- `./gradlew :data:source:local:implementation:desktopTest` passes.

## Execution notes

- **`build.gradle.kts` was touched** (unavoidable): `commonTest.dependencies { implementation(kotlin("test")) }` was added
  so that the `desktopTest` of section 5 compiles. Step 03 expects exactly this line and adds it only if it is missing.
- `StorageDirectory.pathSegments` (`listOf("library", "songs")` etc.) lives in `commonMain` and every `actual` joins it
  its own way, so the layout of the table in section 3 is declared once.
- **iOS text I/O goes through `NSData`**, not `NSString.stringWithContentsOfFile` /
  `writeToFile(atomically:encoding:)`: those are deprecated, and routing `readText` / `writeText` through
  `readBytes` / `writeBytes` plus `decodeToString()` / `encodeToByteArray()` keeps a single code path.
  `NSData.writeToFile(atomically = true)` already gives the atomic write the JVM implementation emulates by hand.
- **Web byte conversion** uses `ByteArray.toInt8Array()` / `Int8Array.toByteArray()` from `org.khronos.webgl`
  (kotlinx-browser, already a `wasmJsMain` dependency) instead of a hand-written loop.
- **Web "missing file"** is not handled by catching a `NotFoundError` `DOMException`: the `js(...)` helpers append
  `.catch(function () { return null; })`, so `getFileHandle` resolves to `null` and `removeEntry` becomes a no-op.
  The control characters separating the `listEntries` result are built with `String.fromCharCode` / `Char(0)` rather
  than escapes, so no literal control byte ends up in the Kotlin source.
- `list()` is sorted by name on every platform (the platform APIs give no order guarantee) and the JVM one hides the
  `.tmp` files its atomic write leaves behind if the process dies mid-write.
- The JVM `renameTo` has a `copyTo` fallback, because it fails on some Android storage and network shares.
- Koin: `single<FileStorage> { createFileStorage() }` was added to both `roomMain/Module.kt` and `wasmJsMain/Module.kt`.
- `./gradlew :data:source:local:implementation:build` is green (Android, desktop, both iOS targets, wasmJs) and the
  10 tests of `JvmFileStorageTest` pass.
