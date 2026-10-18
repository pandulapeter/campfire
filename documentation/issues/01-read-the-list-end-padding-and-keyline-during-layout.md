<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Read the fast scroller column's width and the selection padding during layout, not composition

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/list/FastScrollerColumn.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongList.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistList.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/songCard/CenteredSongCardContent.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/list/SectionHeader.kt`, the file declaring
`LocalListItemTrailingKeylineAdjustment` / `listItemTrailingKeylineAdjustment` (grep), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/KeyboardAwarePadding.kt`
(as the model), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/list/CLAUDE.md`, tests in `presentation/src/commonTest/.../components/list/`

## Problem

Two animated values are read while composing the list screens, so each recomposes the whole list body (and, through
lambdas that capture them, every visible card and header) on every frame of the animation:

- `FastScrollerColumn.width` (`val width: Dp get() = widthAnimatable.value…`) is read in `SongList.kt`
  (`val scrollerColumnWidth = scrollerColumn.width` … `CompositionLocalProvider(LocalListItemTrailingKeylineAdjustment provides
  listItemTrailingKeylineAdjustment(scrollerColumnWidth))`) and the same way in `SetlistList.kt`; the local is read in composition
  by `CenteredSongCardContent` and `SectionHeader`. The ~400 ms spring runs whenever the list changes between fitting and
  scrolling: typing a search, a filter chip, the keyboard resizing the viewport — while the user types.
- `SongList.kt`: `val selectionBottomPadding by animateDpAsState(if (isSelecting) DONE_BUTTON_ROOM else 0.dp)` then
  `extraBottom = 8.dp + selectionBottomPadding`: ~20 recompositions of the 400-line `SongList` body when selection starts or
  ends, each handing `LazyVerticalGrid` a new `PaddingValues`.

## Fix

1. Expose the column width as a provider (`() -> Dp` or a `State<Dp>`), never as a value read in composition.
2. Give the grid a `PaddingValues` implementation whose `calculateEndPadding` / `calculateBottomPadding` read the animated
   values when the grid measures (the way `KeyboardAwarePadding` reads the IME), with an `equals` that ignores the animated
   part, so the grid re-measures but nothing recomposes. Extend `PaddingValues.only(…)` (or add a sibling) so a deferred
   side survives narrowing.
3. Make `LocalListItemTrailingKeylineAdjustment` a `() -> Dp`, read inside `CenteredSongCardContent`'s measure block and in an
   `offset { }` of its actions; pass `SectionHeader` an `endPadding: () -> Dp` (it already uses it only in `layout {}`).

Keep `listItemTrailingKeylineAdjustment`'s clamping as it is (pure).

## Tests

`listItemTrailingKeylineAdjustment` has no test: 0dp → 16dp, 24dp → 12dp, 12dp → 14dp, 30dp (spring overshoot) → 12dp. Add a
test of the new deferred `PaddingValues`: two instances with different animated values are equal, and
`calculateEndPadding` returns the provider's current value. The golden renders (`./gradlew :tools:screenshots:test`) must not
change.
