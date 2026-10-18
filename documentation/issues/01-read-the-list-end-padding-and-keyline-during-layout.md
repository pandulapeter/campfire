<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Read the fast scroller column's width and the selection padding during layout, not composition

**Challenged:** amended — the fix now also covers the in-composition reads the first version missed (the fast scroller's own `padding`, the setlists' `doneButtonRoom`, `headerEndPadding` into both pushed headers, `SectionHeader`'s own read of the local); LazyGrid re-measure and `PaddingValues` equality checked and spelled out.

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/list/FastScrollerColumn.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongList.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistList.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/songCard/CenteredSongCardContent.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/list/SectionHeader.kt`, the file declaring
`LocalListItemTrailingKeylineAdjustment` / `listItemTrailingKeylineAdjustment` (grep), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/KeyboardAwarePadding.kt`
(as the model), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/list/CLAUDE.md`, tests in `presentation/src/commonTest/.../components/list/`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/PaddingValuesSides.kt`, `.../ui/screens/songs/PushedSongSectionHeader.kt`, `.../ui/screens/setlists/PushedSetlistHeader.kt`

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

1. Expose the column width as a provider (`() -> Dp` or a `State<Dp>`), never as a value read in composition. Keep the
   animated bottom paddings as `State<Dp>` (`animateDpAsState(...)` without `by`), read only through providers.
2. Give the grid a `PaddingValues` whose end and bottom read the providers when the grid measures (the way
   `KeyboardAwarePadding` reads the IME). Equality: compare the providers by identity (as `SectionStartRoomPadding` does
   with `appBarOverlap`) and remember them (`remember(scrollerColumn) { { scrollerColumn.width } }`), so two compositions
   hand the grid equal paddings and `rememberLazyGridMeasurePolicy` (keyed on `contentPadding`) is not rebuilt. Checked
   against Compose 1.12.1: the grid reads every side inside its measure policy and resolves its column widths from the
   constraints already offset by the padding (`slots.invoke(density, contentConstraints)`, `GridSlotCache` keyed on those
   constraints), so a deferred end padding does re-measure and re-size the cards; nothing recomposes. Extend
   `PaddingValues.only(…)` with deferred extras (or add a sibling) so a deferred side survives narrowing and `withSectionStartRoom`.
3. Every other composition read of the same values must go too, or the list body still recomposes each frame:
   - `FastScroller`'s `.padding(contentPadding.only(top, end, bottom, extraBottom = selectionBottomPadding))` in `SongList`
     and `extraBottom = doneButtonRoom` in `SetlistList` (the setlists' reorder Done room is the same pattern and was not
     named): use the deferred `only(…)` there as well (`Modifier.padding(PaddingValues)` reads it in measure).
   - `headerEndPadding = scrollerColumnWidth + contentPadding.calculateEndPadding(…)` goes to `SectionHeader` *and* to
     `PushedSongSectionHeader` / `PushedSetlistHeader`: make it a `() -> Dp` through all three (`SectionHeader` uses it only in
     `extendIntoEndPadding` and the pill's `layout {}`).
4. Make `LocalListItemTrailingKeylineAdjustment` a `() -> Dp` (provided as a remembered lambda, so its value never changes
   and no consumer recomposes; the default stays `{ listItemTrailingKeylineAdjustment(FAST_SCROLLER_WIDTH) }`). Read it in
   `CenteredSongCardContent`'s measure block and in an `offset { }` of `ListItemActions` (RTL-aware like `offset(x =)`).
   `SectionHeader` also reads the local in composition (`sectionHeaderPillActionEndPadding(...)`) for the Row's
   `.padding(end = actionEndPadding)` as well as for its layouts: move that end padding into a `layout {}` too.

Keep `listItemTrailingKeylineAdjustment`'s clamping as it is (pure).

## Tests

`listItemTrailingKeylineAdjustment` has no test: 0dp → 16dp, 24dp → 12dp, 12dp → 14dp, 30dp (spring overshoot) → 12dp. Add a
test of the new deferred `PaddingValues`: two instances built with the same provider are equal whatever it returns, and
`calculateEndPadding` / `calculateBottomPadding` return the provider's current value in both layout directions. The golden renders (`./gradlew :tools:screenshots:test`) must not
change.
