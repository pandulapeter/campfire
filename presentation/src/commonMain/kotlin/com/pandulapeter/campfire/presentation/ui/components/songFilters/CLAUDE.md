<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/components/songFilters

The tag and language filters of the songs screen, in a side panel or a sheet.

- `SongFilters` is a side panel or a sheet, and neither when there is nothing to filter by (`hasSongFilters`).
  `ControlsSidePanel`: only the list feeds the bar's nested scroll.
- Filter chips are `CountedFilterChip` on `SelectableChip`, not Material's `FilterChip` (its state layer hugs the
  label); the `indication` sits outside the padding under a `clip`. No chip in the app is a Material `FilterChip`.
- `hasRoomForSidePanel`: the filter panel shows only while two columns still fit beside it, the fast scroller counted;
  otherwise the filters open in a sheet.

### The filter groups (`FilterGroupsLayout.kt`)

- **The fold is decided by room, not a count**: groups report whole and fully collapsed heights as intrinsics, and
  `chipBudgets` shares the height like vessels. Selected chips are always visible; "Show all" opens the rest. Below the
  minimum the content scrolls.
- In a sheet, use the sheet's tallest height (`BottomSheetContentScope.uncoveredTopInset`): Material's offset-dependent
  top padding would otherwise make the layout oscillate every frame.
- The "any / every" choice (only with two selected) and "Reset all filters" are `FilterSlot.TRANSIENT`, out of the
  sharing, so selecting a chip never hides chips under the finger. The clear action sits in `FilterSectionTitle`, built
  to keep the plain title's height, so the sheet never resizes on a selection.
- Room is shared in the lookahead pass only; the approach pass animates bounds. Moving chips use `animateBounds` in one
  `LookaheadScope`.
- The language group appears only with more than one language (no-language songs counting as one).
- **Each group has its own search field** (`FilterSearchField`), always shown and never taking the caret by itself;
  selected chips stay in sight whatever the query (`chipsMatching`).
