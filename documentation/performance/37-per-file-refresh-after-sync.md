<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 37 — Refresh only the files a sync run touched

| | |
|---|---|
| Lane | E |
| Impact | medium |
| Confidence | high |
| Platforms | all |
| Files | `data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SongRepository.kt`, `…/SetlistRepository.kt`, `…/SongContentRepository.kt`, `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SongRepositoryImpl.kt`, `…/SetlistRepositoryImpl.kt`, `…/SongContentRepositoryImpl.kt`, `…/SyncRepositoryImpl.kt`, `…/sync/SyncEngine.kt`, `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/SetlistLocalSource.kt` (only if a single-file read with size is missing; `loadSetlist` exists), the fakes that implement the three repository interfaces (`data/repository/implementation/src/commonTest/…/sync/FakeSyncCollaborators.kt`, `domain/implementation/src/commonTest/…/useCases/{ImportFilesUseCaseImplTest,GetScreenDataUseCaseImplTest,ExportSetlistUseCaseImplTest,ExportSongsUseCaseImplTest,ExportLibraryUseCaseImplTest,SongReferencesTest}.kt`), `data/repository/implementation/src/commonTest/…/SyncRepositoryImplTest.kt`, `…/LiveRescanPauseTest.kt`, `data/repository/implementation/CLAUDE.md` |
| Depends on / conflicts with | **Coordinate with 23** (desktop/iOS rescan on resume moves to ON_START with a throttle; that is the remaining full rescan besides the refresh action, and must keep calling `rescan()`). **Coordinate with 24** (changes how `CampfireViewModel._songTexts` is read again after an invalidation; this plan changes how many invalidation events a run sends, see step 3). Conflicts with 38 (same repository interfaces and fakes; land one after the other), 35 and 39 (`SyncRepositoryImpl.kt`) and 36 (`SyncEngine.kt`). |
| Commit message | `Refresh only the files a sync run changed instead of rescanning the whole library.` |

## Problem
**A run that changed anything rescans the whole library.**
- `SyncRepositoryImpl.kt:466-469`: `if (result.summary.hasChanges) rescanLibraryAfterRun()`.
- The same happens after a deletion question (441-443) and in `finishRunCutShort` (see `rescanLibraryAfterRun`, 595-598).
- `rescanLibrary()` (585-588) is `songRepository.rescan(); setlistRepository.rescan()`.
- `SongRepositoryImpl.rescan()` (47-50) is `songContentRepository.invalidate()`, which drops **every** cached text and emits `null`, followed by `reloadData()`.

**Live rescans during a run do the same.**
- `scheduleLiveRescan` (522-530) re-reads the whole library during a run, throttled so it takes about a sixth of the run's time.
- Its KDoc gives the reason: "there is no per file way into the cache".

**Cost, for about 2000 songs:**
- Downloading one edited song re-reads and re-parses 2000 files.
- The `null` invalidation makes `CampfireViewModel` (886-889) read again every text it holds, including the open song.
- A first sync of a whole library repeats full rescans all through the run. On the web that shares the thread with the UI.

## Fix
1. **The engine reports the local files it changed.** `SyncEngine` collects a `SyncKey` at each place it changes a library file:
   - the download write (`SyncEngine.kt:489`);
   - the local deletion (519);
   - the conflict copy (603, the name `writeLibraryFileToFreeName` returns);
   - taking a copy back (652).
   - Not 702: that removes a non-library file, which no repository lists.

   Add `onLocalFilesChanged: (Set<SyncKey>) -> Unit` to `synchronize`, called after each operation's outcome next to `onIndexChanged`, or carry the keys in `OperationOutcome` and hand them out the same way.
2. **Repositories get a per-file refresh:**
   ```kotlin
   // SongRepository
   /** Reads these files again - and only these - after something outside the repository changed them. */
   suspend fun refresh(fileNames: Set<String>)
   // SongRepositoryImpl
   override suspend fun refresh(fileNames: Set<String>) {
       val reloaded = coroutineScope { fileNames.map { async { it to songLocalSource.loadSong(it) } }.awaitAll() }  // ≤ a run's batch
       songContentRepository.invalidate(fileNames)
       updateData { current -> current.orEmpty().filterNot { it.fileName in fileNames } + reloaded.mapNotNull { it.second } }
   }
   ```
   - The same for `SetlistRepositoryImpl`, using `setlistLocalSource.loadSetlist`. A file that no longer reads, or no longer decodes, drops out, as the scan would drop it.
   - `updateData` already handles a full read in progress (the read goes again), so this composes with an explicit refresh or with plan 23's resume rescan.
3. **Invalidate several names in one step:** add `SongContentRepository.invalidate(fileNames: Set<String>)`.
   - `_invalidations` has `extraBufferCapacity = 64` and is fed with `tryEmit`, so emitting one event per name for a large batch **drops events** when the ViewModel is slow.
   - Emit per name up to a small batch (say 32), and `null` ("everything") above it.
   - Keep the flow's type, so plan 24's collector is unaffected. If 24 prefers a `Set<String>` event, change the flow type there and say so in both plans.
4. **`SyncRepositoryImpl`:**
   - Accumulate the reported keys in a set guarded by a `Mutex`.
   - `scheduleLiveRescan` becomes "flush": take the set and call `songRepository.refresh(songNames)` / `setlistRepository.refresh(setlistNames)`. Keep the throttle; `liveRescanPauseAfter` still applies, now to a cost proportional to the batch.
   - `rescanLibraryAfterRun` becomes "cancel the live flush, then flush the rest", and runs where it runs now (completed with changes, deletion question after finished operations, `finishRunCutShort`).
   - Keep a full `rescanLibrary()` as the fallback only when the engine could not say what it touched: a run that ended in an exception thrown past the collection.
5. **Must not change:**
   - Explicit refresh, the end of an import (see 38) and resume (23) still do full rescans.
   - The lists still key by file name and replace rather than append.
   - The caches must still catch up after a cut-short run (the reason `finishRunCutShort` exists).

## Verification
- Tests: `./gradlew :data:repository:implementation:desktopTest :domain:implementation:desktopTest`.
  - `SyncEngineTest`: download, local deletion, conflict copy and copy-taken-back each report their key.
  - `SyncRepositoryImplTest`: a completed run with one download calls `refresh({that song})` and never `rescan()`, and a cut-short run refreshes what finished.
  - Update `LiveRescanPauseTest` if the helper's contract moves.
- **Manual check:** a connected desktop with about 2000 songs. Edit one song on another device, sync, and confirm with timestamps in the log that the post-run step takes milliseconds rather than a full scan. The open song updates in place.
- **Docs:** rewrite the live-rescan and "tells the song and setlist repositories to rescan afterwards" sentences in `data/repository/implementation/CLAUDE.md`, and the `scheduleLiveRescan` / `rescanLibrary` KDoc.
