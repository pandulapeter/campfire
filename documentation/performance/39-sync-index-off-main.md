<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 39 — Decode and encode the sync index off the main thread

| | |
|---|---|
| Lane | E |
| Impact | medium (Android, iOS, desktop start-up while connected) |
| Confidence | medium (the thread is certain; the cost grows with the library) |
| Platforms | Android, iOS, desktop (the web has one thread either way) |
| Files | `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt` |
| Depends on / conflicts with | Conflicts with 35 and 37 (same file, different functions). |
| Commit message | `Decode and encode the sync index on a background thread.` |

## Problem
**The whole index is decoded on the main thread at start-up.**
- `SyncRepositoryImpl.restore()` (134) → `restoreConnection()` → line 208: `val document = loadIndexOrNull() ?: SyncIndexDocument()`.
- That is `loadIndex()` (745-755): `json.decodeFromString<SyncIndexDocument>(text)`, with no dispatcher switch.
- `restore()` is called from `viewModelScope` (`CampfireViewModel.kt:858-870`), which is `Dispatchers.Main`. Only the file read inside moves off it.

**Only two small fields are needed.** Line 210 onward uses just `lastSyncedAt` and `isRunInProgress`, but the document holds an entry per library file: path, a 64-character hash and a revision, about 150 B each.
- For about 2000 files that is about 300 KB of JSON decoded into a `Map` on the main thread during the first frames of every connected launch.
- When the run marker is set, it is encoded again on Main as well (line 214, `saveIndexQuietly` → `saveIndex`, 767: `json.encodeToString(document)`).
- The other callers (`connect`, 685, and the run itself) already run on the repository's `Dispatchers.Default` scope, or are rare.

## Fix
- Move the JSON work into `withContext(Dispatchers.Default)` in the two helpers every path goes through:
  ```kotlin
  private suspend fun loadIndex(): SyncIndexDocument {
      val text = syncStateLocalSource.loadSyncIndex() ?: return SyncIndexDocument()
      return withContext(Dispatchers.Default) { try { json.decodeFromString<SyncIndexDocument>(text) } catch (…) { … as now … } }
  }
  private suspend fun saveIndex(document: SyncIndexDocument) =
      syncStateLocalSource.saveSyncIndex(withContext(Dispatchers.Default) { json.encodeToString(document) })
  ```
- Keep the `CancellationException` rethrow and the "does not decode → no index" rule exactly as they are. `Dispatchers` is already imported in this file.
- Not proposed: splitting `lastSyncedAt` and `isRunInProgress` into a separate small file. That would change the on-disk format for an advantage this fix already delivers.

## Verification
- Tests: `./gradlew :data:repository:implementation:desktopTest`. `SyncRepositoryImplTest` exercises `restore` and the index writes. Check it does not depend on the decode happening on the test dispatcher; if a test uses virtual time around `restore`, it must still pass.
- **Manual check:** Android profile build, connected, index for about 2000 files. In a system trace (Perfetto) of a cold start, the `decodeFromString` slice must now be on a `DefaultDispatcher` worker rather than on `main`.
