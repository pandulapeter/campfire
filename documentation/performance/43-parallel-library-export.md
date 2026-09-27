<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 43 — Read the library export in parallel, off the main thread

| | |
|---|---|
| Lane | E |
| Impact | low-medium (library export only; large on the web) |
| Confidence | high |
| Platforms | all |
| Files | `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportLibraryUseCaseImpl.kt`, `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ExportLibraryUseCaseImplTest.kt` |
| Depends on / conflicts with | — (benefits from 33 on the web) |
| Commit message | `Read the songs of a library export in parallel batches on a background thread.` |

## Problem
**The export reads one song at a time.**
- `ExportLibraryUseCaseImpl.kt:46-55`:
  ```kotlin
  songFileSizes.keys.sorted().forEach { fileName ->
      val content = if (… > MAX_TEXT_FILE_SIZE) null else songContentRepository.loadSongContent(fileName, shouldCache = false)
      if (content == null) skipped += fileName else put("$SONGS_DIRECTORY/$fileName", content.text.encodeToByteArray())
  }
  ```
- The setlists after it (56-59) are read the same way.

**It runs on Main between reads.**
- The use case has no `withContext`. It is called from `viewModelScope` (`CampfireViewModel.kt:1877-1885`), so every iteration resumes on Main.
- The UTF-8 encoding of each text (about 6 MB in total for 2000 songs) runs there too.

**Cost:** 2000 sequential storage round trips. On the web each is several promise round trips plus the per-byte copy (plan 33). Only `pack` (`ArchiveLocalSourceImpl`) moves to `Dispatchers.Default`.

## Fix
```kotlin
override suspend operator fun invoke(): ExportLibraryUseCase.Result? = withContext(Dispatchers.Default) {
    …same guards…
    val names = songFileSizes.keys.sorted()
    val texts = names.chunked(READ_BATCH_SIZE).flatMap { batch ->   // 64, as the scan and the sync preparation use
        coroutineScope { batch.map { name -> async { name to if (songFileSizes.getValue(name) > ImportLimits.MAX_TEXT_FILE_SIZE) null else songContentRepository.loadSongContent(name, shouldCache = false) } }.awaitAll() }
    }
    val files = buildMap { texts.forEach { (name, content) -> if (content == null) skipped += name else put("$SONGS_DIRECTORY/$name", content.text.encodeToByteArray()) } … setlists as now … }
    …
}
```

- **Order:** the archive entries and `skippedFileNames` stay in the sorted order they have now. `ZipWriter` writes the map in insertion order, and the export test may compare it.
- **Safe to read concurrently:** `SongContentRepositoryImpl` only locks around its map, never around a read.
- **Must not change:**
  - "A scan that failed is not an empty library" (the `?: return null` guards).
  - Oversized files are skipped unopened.
  - `shouldCache = false`.

## Verification
- Tests: `./gradlew :domain:implementation:desktopTest`. `ExportLibraryUseCaseImplTest` must still pass unchanged. Add a case asserting entry order with a fake that answers out of order, by delaying earlier names.
- **Manual check:** export about 2000 songs on the web build and on Android, and time from tap to the save dialog before and after.
