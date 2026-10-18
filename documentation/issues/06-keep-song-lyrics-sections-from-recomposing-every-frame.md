<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Keep the song's sections from being composed again on every frame of a collapsing bar or a resize

**Challenged:** amended — named what the extracted composable must take to skip under strong skipping (the
`LookaheadScope` as its receiver, every remembered holder by identity) and that its state reads move with it; step 4's
provider must read a snapshot `State` (a lambda capturing the live `Dp` is a new lambda every frame), and `viewport`
must go the same way, or `SongLyrics` never skips.

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all (a phone on its side above all)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SongLyrics.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SongSectionsLayout.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsPage.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/CLAUDE.md`

## Problem

`SongLyrics.kt`'s trailing `content` lambda of `SongSectionsLayout` holds the loop over every unit
(`units.itemRanges.forEachIndexed { … SongSectionItem(...) }`), `SectionCards(...)` and `repeat(...) { HorizontalDivider() }`.
It captures values, so it is a new lambda on every composition and its body runs again whenever `SongLyrics` recomposes,
which happens on every frame of:

- the app bar collapsing during a scroll in a short window (`rowViewportHeight` is the live height from
  `SongDetailsPage`'s `BoxWithConstraints`);
- the metronome panel opening or closing;
- the navigation rail animating (`extraWidth`, read in the lambda: `val isHeldStill = … || extraWidth > 0.dp || …` and
  `animatesControls = sectionMotion == SectionMotion.SPRING && extraWidth <= 0.dp`).

Every line is a unit of its own, so an 80-line song is ~80 `SongSectionItem` call sites comparing ~21 parameters each (three
`TextStyle`s built anew each composition, so their `equals` is deep), ~80 new modifier chains and ~80 dividers per frame,
per composed pager page.

## Fix

1. Compute `val isResizing = extraWidth > 0.dp` above the layout and pass the boolean.
2. Move the loop, the cards and the dividers into a private `@Composable fun LookaheadScope.SongSectionChildren(...)`
   (Unit-returning, so restartable and skippable); the `content` lambda only calls it. The `LookaheadScope` receiver is
   what `movedBy` needs for `animateBounds` and is a remembered instance, so it compares equal by identity. Every other
   parameter must settle across the frames named above, and under strong skipping an unstable one is compared by
   identity, so pass the remembered objects themselves: `sections`, `units`, `unitKeys`, `cardKeys`, `animations`,
   `sectionGlides`, `fitDeferral`, `tuningChipVisibility`, `chordLayouts`, `textMeasurements`, `foldedRuns`,
   `onChordFoldToggled`, plus `sectionMotion`, `isResizing`, `hasChordFoldBeenToggled`, `fontScale`, `model.notation`,
   `isSongInfoShown`, `songInfoEditing`, `shownPlayingControls`, `playingTools`, `chordDiagrams` (all remembered by
   `SongPages` / `SongDetailsScreen` / `SongPreview`, so they keep their identity while only the viewport moves) and
   `defaultLabels` (a data class of strings). Never a lambda or object built in `SongLyrics`' body. The reads
   `movedBy` does (`fitDeferral.isPending`, `SectionAnimation.isTooTallToAnimate`, `SectionGlides.isGliding`) are
   snapshot state and now invalidate `SongSectionChildren`'s scope instead of the lambda's, which is what they did
   before. The children keep their emission order (units, then cards, then dividers), which `SongSectionsLayout`'s
   measure slices by position.
3. Remember the text styles keyed on what they are built from (font scale, `LocalTextStyle.current`, typography), so
   the comparisons `SongSectionChildren` and the `remember`s below it make short-circuit on identity (`TextStyle.equals`
   checks `===` first).
4. Optionally (crosses into `screens/songDetails`): hand `rowViewportHeight` to `SongSectionsLayout` as a provider read in
   its measure block. The provider must read a snapshot `State` — e.g. `val liveViewportHeight by
   rememberUpdatedState(viewportHeight)` in `SongDetailsPage` and `{ liveViewportHeight }` — since a lambda capturing
   the `Dp` itself is memoized on that value and is a new lambda every frame, so nothing would skip. `onRowsPlaced`
   captures `viewport` (also new every frame of the height), so read it through `rememberUpdatedState` the same way.
   Only then are all of `SongLyrics`' parameters equal across a bar collapse and it skips; the measure still reruns
   through the state read.

## Tests

The layout logic is unchanged, so the `songView` tests and the golden renders (`./gradlew :tools:screenshots:test`) must pass
unchanged.
