<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

> **Decision:** Decided by the user on 2026-09-27: execute without the optional fsync step.
# 38 — Import without reading back and without a full rescan

| | |
|---|---|
| Lane | E |
| Impact | medium (every import; large for a backup restored into a big library) |
| Confidence | high |
| Platforms | all |
| Files | `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/SongLocalSource.kt` (KDoc), `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/SongLocalSourceImpl.kt`, `data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SongRepository.kt`, `…/SetlistRepository.kt`, `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SongRepositoryImpl.kt`, `…/SetlistRepositoryImpl.kt`, `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImpl.kt`, `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlanner.kt`, the repository fakes in `domain/implementation/src/commonTest/…/useCases/*Test.kt` and `data/repository/implementation/src/commonTest/…/sync/FakeSyncCollaborators.kt`, `domain/implementation/src/commonTest/…/useCases/ImportFilesUseCaseImplTest.kt`, `data/repository/implementation/CLAUDE.md`, `domain/implementation/CLAUDE.md` |
| Depends on / conflicts with | Conflicts with 37 (same repository interfaces and fakes; land one after the other). Independent of 34 (the scan path). |
| Commit message | `Put imported songs and setlists into the lists directly instead of reading them back and rescanning the library.` |

## Problem
**Each imported song is read back and parsed right after it is written.**
- `SongLocalSourceImpl.kt:98-102`:
  ```kotlin
  fileStorage.writeText(StorageDirectory.SONGS, name, text)
  return loadSong(name) ?: throw IllegalStateException(…)   // info + readText + ChordProParser.summarize
  ```
- `ImportFilesUseCaseImpl.kt:60-64` uses only `.fileName` of the result.

**Then the whole library is rescanned.**
- `ImportFilesUseCaseImpl.kt:104-116`, in `finally`: `songRepository.rescan()`, which also drops every cached text, and `setlistRepository.rescan()`.
- The comment there says one rescan beats "one cache update per file". That is true of k separate O(n) `updateData` calls, each rebuilding the song list downstream. It is not true of one merge.

**Cost:**
- "Open with" on a single `.cho` into a 2000-song library re-reads and re-parses all 2000 files.
- A 2000-song backup costs 2000 sequential cycles of write, stat, read and parse, and then a 4000-file rescan.
- Planning a re-import of a backup reads library texts one at a time: `ImportPlanner.kt:239-242` (`readSongFamily`, `candidates.forEach { … readLibraryText(fileName) … }`), called once per family from `planSongs`.

## Fix
1. **Build the song from the text in memory** in `SongLocalSourceImpl.importSong`:
   ```kotlin
   fileStorage.writeText(StorageDirectory.SONGS, name, text)
   // What was just written, without asking the storage for it back: the text is the file, and the size is its UTF-8 length.
   return StoredFileInfo(name = name, size = text.encodeToByteArray().size.toLong(), lastModified = Clock.System.now().toEpochMilliseconds())
       .toSong(ChordProParser.summarize(text))
   ```
   - `Song.lastModified` is read nowhere outside `:data` (checked).
   - The "disappeared right after it was written" exception goes away with the read.
2. **Merge the results into the lists once, at the end of the import.** Add to `SongRepository` and `SetlistRepository`:
   ```kotlin
   /** Puts files an import wrote into the list, replacing any of the same name, in one change. */
   suspend fun adoptImported(songs: Collection<Song>)   // SetlistRepository: adoptImported(setlists: Collection<Setlist>)
   // Impl:
   val names = songs.mapTo(hashSetOf()) { it.fileName }
   names.forEach { songContentRepository.invalidate(it) }   // only a replaced file can have a cached text; see 37 for a batch invalidate
   updateData { current -> current.orEmpty().filterNot { it.fileName in names } + songs }
   ```
   - `importSetlist` already returns the saved `Setlist` with its size (`SetlistLocalSourceImpl.saveSetlist`).
   - `ImportFilesUseCaseImpl` collects the returned `Song`s and `Setlist`s next to the file names it already collects, and its `finally` calls `adoptImported(...)` in place of `rescan()`, still under `NonCancellable`, still after a failure or cancellation halfway.
   - Files written before the failure are the ones collected, which is exactly what the rescan was there to pick up.
3. **Read each planned family's library texts in parallel** in `readSongFamily`. Prefetch with `coroutineScope { candidates.map { async { it to readLibraryText(it) } }.awaitAll() }` and keep the existing loop, including the `takenFileName` early skip, over the prefetched map.
   - Or, simpler and bounded: prefetch for all families at once in `planSongs` in chunks of 64.
   - The planning result must be identical: `ImportPlannerTest` covers it.
4. **Optional, DECISION NEEDED: fsync.**
   - `JvmFileStorage.kt:141` forces every write to the device (`FileChannel.open(temporary, WRITE).use { it.force(true) }`). A 2000-file import on a phone pays 2000 serial flushes of a few ms each.
   - Skipping the force for a file that did not exist before the import loses nothing that is not still in the import's source. The force is what protects an *existing* song from a torn write.
   - This changes a documented durability rule ("flushed to the device before the rename", `data/source/local/implementation/CLAUDE.md`), so it is left out unless it is explicitly approved. If approved, it is a separate `writeText(..., isNew = true)` path touching `JvmFileStorage` (both copies) and must be its own commit.
5. **Must not change:**
   - Names, numbering, conflict handling and replacement stay exactly as they are.
   - The demo library planting goes through the same import and must still appear.

## Verification
- Tests: `./gradlew :domain:implementation:desktopTest :data:repository:implementation:desktopTest :data:source:local:implementation:desktopTest`. `ImportFilesUseCaseImplTest`:
  - Assert that an import calls `adoptImported` with the written songs and never `rescan()`.
  - Assert that a failure on the third file still adopts the first two.
  - A replaced song's cached text is invalidated.
- **Manual checks:**
  - Desktop with about 2000 songs: open one `.cho` with the app and confirm with log timestamps that no scan follows.
  - Import an exported 2000-song archive into an empty library and compare wall time before and after.
  - A fresh install still plants the demo library.
- **Docs:** replace "a `rescan()` — the refresh action, and the last step of every import" in `data/repository/implementation/CLAUDE.md`, and the import paragraph in `domain/implementation/CLAUDE.md`.
