<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Recompose only the moved rows while a setlist row is dragged

**Kind:** performance  ·  **Severity:** low–medium  ·  **Platforms:** all (worst on wasm and cheap Android phones)
**Challenged:** amended — the row's order provider must be built inside the item's composable content (a lambda built in the `LazyGridScope` builder is new on every move and keeps every row from skipping); the builder keeps reading the drawn order; `hapticFeedback` read through updated state too; noted that Reorderable 3.1.0 already wraps `onMove` in `rememberUpdatedState`.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistReorderState.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistSongRow.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistList.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/CLAUDE.md` only if a
sentence there becomes false; a UI test in `tools/screenshots` only if the existing reorder UI test can be extended
cheaply (see Tests)

## Problem

Reviewed at `6c3043996`. In reorder mode each accepted move recomposes the whole `SetlistList` and every visible
row:

1. `rememberSetlistReorder` runs in `SetlistList`'s composition and reads the dragged order as an effect key:

   ```kotlin
   LaunchedEffect(setlistsWithSongs, draggedSetlist.value) {
       draggedSetlist.value?.let { dragged ->
           if (dragged.isSettledBy(setlistsWithSongs.firstOrNull { it.setlist.fileName == dragged.setlistFileName })) draggedSetlist.value = null
       }
   }
   ```

   so `draggedSetlist.value = it` in the move callback invalidates `SetlistList`.
2. That recomposition builds a new `SetlistReorder` (not remembered, no `equals`) with new `onDragStarted` /
   `onDragStopped` lambdas. It is a parameter of every `SetlistSongRow` and is captured by the grid's content lambda, so
   the item provider is rebuilt and no visible row can skip.
3. Rows read `isRearranging = reorder.draggedSetlist != null` (the whole dragged order, for a yes/no that only flips at
   the start and end of a drag) and take `songFileNames: List<String>`, a new list on every move, captured by
   `onMoveUp` / `onMoveDown`.

On a 360×640 dp phone about 8–10 rows are visible; each crossing recomposes all of them
(`SetlistRowContent` → `SongListItem`: title, labels, key, star, menu, drag handle) in the frame the library starts its
displacement animation, roughly 5–15 ms on a low-end device: a likely dropped frame per row crossed in a fast drag.

The Reorderable library's `draggableHandle` is keyed only on its state, so changing these lambdas cannot cancel a drag;
this is cost, not correctness.

## Fix

- `rememberSetlistReorder`: `remember` the `SetlistReorder` once (keyed on `reorderableState`), with what its
  callbacks use read through `rememberUpdatedState` (`onReorder`, `setlistsWithSongs`, and `hapticFeedback`, which
  `onDragStarted` / `onDragStopped` call), so the same instance survives every move.
- Replace the effect with `LaunchedEffect(Unit) { snapshotFlow { draggedSetlist.value to latestSetlists.value }.collect { … } }`
  (or keyed on `setlistsWithSongs` with `snapshotFlow { draggedSetlist.value }` inside), so the composition no longer
  reads `draggedSetlist.value`. Keep the settle rule exactly as it is (identity, `isSettledBy`).
- Expose `isRearranging` from `SetlistReorder` as a `derivedStateOf { draggedSetlistState.value != null }` and read
  that in the row instead of `reorder.draggedSetlist != null`.
- Hand rows the setlist's drawn order as a provider `() -> List<String>` read inside `onMoveUp` / `onMoveDown`, not as
  a list parameter. Update the `@param songFileNames` KDoc. **The provider must keep its identity across moves**, or
  it is the new parameter that stops every row from skipping: build it inside the `itemsIndexed` content lambda (a
  composable body, where strong skipping memoizes it on its captures), capturing only what a move does not change
  (`rowsCache`, `setlistWithSongs`, the remembered `reorder`), e.g.
  `{ rowsCache.rowsFor(setlistWithSongs, reorder.draggedSetlist).songFileNames }`; not in the `LazyGridScope` builder
  (not composable, so a lambda there is a new instance each time the builder runs, which is every move).
- The `LazyGridScope` builder keeps reading `reorder.draggedSetlist` through `rowsCache.rowsFor` as today: that read is
  what rebuilds the item provider in the frame of the move, which the reorderable state waits for. Only composition
  reads go.
- The move callback of `rememberReorderableLazyGridState` must still see the latest `setlistsWithSongs`. Reorderable
  3.1.0 already keeps `onMove` through its own `rememberUpdatedState`, and `SetlistList` still recomposes when
  `setlistsWithSongs` changes, so the lambda as written stays correct; reading the list through the updated state is
  harmless and keeps it correct if that ever stops being true.

After this a move rebuilds the item provider, runs each visible item's content lambda (a few calls), and recomposes
only the rows whose `row` / `rowIndex` / `isLastRow` changed, not the screen. Check every other `SetlistSongRow`
parameter still compares equal across a move (they are captured from `SetlistList`'s last composition, which a move no
longer causes).

Behaviour must not change: haptics on lift, pass, and drop; one write per drag; the drawn order kept until the library
agrees; other setlists' rows disabled while a drag is in one (`draggingSetlistFileName`); Move up / down as one write.

## Tests

The decisions (`afterMove`, `isSettledBy`, `movedOnePlace`) are already pure and tested and do not change. Recomposition
counts are not asserted (implementation detail). No UI test drives a drag today (`tools/screenshots` has none), and
building one for a refactor that keeps every decision pure and tested is not worth it: the existing unit tests, the
`:tools:screenshots:test` suite, and a compile of every platform verify it.
