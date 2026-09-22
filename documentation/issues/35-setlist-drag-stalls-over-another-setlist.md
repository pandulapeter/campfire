# 35 · Dragging a setlist row past the end of its setlist makes the rows stop making way for up to a second

**Severity:** wrong behaviour (all platforms; likely whenever the last song of a setlist is dragged "to the bottom" and
overshoots, since the next setlist's first row is right below) · **Area:** `:presentation`
(`screens/setlists/SetlistsScreen.kt`)

## Symptom
1. Two setlists, A (five songs) followed by B.
2. Drag A's last row, by its handle or by a long press, down past A's end until it covers B's first row, then drag it
   back up over A's rows.

For up to a second the rows of A do not move out of the way; then they catch up in one jump. Holding the row over B's
rows keeps it frozen indefinitely (a new one second wait starts as soon as the last one ends). The same happens when a
drag auto-scrolls the list towards its bottom edge past the end of the setlist.

## Cause
Every row of every setlist is a drop target: `SetlistsScreen.kt:357-364` wraps each one in a `ReorderableItem` with
the default `enabled = true`, which puts its key into the reorderable state's `reorderableKeys`. Only the `onMove`
callback (`SetlistsScreen.kt:206-224`) knows that a move into another setlist means nothing:

```kotlin
// Songs can only be reordered within their own setlist.
if (fromKey.setlistFileName != null && fromKey.setlistFileName == toKey.setlistFileName && ...) {
```

By then the library (`sh.calvin.reorderable` 3.1.0, `ReorderableLazyCollection.kt`, `moveItems`) has already taken its
move lock and, after calling `onMove`, waits for the grid's layout to change before releasing it:

```kotlin
onMoveStateMutex.withLock {
    ...
    scope.(onMoveState.value)(draggingItem.data, targetItem.data)
    ...
    withTimeout(MoveItemsLayoutInfoUpdateMaxWaitDuration) {   // 1000 ms
        layoutInfoFlow.take(2).collect()
    }
```

An ignored move changes nothing, and the dragged row itself is moved by a `graphicsLayer` translation that does not
remeasure the grid, so the wait runs its full second. Meanwhile `onDrag` gives up on every move it would make
(`if (!onMoveStateMutex.tryLock()) return`), and on the next unlock the row still covering B's row starts the next wait.

## Fix
Take the other setlists' rows out of the targets for as long as a drag runs, with the `enabled` parameter
`ReorderableItem` has for this (a disabled item's key is removed from `reorderableKeys`, so it is never found as a
target; its handle is unaffected, and the library already disables every other handle during a drag).

In `SetlistList`:

```kotlin
// The setlist a drag was started in, known from the press that starts it rather than from its first move: the rows of
// every other setlist stop being places to drop it for as long as it lasts. A move onto one of them is refused below,
// but only after the reorderable state has locked itself for up to a second waiting for the list to answer it, which
// froze the rows under the finger.
var draggingSetlistFileName by remember { mutableStateOf<String?>(null) }
```

- `onDragStopped` (`:226-229`) also sets `draggingSetlistFileName = null`.
- Both handles report the start, with the setlist of the row they are on:
  `Modifier.draggableHandle(onDragStarted = { draggingSetlistFileName = setlistWithSongs.setlist.fileName }, onDragStopped = onDragStopped)`
  (`:384`), and the same `onDragStarted` on the two `longPressDraggableHandle` calls (`:396`, `:415`). A small local
  `val onDragStarted: (Offset) -> Unit = { draggingSetlistFileName = setlistWithSongs.setlist.fileName }` inside the
  row keeps the three identical.
- `ReorderableItem(..., enabled = draggingSetlistFileName.let { it == null || it == setlistWithSongs.setlist.fileName }, ...)`.

Keep the `onMove` check as it is: the keys are removed by an effect a frame after the drag starts, and the check is
what refuses a target found in that frame.

Do **not** reorder by recomputing from `setlistsWithSongs` in `onMove` or change `DraggedSetlist`; the drag's own
bookkeeping is right, only the targets are wrong.

## Tests
None (UI is untested).

## Verify
1. Android and desktop: the repro above. Before: rows freeze for about a second, repeatedly while hovering over B.
   After: hovering over B's rows does nothing, dragging back over A's rows moves them immediately.
2. Drag a row within a long setlist to the bottom edge so the list auto-scrolls past the setlist's end and into the
   next one: the scroll stops where it always did (the dragged row's own slot at the top), no stalls, and the drop
   writes the new order once (`reorderSetlist`).
3. Reorder rows inside one setlist by handle and by long press, including in a multi-column window: unchanged.
4. After a drag, start one in a different setlist: its rows respond (the targets were enabled again).
5. Rotate an Android phone mid-drag: the order the drag had reached is written, and the next drag in any setlist works.

## Docs
`presentation/CLAUDE.md`, the "**A drag is answered in the frame it is reported and written once when it ends.**"
bullet: add "The rows of the other setlists are not drop targets while a drag runs (`ReorderableItem`'s `enabled`, from
the setlist the drag started in): the reorderable state locks itself after every move it hands over until the list
answers it, and a move into another setlist, which is never answered, froze the drag for a second."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 11 changes the missing-entry menu at the bottom of the same file and 08 the placeholder branch of
`SetlistList`; schedule edits to the file one after another.
