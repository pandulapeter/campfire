<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 09 — Rebuild the transposition labels only when the pager's keys change

| | |
|---|---|
| Lane | A |
| Impact | low |
| Confidence | high |
| Platforms | all |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt, presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/TranspositionLabelsTest.kt |
| Depends on / conflicts with | Touches `SongDetailsScreen` near 02's edits. Land after 02 and 06. Order: … 06 → **09** → 10. |
| Commit message | `Stop measuring every transposition label again whenever the library changes.` |

## Problem
`SongDetailsScreen.kt:205-214`:

```kotlin
val transpositionLabels = remember(songs, isPerformanceModeEnabled, shouldShowChords, chordSpelling) {
    … transpositionLabelsForSongs(songs) { song, transposition -> viewModel.renderKey(song = song, transposition = transposition, spelling = chordSpelling) }
}
val inlineControlsWidth = rememberCompactSteppersWidth(transpositionLabels = transpositionLabels, spacing = INLINE_CONTROL_SPACING)
```

`songs` (`:146-152`) is rebuilt on every emission of `songsByFileName`, which means every library change: a sync run downloading files, a tag written, a rescan on resume. Each time:
- `transpositionLabelsForSongs` renders the key 25 times for every distinct `(key, transpose)` pair, each time through `transposeChordPro` and `convertChordProNotation` on a one-key song.
- It returns a new list, and `rememberCompactSteppersWidth` (`SongDisplayControls.kt:224-231`) then measures every label through a `rememberTextMeasurer()` with the default cache of 8. That is up to a couple of hundred paragraph layouts for a setlist spanning many keys.

This all runs on the main thread, and nothing about the result has changed.

## Fix
1. The labels depend on each song's `key` and `transpose` only (`renderKey`, `CampfireViewModel.kt:1535-1538`, reads nothing else), plus `hasChords` for the filter. Key the computation on those:
   ```kotlin
   val keyedTranspositions = songs.filter { it.hasChords }.map { it.key to it.transpose }.distinct()   // O(n), in composition
   val transpositionLabels = remember(keyedTranspositions, isPerformanceModeEnabled, shouldShowChords, chordSpelling) {
       if (isPerformanceModeEnabled || !shouldShowChords) emptyList()
       else transpositionLabelsForKeys(keyedTranspositions) { key, transpose, transposition ->
           viewModel.renderKey(key = key, transpose = transpose, transposition = transposition, spelling = chordSpelling)
       }
   }
   ```
   `remember` compares keys with `equals`, so a new list with the same pairs reuses the labels. `rememberCompactSteppersWidth` is keyed on `transpositionLabels`, so it keeps its measurement too.
2. `transpositionLabelsForSongs` becomes `transpositionLabelsForKeys(keys: List<Pair<String?, Int>>, renderKey: (String?, Int, Int) -> String?)`, with the same output order and `distinct()`.
3. `CampfireViewModel.renderKey` gets an overload on `(key: String?, transpose: Int, transposition: Int, spelling)`. The `Song` version delegates to it, so the lists screens keep calling it unchanged.
4. **What must not change:** the widest label that is measured, and so where the steppers switch between the bar and the sheet.

## Verification
- Run `./gradlew :presentation:desktopTest`, with `TranspositionLabelsTest` updated to the new signature and one added case: songs sharing `(key, transpose)` give the same labels as one of them.
- Open a setlist on a desktop window wide enough for inline steppers, then add a tag to the song on screen with the header's "Add tag" chip (which rewrites the file and makes the library emit). The steppers do not move, and a profiler shows no `TextMeasurer.measure` burst on that emission.
