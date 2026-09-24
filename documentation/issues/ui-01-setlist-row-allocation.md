<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Avoid rebuilding setlist orders for every visible row

Priority: high. Affects all platforms; a wide web window can compose many rows at once.

## Evidence

`SetlistList` calls `setlistWithSongs.rows(draggedSetlist)` for every setlist when it builds the lazy grid. The ordinary path maps every entry to a new `SetlistRow` (`SetlistsScreen.kt`, `rows`, around line 518). Inside **each visible row**, it maps all rows back to file names, then calls `movedOnePlace` twice (around lines 355-360). Each call searches the list and copies it before the user has requested a move. This also happens in performance mode, because `takeIf { isReorderable }` is applied after the copies are made. A drag updates `draggedSetlist` during pointer movement and repeats the work.

## Implementation plan

1. In `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt`, keep the drawn `rows` in one place per setlist. Derive its file-name order **once per setlist**, outside `itemsIndexed`. Preserve the existing drawn order and original slot numbers while a drag is active.
2. Before constructing move actions, check `isReorderable`. For each row, determine only whether `rowIndex` has a neighbor above or below. Put `movedOnePlace(...)` inside the action lambda so the list copy occurs only when the accessibility action is invoked. Use the drawn `rowIndex`, not `indexOf(songFileName)`, since the row index already names the current drag order.
3. Avoid rebuilding `SetlistRow` objects for unchanged setlists on every drag move. The lazy-grid item builder is not composable, so do not call `remember` inside its `forEach`. Instead, remember a small `SetlistRowsCache` before `LazyVerticalGrid`; have its `rowsFor(setlistWithSongs, draggedSetlist)` reuse rows when that setlist instance and its relevant drag order are unchanged. Drop cached entries for removed setlists. Keep the lazy item keys exactly as they are.
4. Add a focused test for pure order helpers if they are extracted. Cover first and last rows, a middle row, a missing song entry, and a drag order. Do not add a test that merely mirrors the `itemsIndexed` implementation.

## Done when

- Composing a row performs no full-setlist copy for either move action; one copy is made when a move is invoked.
- Dragging and keyboard/screen-reader moves still renumber rows immediately and persist the same order after the drag ends.
- A setlist with a missing song and performance mode keep their current behavior.
- Verify manually in a long setlist on web, including a drag near the bottom and move-up/move-down accessibility actions.
