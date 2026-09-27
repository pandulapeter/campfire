<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 34 — One-pass web library scan

| | |
|---|---|
| Lane | E |
| Impact | medium-high |
| Confidence | medium (the round-trip count is certain; per-call cost varies by browser) |
| Platforms | web (the common API change is a no-op elsewhere) |
| Files | `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.kt`, `…/wasmJsMain/…/storage/file/FileStorage.wasmJs.kt`, `…/commonMain/…/source/SongLocalSourceImpl.kt`, `…/commonMain/…/source/SetlistLocalSourceImpl.kt`, `data/source/local/implementation/CLAUDE.md` |
| Depends on / conflicts with | Depends on 33 (reuses its `readFileText` JS helper). Conflicts with 41 (same wasm file). |
| Commit message | `Read the web library in batches of one browser call each instead of four calls per file.` |

## Problem
**The listing opens every file, one after another.**
- `FileStorage.wasmJs.kt:200-213`:
  ```js
  for await (var entry of directory.entries()) {
      if (entry[1].kind === 'file') { var file; try { file = await entry[1].getFile(); } … }
  ```
- Each `getFile()` is its own round trip to the browser's storage backend, and each one waits for the previous.
- This happens before the scan reads a single song (`SongLocalSourceImpl.kt:59`: `fileStorage.list(StorageDirectory.SONGS)`).

**Then each file is looked up again by name.**
- `readText` (`FileStorage.wasmJs.kt:82-86`) → `fileHandle` (135-138) → `getFileHandle(...)`, then `getFile()` and `arrayBuffer()` in `readFileBytes` (241-248).
- That is one `withContext` hop and one `await` per step, per file.

**Cost:** for 2000 songs, about 8000 promise round trips on the only thread before the list is complete. The cold start pays this once and every rescan pays it again (refresh, the end of an import, the end of a sync run, live rescans during a run). The setlist scan (`SetlistLocalSourceImpl.kt:37-58`) does the same thing, one file at a time.

## Fix
1. **In `listEntries`, open the files in parallel:** collect the handles first, then `await Promise.all(handles.map(h => h.getFile().catch(...)))`, keeping the per-entry `NotFoundError` skip. The output format stays the same.
2. **Add a batch read to `FileStorage`**, with a common default so the JVM and iOS storages are unchanged:
   ```kotlin
   sealed interface BatchRead { data class Text(val text: String) : BatchRead; data object Missing : BatchRead; class Failed(val cause: Exception) : BatchRead }
   /** One answer per name, in order. A failed file is its own answer, never the batch's. */
   suspend fun readTexts(directory: StorageDirectory, names: List<String>): List<BatchRead> = coroutineScope {
       names.map { name -> async { try { readText(directory, name)?.let(BatchRead::Text) ?: BatchRead.Missing } catch (e: CancellationException) { throw e } catch (e: Exception) { BatchRead.Failed(e) } } }.awaitAll()
   }
   ```
3. **Override it in `OpfsFileStorage` with a single `js()` call per batch.**
   - The call runs `Promise.all(names.map(n => dir.getFileHandle(n).then(readFileText /* from 33 */)))`.
   - Each item's rejection becomes a per-item marker, so a failure never rejects the whole batch.
   - It returns a `JsArray<JsString?>` whose elements carry the 33 prefix convention (`\u0000` text, `\u0001` fall back, `\u0002`+message failed, null missing). Reading the array costs one boundary call per file, not per byte.
   - Items marked "fall back" go through `readBytes(...)?.decodeLibraryText()` one by one; that is only the non-UTF-8 files.
4. **`SongLocalSourceImpl.loadSongs`** keeps its batches of `BATCH_SIZE` (64) and its doubling `onProgress`, but calls `fileStorage.readTexts(SONGS, batch.map { it.name })`.
   - The size check (`size > MAX_TEXT_FILE_SIZE` → skip with a log line) happens before a name goes into a batch.
   - `Failed` is logged and skipped exactly as `readSong`'s catch does now.
   - Parsing (`ChordProParser.summarize`) stays on `Dispatchers.Default`.
5. **`SetlistLocalSourceImpl.loadSetlists`** uses `readTexts` for all its files at once; setlists are few.
6. **Must not change:**
   - A file that is there and cannot be read is skipped and logged. It is never reported as missing: sync depends on that distinction, and this path is the scan, not sync.
   - The scan is still bounded to 64 concurrent reads.
   - Partial publishing still happens only on the first read (`BaseLocalDataRepository.publishPartialData`).

## Verification
- Build: `./gradlew :data:source:local:implementation:desktopTest` (the JVM storage goes through the default `readTexts`), then `./gradlew :app:web:wasmJsBrowserDistribution`.
- **Manual checks** on the web build with about 2000 songs:
  - In DevTools' Performance panel, compare the time from reload to a complete song list, before and after.
  - Put an undecodable setlist JSON and a Windows-1252 song into OPFS: the setlist is skipped, the song reads correctly.
  - Delete a file from another tab's console while a rescan runs: it is left out, and the scan does not fail.
- **Docs:** add `readTexts` to the `FileStorage` bullet in `data/source/local/implementation/CLAUDE.md` (why it exists: one browser call per batch).
