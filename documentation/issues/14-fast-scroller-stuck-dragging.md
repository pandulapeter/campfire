# 14 · The fast scroller can get stuck "dragged": its bubble stays up and the thumb stops following the list

**Severity:** stuck state (all platforms; most likely on phones, where the keyboard going down under a search is enough
to trigger it; stays until the scroller is pressed again) · **Area:** `:presentation` (`components/FastScroller.kt`)

## Symptom
1. Android or iOS, songs screen. Open the search and type a query whose results are a little longer than the part of
   the screen the keyboard leaves free (a single letter in a library of ~20 songs is usually right).
2. Drag the fast scroller's thumb downwards.
3. The keyboard goes down as the list starts moving (`HideKeyboardWhenScrolledDown`), the results now fit on the
   screen, and the scroller fades away under the finger.
4. Clear the search (or close it) so the list is long again.

The scroller comes back with the letter bubble still showing, the thumb in the accent color, the track visible, and
the thumb parked where the drag left it: scrolling the list moves the bubble's letter but not the thumb. It stays like
that until the scroller itself is pressed again. The same happens whenever the list stops being scrollable in the
middle of a drag for any other reason — a sync run or an import removing songs, a filter chip tapped in the desktop
side panel with the other hand, a setlist archived on another device.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/FastScroller.kt:190-197`
only composes the element carrying the gesture while the list can scroll:

```kotlin
if (isVisible) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .hoverable(interactionSource)
            .thumbDragGestures(state = state, coroutineScope = coroutineScope)
    )
}
```

and the gesture (`FastScroller.kt:205-223`) only ends the drag on the way out of a gesture that ran to its end:

```kotlin
awaitEachGesture {
    val down = awaitFirstDown()
    down.consume()
    state.startDrag(pressY = down.position.y)?.let { fraction ->
        coroutineScope.launch { state.scrollToFraction(fraction) }
    }
    drag(down.id) { change -> ... }
    state.endDrag()
}
```

When `isScrollable` (`metrics != null`, `:242`) turns false mid-drag, the `Box` leaves the composition, its pointer
input coroutine is cancelled inside `drag`, and `endDrag()` never runs. `FastScrollerState` is remembered by the
scroller (`:95-101`), which stays composed, so `isDragging` stays true: `thumbTop` keeps returning the frozen
`draggedThumbTop` (`:247-248`), the bubble's `visible = state.isDragging && label != null` (`:164`), and
`hoverProgress`/`dragProgress` (`:113-120`) stay at 1. The only thing that resets it is the next `startDrag`/`endDrag`
pair.

## Fix
End the drag however the gesture ends, cancellation included:

```kotlin
private fun Modifier.thumbDragGestures(
    state: FastScrollerState,
    coroutineScope: CoroutineScope,
) = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        // Ended in a finally: the element this runs on is taken away the moment the list stops being scrollable,
        // which cancels the gesture in the middle of a drag - the keyboard going down under a search results list is
        // enough - and a drag that never ended left the bubble up and the thumb frozen the next time it came back.
        try {
            state.startDrag(pressY = down.position.y)?.let { fraction ->
                coroutineScope.launch { state.scrollToFraction(fraction) }
            }
            drag(down.id) { change ->
                // The delta has to be read before consuming the change, as consumed changes report none.
                val fraction = state.dragBy(change.positionChange().y)
                change.consume()
                coroutineScope.launch { state.scrollToFraction(fraction) }
            }
        } finally {
            state.endDrag()
        }
    }
}
```

`endDrag` only writes a snapshot state, which is fine from a `finally` of a cancelled coroutine.

Do **not** keep the gesture element composed while the list is not scrollable to avoid the cancellation: the column
would then take presses (and consume them) over a list that has nothing to scroll.

## Tests
None (UI is untested).

## Verify
1. Android: the repro above. Before: the bubble and the parked thumb come back with the long list. After: the scroller
   comes back idle (dim thumb, no bubble, no track) and follows the list as it scrolls.
2. Desktop (`./gradlew :app:desktop:run`), side panel visible: start dragging the thumb, and while holding it, have the
   list shrink below one screen (e.g. an import that is running finishes, or a tag filter applied with the keyboard
   Tab + Space). Release, widen the list again: scroller idle.
3. Ordinary use is unchanged: press on the track jumps, the thumb drags, the bubble shows the section letter on the
   songs screen and none on the setlists screen, hover shows the track on the desktop.

## Docs
None — `presentation/CLAUDE.md`'s `FastScroller.kt` bullet describes behavior that does not change.

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/FastScroller.kt`

## Depends on
Nothing.
