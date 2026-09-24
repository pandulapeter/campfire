<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Index song sections without one label object per list item

Priority: medium for large libraries and search result changes. Affects the Songs screen on every platform.

## Evidence

`SongsScreen.kt` (around lines 302-307) expands every song group into a `sectionLabels` list with one element per lazy-grid item, solely to answer the fast scroller's `labelForItem(index)` call (around line 475). This work and allocation happen again whenever search/filter/sort changes the groups. The outgoing sticky-header overlay also allocates a `mapNotNull` list while looking up one pushed header (around line 453).

## Implementation plan

1. Replace the expanded `sectionLabels` list with an immutable section index made of cumulative grid-item end positions and one label per group. Count a sticky header as one item, exactly as the current code does. Do not include a placeholder in the index: it should still return `null` for the placeholder-only screen.
2. Implement `labelForItem(index)` by binary-searching the cumulative ends and returning that group's label. Groups with no header (ranked search results) must return `null` for every song. Preserve the current zero-based lazy-grid indices.
3. Store a header-by-key map alongside the section index and use it for the pushed-header overlay instead of `songGroups.mapNotNull(...).firstOrNull(...)`. Rebuild both structures only when `songGroups` changes.
4. Add pure tests comparing the new lookup with the old expanded-list result for empty groups, one group, many artist groups, a headerless search group, and indices at both sides of every boundary. If the helper lives in `:presentation`, configure `commonTest` with `kotlin("test")`.

## Done when

- Index storage scales with the number of groups, not the number of songs.
- Fast-scroller bubbles and pushed headers show the same labels at section boundaries and during a search.
- Changing a query no longer allocates a list element for every result solely for the scroller.
