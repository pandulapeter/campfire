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

- `SongFilters` is a sheet on every screen size (a side panel would push the fast scroller off the window's edge), not
  offered when there is nothing to filter by. The bar action appears without animation while the library arrives, so
  `hasSongFilters` is read straight from `screenData`, landing before the songs say they are read.
- **No chip in the app is a Material `FilterChip`** (its state layer hugs the label): `SelectableChip`, its
  `indication` outside the padding under a `clip`.
- **The fold is decided by room, not a count**: groups report whole and collapsed heights as intrinsics, and
  `chipBudgets` shares the height like vessels. Selected chips are always visible; below the minimum the content
  scrolls. Room is shared in the lookahead pass only; moving chips use `animateBounds` in one `LookaheadScope`.
- In a sheet, the height offered is the sheet's tallest at every offset (`animateSheetContentHeight`), so the sharing
  never follows a drag.
- Nothing under the finger moves on a selection: the "any / every" choice (only with two selected) is out of the
  sharing (`FilterSlot.TRANSIENT`); a group's clear action keeps the plain title's height; the header's Reset is always
  passed and fades in place.
- The language group appears only with more than one language (no-language songs counting as one).
- **Each group has its own search field**, always shown, never taking the caret by itself; selected chips stay in
  sight whatever the query.
