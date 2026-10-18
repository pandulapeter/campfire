<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Keep the song's sections from being composed again on every frame of a collapsing bar or a resize

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
2. Move the loop, the cards and the dividers into a private skippable `@Composable` (`SongSectionChildren(...)`) taking only
   values that settle; the `content` lambda only calls it.
3. Remember the three text styles keyed on what they are built from (font scale, `LocalTextStyle.current`, typography).
4. Optionally (crosses into `screens/songDetails`): hand `rowViewportHeight` to `SongSectionsLayout` as a provider read in its
   measure block, and keep `onRowsPlaced` stable with `rememberUpdatedState` in the caller.

## Tests

The layout logic is unchanged, so the `songView` tests and the golden renders (`./gradlew :tools:screenshots:test`) must pass
unchanged.
