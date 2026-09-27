<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 27 — Open the song picker without sorting and normalizing the library on the main thread

| | |
|---|---|
| Lane | D |
| Impact | medium: a hitch on the frame the sheet opens, growing with the library |
| Confidence | high |
| Platforms | all (worst on Wasm and Kotlin/Native) |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt |
| Depends on / conflicts with | 18 (moves `indexedSongs` off the main thread and edits `SearchIndex.kt`; build on it). 23 and 24 also edit `CampfireViewModel.kt`, in other regions. |
| Commit message | `Keep the song picker's alphabetical order and search keys ready in the view model.` |

## Problem
`dialogs/Dialogs.kt:1200-1216`, in `SongPicker`'s first composition:
```kotlin
val pickableSongs = remember(songs, initialSongFileNames) {
    val pickable = songs.map { song ->
        PickableSong(
            song = song,
            title = viewModel.normalizeForSearch(song.title),
            artist = viewModel.normalizeForSearch(song.artist),
            tags = song.tags.mapTo(mutableSetOf()) { it.lowercase() },
            searchableTags = song.tags.map { viewModel.normalizeForSearch(it) },
            ...
    initialSongFileNames.mapNotNull { pickableByFileName[it] } +
            pickable.filterNot { it.song.fileName in initial }
                .sortedWith(compareBy({ viewModel.normalize(it.song.title) }, { viewModel.normalize(it.song.artist) }, { it.song.fileName }))
}
```
- The comparator runs `NormalizeTextUseCase` (`trim().lowercase()` plus a scan, allocating) on **both sides of every comparison**. For 2,000 songs that is about 2 × 2 × n·log₂n ≈ 88k calls.
- On top of that come 3+ `normalizeForSearch` calls per song.
- All of it runs on the main thread in the composition that opens the sheet, so the sheet's first frame waits for it.
- It is recomputed whenever `allSongs` changes while the sheet is open.
- It duplicates work already done. The ViewModel's `SongSearchIndex` (`SearchIndex.kt`) already holds `normalizeSearchText` of the title, artist and every tag for every song (`SearchableSong`), and `songsByFileName` / `indexedSongs.search.byFileName` expose them.

`SetlistPicker` (`:1090-1094`) has the same comparator pattern:
```kotlin
.sortedWith(compareBy({ viewModel.normalize(it.title) }, { it.fileName }))
```
It only runs over the setlists, so n is small there.

## Fix
1. **Add the picker's ordering to the ViewModel, next to `indexedSongs`.** Compute it off the main thread, as plan 18 does for the other library-sized states. Build it eagerly like every other state (see `asState`'s KDoc for why), so the sheet's first frame already has it and nothing pops in.
   ```kotlin
   /** Every song of the library alphabetically, with its search keys, as the song picker lists and searches it. */
   internal val alphabeticalSongs = indexedSongs
       .map { indexed ->
           indexed.search.byFileName.values
               .map { it to SortKey(normalizeText(it.song.title), normalizeText(it.song.artist)) }
               .sortedWith(compareBy({ it.second.title }, { it.second.artist }, { it.first.song.fileName }))
               .map { it.first }
       }
       .flowOn(Dispatchers.Default)
       .asState(emptyList())
   ```
   Each key is normalized once per song (decorate, sort, undecorate). If plan 18 has already moved `indexedSongs` off the main thread, only the `flowOn` placement needs to match it.
2. **In `SongPicker`, collect `alphabeticalSongs` and build `PickableSong` from each `SearchableSong`:**
   - Take `title`, `artist` and `searchableTags` from it (they are the same `normalizeSearchText` keys).
   - Compute only the lowercase tag set and the language set, which are cheap.
   - Keep today's ordering rule: the setlist's own songs first, in its order (`initialSongFileNames`), then the rest in the order that arrives.
   ```kotlin
   val alphabetical by viewModel.alphabeticalSongs.collectAsStateWithLifecycle()
   val pickableSongs = remember(alphabetical, initialSongFileNames) {
       val byFileName = alphabetical.associateBy { it.song.fileName }
       val initial = initialSongFileNames.toSet()
       (initialSongFileNames.mapNotNull { byFileName[it] } + alphabetical.filterNot { it.song.fileName in initial })
           .map { it.toPickable() }
   }
   ```
   Drop the `songs` dependency of this `remember`. `songs` is still used for `toPickerFilters` and the empty-library text.
3. **In `SetlistPicker`, decorate before sorting**, so each title is normalized once:
   ```kotlin
   .map { it to viewModel.normalize(it.title) }.sortedWith(compareBy({ it.second }, { it.first.fileName })).map { it.first }
   ```
4. Keep what the two pickers show and how they rank matches. The comparison keys are the same functions as today (`normalize` for order, `normalizeSearchText` for search), only computed once.

## Verification
- `./gradlew :presentation:desktopTest`, then `./gradlew :app:desktop:run` and `:app:web:wasmJsBrowserDevelopmentRun` with a large library (duplicate the demo songs into 2,000 files).
- Open "Song assignments" for a setlist. Measure the time from tap to the sheet's first frame with a trace, or log `withFrameNanos` in the sheet: it no longer grows with the library.
- The order is identical to before: the setlist's songs first, the rest alphabetically with accents folded ("Ábel" next to "Abel").
- The search still finds songs by title, artist and tag, with punctuation ignored.
- `presentation/CLAUDE.md`'s description of the song picker ("the rest alphabetically") stays true; no doc change is needed beyond the new state's KDoc.
