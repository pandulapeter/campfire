# Keep the setlist rows' drag handle animated in and out across the reorder mode switch by hoisting its visibility state above the ReorderableItem branch

**Challenged:** amended — the mechanism now accounts for `Transition.onDisposed` writing the disposed transition's own target into the shared `MutableTransitionState` (harmless when the switch finds the grip at rest, which is the case the plan fixes, verified against animation-core 1.12.1; a switch mid-animation still jumps and is stated as accepted), plus a manual step for that rapid toggle. Structure (remember above the branch, unconditional, fresh on item reuse), first-frame-right for rows scrolled in during the mode, the drag disposal path and the `reorderableKeys` claim (Reorderable 3.1.0) all checked and unchanged.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt`, `presentation/CLAUDE.md` (the paragraph "A setlist row only goes through `ReorderableItem` …", ~567-572)

## Problem

Commit e7245858d took a setlist row out of `ReorderableItem` outside reorder mode, so that rows nobody can drag stop
paying for the library's per-row tracking and for the `animateDpAsState` / `draggedListItemContainerColor` coroutines.
It did so by calling one `rowContent` lambda from two call sites,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt:678-690`
at 1b26dfb94:

```kotlin
if (isReordering) {
    ReorderableItem(
        state = reorderableState,
        key = key.string.orEmpty(),
        enabled = draggingSetlistFileName.let { it == null || it == setlistWithSongs.setlist.fileName },
        animateItemModifier = listItemAnimation(
            listState = listState,
            isEnabled = hasLoadedLibrary,
            isRearranging = draggedSetlist != null,
        ),
    ) { isBeingDragged -> rowContent(this, isBeingDragged, Modifier) }
} else {
    rowContent(null, false, listItemAnimation(listState = listState, isEnabled = hasLoadedLibrary))
}
```

The two branches are different groups of the composition (and the first one composes the row inside
`ReorderableCollectionItem`'s own `Box`), so `isReordering` flipping disposes every visible row's subtree and composes
a new one: every `remember` and every transition inside `rowContent` starts over. The one that shows is the grip, at
`SetlistsScreen.kt:605-615`:

```kotlin
AnimatedVisibility(
    visible = isReorderable,
    enter = fadeIn() + expandHorizontally(),
    exit = fadeOut() + shrinkHorizontally(),
) {
    DragHandle(…)
}
```

- **Entering the mode** composes it fresh with `visible = true`; an `AnimatedVisibility` composed visible starts
  visible, so the grip is there in the first frame instead of fading and expanding in.
- **Leaving it** composes it fresh with `visible = false`, so the grip is gone in one frame instead of shrinking out.

Either way the grip's width (and with it the room the title and the subtitle have, which can change how many lines the
card takes) changes in one frame, so the text and every row under a re-wrapped one jump. That is a change the user
caused (the header's Reorder / Done, Back or Escape), which `CLAUDE.md` says is animated, and which was animated
before e7245858d. The rebuild also repeats the row's remembered key (`SetlistsScreen.kt:639-641`, "a whole
transposition to work out again") for every visible row, and recreates `CoverArtImage`'s `AsyncImage`, which is drawn
straight from Coil's memory cache when the cover is there (no placeholder flash was observed in reading; unverified on a
device).

The comment that introduces the branch, `SetlistsScreen.kt:564-567`, and `presentation/CLAUDE.md` (~567-568) say

```kotlin
// ReorderableItem's drag tracking, and the elevation and color it animates while a row is
// lifted, are only worth paying for while this setlist is actually being reordered - which,
// since reorder mode narrows the grid to that one setlist, is exactly when isReordering is
// true here.
```

```
reorder mode narrows the grid to the one setlist being dragged, so `isReordering` already says whether this row's
`ReorderableItem` wrapper, …
```

which is not what happens: `isReordering` turns true at once, while the grid is only narrowed by the
`LaunchedEffect(isReordering, reorderingSetlistFileName)` at `SetlistsScreen.kt:283-317` once the setlist has been
scrolled to the top (`narrowedSetlistFileName = reorderingSetlistFileName` after `animateScrollToItem`). During that
scroll every visible row of every setlist is a `ReorderableItem`. That is fine and should stay so — see Fix — but
the comment and the documentation must say it.

## Fix

**Recommended: hoist the grip's visibility into a `MutableTransitionState` remembered in the item's own scope, above
the `if (isReordering)` branch**, so that whichever subtree is composed picks up the transition where it stands:

```kotlin
) { rowIndex, row ->
    …
    val isReorderable = canMove && isReordering && reorderingSetlistFileName == setlistWithSongs.setlist.fileName
    // Above the branch below, which composes the row anew whenever reorder mode starts or ends: a visibility composed
    // anew starts where its target is, so the grip would appear and vanish in one frame. A row that scrolls in while
    // the mode is on starts with its grip already there, which is the list being shown rather than changed.
    val handleVisibility = remember { MutableTransitionState(isReorderable) }
    handleVisibility.targetState = isReorderable
    …
```

and in `rowContent`:

```kotlin
AnimatedVisibility(
    visibleState = handleVisibility,
    enter = fadeIn() + expandHorizontally(),
    exit = fadeOut() + shrinkHorizontally(),
) { DragHandle(…) }
```

`AnimatedVisibility(visibleState = …)` builds its transition from the state's `currentState`, which only reaches the
target once an animation has finished: entering, the new subtree inside `ReorderableItem` finds `current = false,
target = true` and expands the grip in; leaving, the new plain subtree finds `current = true, target = false` and
shrinks it out (its `DragHandle` already gets the plain `Modifier`, since `scope` is null there). The write to
`targetState` is made in composition before anything reads it, so it is not a backwards write.

One more thing the old subtree does on its way out, checked in animation-core 1.12.1's `Transition.onDisposed`: the
disposed `AnimatedVisibility`'s transition ends itself, writing *its own* last target into the shared state's
`currentState` and clearing `isRunning`. Forgotten observers are dispatched before the new subtree's effects, and when
the switch finds the grip at rest that write is the value `currentState` already holds, so the ordinary enter and
exit above are unaffected (`isRunning` is set again by the new transition's first frame). A switch made while the grip
is still moving — Done or Back within the ~300 ms of the grip sliding in, or Reorder again while it slides out — is
not continuous: the new subtree starts from the state's `currentState` (not the half-way width), and the old
transition's write then flips `currentState` to the stale target, so the grip jumps and runs its animation again
from the far end. It never sticks (the new transition's frame loop is gated on `currentState != targetState` and
settles it), and a mid-flight reversal of a remounted subtree cannot be continuous without `movableContentOf`
(rejected below), so this is accepted rather than worked around. Likewise a grip whose `AnimatedVisibility` is not
composed while the target changes (performance mode drops `actions`) keeps a stale `currentState`; it cannot happen
from this screen, since performance mode is switched in Settings, where reorder mode has already ended with the
screen leaving the top. The `DragHandle`
modifier and everything else in `rowContent` stay as they are.

In the same change, move the remembered key out of `rowContent` into the item scope (computed only for an
`Entry.Present`, passed into or captured by `rowContent`), so the flip no longer recomputes a transposition per visible
row:

```kotlin
val renderedKey = (entry as? CampfireViewModel.SetlistWithSongs.Entry.Present)?.let { present ->
    val transposition = transpositions[present.song.fileName, setlistWithSongs.setlist.fileName]
    val capo = effectiveCapo(song = present.song, setlistFileName = setlistWithSongs.setlist.fileName, capos = capos).fret
    remember(present.song.key, present.song.transpose, transposition, capo, chordSpelling) {
        viewModel.renderKey(song = present.song, transposition = transposition, capo = capo, spelling = chordSpelling)
    }
}
```

(a `remember` inside `?.let` is a conditional group of its own, which is fine since a row's entry kind is fixed by its
key; alternatively keep the `when` and compute it in a small `@Composable` helper). The per-row cost outside the mode
is unchanged: no `ReorderableItem`, no `draggableHandle` / `longPressDraggableHandle`, no `animateDpAsState`, no
`draggedListItemContainerColor` — a `MutableTransitionState` is a plain state holder, and the `AnimatedVisibility`
was already composed on every row.

Keep `isReordering` (not `isReordering && reorderingSetlistFileName == setlistWithSongs.setlist.fileName`) as the
condition for the wrapper. During the bring-to-top scroll the user can already drag the reordered setlist's rows over
the other setlists' rows, and those rows being `ReorderableItem`s is what keeps them out of the drop targets:
`ReorderableCollectionItem` adds a key to the state's `reorderableKeys` in a `LaunchedEffect(state.reorderableKeys,
enabled)` and removes it only when `enabled` turns false — never on dispose — so the rows of a setlist reordered
earlier would still be in the set from that session, and a move onto one is refused only after the reorderable state
has locked itself for up to a second (the freeze the `draggingSetlistFileName` comment describes). The cost is a few
rows for the length of one scroll. Reword instead:

- the comment at `SetlistsScreen.kt:564-570`: the wrapper and what it animates are only paid for while the mode is on;
  the other setlists' rows are wrapped too until the grid has been narrowed, which is what keeps them out of the
  drag's drop targets for the scroll that brings the setlist to the top, and add one sentence on why the grip's
  visibility lives above the branch (or keep that sentence beside the `remember` only — one place, not both);
- `presentation/CLAUDE.md`'s paragraph the same way: "A setlist row only goes through `ReorderableItem` while reorder
  mode is on (`SetlistsScreen.kt`) — every row of every setlist while the setlist is being brought to the top, then
  only the narrowed setlist's —", plus a clause that the grip's `MutableTransitionState` is remembered above that
  switch so the grip still slides in and out as the row is composed anew.

**Rejected options.**

- `movableContentOf` per row, so the subtree moves between the two parents instead of being rebuilt: its lambda is
  remembered, so everything `rowContent` captures (the entry, `rowIndex`, the move callbacks, `topFade`, the
  preferences, the scope) would have to become parameters or `rememberUpdatedState`s — a large rewrite of the row for
  the one transition that shows.
- Always going through `ReorderableItem` and only gating the animations: that is what e7245858d removed, and the
  wrapper's `onGloballyPositioned` (`positionInRoot` for every row on every scrolled frame), `derivedStateOf` and
  `LaunchedEffect` are the per-row overhead it was after.
- Keeping rows wrapped until a drag has settled (`isReordering || draggedSetlist != null ||
  reorderableState.isAnyItemDragging`), so that a drop's settle or a drag cut short by the mode ending keeps its
  animation: with the mode off `isReorderable` is false, so the still-composed handle's `pointerInput` restarts
  disabled, and the grip's `DragGestureDetector.Press` (`detectDragGestures`) does not call `onDragCancel` when its
  coroutine is cancelled — the drag would never stop, `isAnyItemDragging` would stay true and the rows wrapped for
  good. At 1b26dfb94 the whole wrapper is disposed instead, whose `DisposableEffect` in the library's `draggable`
  calls both `onDragStop` and our `onDragStopped`, so the drag is still written. Keeping the handle enabled past the
  mode would let a drag outlive performance mode or an archive that ended it. The visible cost of not doing this is a
  dropped row finishing its last ~300 ms of settle in one frame when Done is pressed right after the drop, which is
  accepted. Ordinary placement animations are not cut by the switch: `LazyLayoutItemAnimator` keeps them per item key
  and only drops one when the root's `animateItem` specs go away, and both branches put them on the root.

## Tests

None. The change is inside a Composable's composition structure (where state is remembered relative to a branch),
which is UI and untested by code per `CLAUDE.md`; there is no pure logic to pull out.

## Manual check

On Android (touch, long-press drag and grip) and on the desktop (mouse, grip, Escape):

1. Setlists screen with a setlist of five or more songs, some with covers, the setlist not at the top of the list.
   Start Reorder from its header: while the list scrolls the setlist to the top, every row's grip fades and expands
   in (no grip on the other setlists' rows); no title re-wraps in a single frame; covers do not flash their
   placeholder.
2. Press Done, then enter again and leave with Back (Android) / Escape (desktop): the grips shrink and fade out each
   time, the rows' text slides back rather than jumping.
3. During the bring-to-top scroll of step 1, drag a row of the reordered setlist onto a row of another setlist that
   was reordered in an earlier session: the drag is not frozen, the row is not dropped there.
4. Press Reorder and at once Done (and Done then at once Reorder): the grip may jump, but always ends shown in the
   mode and gone outside it, never left half-way or shown on a row outside the mode.
5. Drag a row to a new place and release; then drag again and, mid-drag, press Escape (desktop): the mode ends, the
   drag ends with it and the order written is the one shown; entering the mode again, dragging works.
6. With the mode off, fling the setlists list on a low-end Android device and compare with the previous build: no
   regression in scroll smoothness (the per-row overhead e7245858d removed stays removed).
