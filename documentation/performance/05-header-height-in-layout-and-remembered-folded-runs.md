<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 05 — Read the header height in the layout phase and remember `FoldedRuns`

| | |
|---|---|
| Lane | A |
| Impact | low |
| Confidence | high |
| Platforms | all |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt |
| Depends on / conflicts with | Edits `SongLyrics`'s top half and `SongSectionsLayout`'s parameters, which 04, 08 and 07 also touch. Order: … 04 → **05** → 08 → 07 … |
| Commit message | `Lay out a song page in one pass when it opens.` |

## Problem
1. **Backwards write.** `SongLyrics.kt:221-232` writes the header's height during measurement and reads it back during composition:
   ```kotlin
   var headerHeight by remember { mutableIntStateOf(0) }
   val headerHeightDp = with(density) { headerHeight.toDp() }
   …
   .layout { measurable, constraints ->
       val placeable = measurable.measure(constraints)
       if (placeable.height != headerHeight) headerHeight = placeable.height
   …
   availableHeight = if (availableHeight.isSpecified) (availableHeight - headerHeightDp).coerceAtLeast(0.dp) else availableHeight,
   ```
   The consequences:
   - Every page that opens (the current one and both composed neighbours) is composed twice.
   - The first frame's grid is searched as if there were no header, so on a multi-column window the column count can change on frame 2.
   - The same thing happens whenever the header changes height: a tag added or removed, and every frame of a pinch, because the header's text scales too. That includes one extra recomposition and relayout after the pinch ends.
2. **That second composition cannot skip anything.** `SongLyrics.kt:184-193` builds a new `FoldedRuns(...)` on every composition. It is a plain class holding `Set`s, so strong skipping compares it by identity. The `SongSectionsLayout` content lambda captures it, so every composition of `SongLyrics` re-runs every section's `SongSectionContent`: the runs loop, `group.map { … }` for every tab run (a new `List`, so `SongTabRun` recomposes and rebuilds its `Layout` measure policy and `drawBehind`), `runNameCounts`, and so on. The lines themselves skip.

## Fix
1. Keep `headerHeight` as state, but read it only in the layout phase:
   - Delete `headerHeightDp`.
   - Pass `SongSectionsLayout(availableHeight = availableHeight, headerHeight = { headerHeight }, …)`.
   - In its measure block, compute:
     ```kotlin
     val availableHeightPx = if (availableHeight.isSpecified) (availableHeight.roundToPx() - headerHeight()).coerceAtLeast(0) else 0
     ```
   This keeps the current semantics, including the 0 that makes the search fall back to `gridFor(maxColumnCount)`. The `Column` measures the header before the `LookaheadScope`, so the value is current within the same pass. The read subscribes the layout, so a later change of the header's height still remeasures it, with no recomposition. The divider lambda (`:255`) already reads `headerHeight` in measure, and stays as it is.
2. Remember the fold state:
   ```kotlin
   val latestOnFoldToggled by rememberUpdatedState(onFoldToggled)
   val foldedRuns = if (onFoldToggled == null) null else remember(foldedSections, toggledFolds) {
       FoldedRuns(
           collapsed = foldedSections,
           toggled = toggledFolds,
           onToggled = { key -> toggledFolds += key; latestOnFoldToggled?.invoke(key) },
       )
   }
   ```
   It is rebuilt exactly when what it answers changes, so `SongSectionContent` skips on every other composition.
3. **What must not change:**
   - `availableHeight` keeps meaning "the page without scrolling", with the header subtracted.
   - `maxRowHeight` stays the full `availableHeight`.
   - The fade-in of a section that was just unfolded still only plays for a toggled key.

## Verification
- Run `./gradlew :presentation:desktopTest :app:android:assembleDebug`.
- Layout Inspector: opening a song composes `SongLyrics` once rather than twice, and `SongSectionContent` counts stay at 1 when a tag is added.
- Fold and unfold a chorus on the details screen. It still fades in, and the sections around it still move as they do today.
- Desktop, a song that just fits two columns with a tall header (many tags): the column count on the first frame is the final one (no jump when the page opens in a pager).
