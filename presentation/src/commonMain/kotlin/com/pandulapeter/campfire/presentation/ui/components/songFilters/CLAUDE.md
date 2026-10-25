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

The tag and language filters of the songs screen, in a bottom sheet.

- `SongFilters` is a sheet on every screen size (no side panel, which would push the fast scroller off the window's
  edge), not offered when there is nothing to filter by (`hasSongFilters`). The bar action appears without animation
  while the library arrives, so `hasSongFilters` is read straight from `screenData`, landing before the songs say they
  are read.
- Filter chips are `CountedFilterChip` on `SelectableChip`, not Material's `FilterChip` (its state layer hugs the
  label); the `indication` sits outside the padding under a `clip`. No chip in the app is a Material `FilterChip`.

### The filter groups (`FilterGroupsLayout.kt`)

- **The fold is decided by room, not a count**: groups report whole and fully collapsed heights as intrinsics, and
  `chipBudgets` shares the height like vessels. Selected chips are always visible; "Show all" opens the rest. Below the
  minimum the content scrolls.
- In a sheet, use the sheet's tallest height (`BottomSheetContentScope.uncoveredTopInset`): Material's offset-dependent
  top padding would otherwise make the layout oscillate every frame.
- The "any / every" choice (only with two selected) is `FilterSlot.TRANSIENT`, out of the sharing, so selecting a
  chip never hides chips under the finger. A group's clear action sits in `FilterSectionTitle`, built to keep the plain
  title's height, so the sheet never resizes on a selection. Reset (`ResetFiltersAction`), emptying every group, is the
  sheet's header action, always passed and faded in and out inside, so its arrival moves nothing either.
- Room is shared in the lookahead pass only; the approach pass animates bounds. Moving chips use `animateBounds` in one
  `LookaheadScope`.
- The language group appears only with more than one language (no-language songs counting as one).
- **Each group has its own search field** (`FilterSearchField`), always shown and never taking the caret by itself;
  selected chips stay in sight whatever the query (`chipsMatching`).
