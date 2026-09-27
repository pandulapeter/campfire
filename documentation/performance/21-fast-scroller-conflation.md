<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 21 — Scroll to the fast scroller's position at most once per frame

| | |
|---|---|
| Lane | C |
| Impact | low on Android; medium–low on the desktop with high-rate mice |
| Confidence | medium (not measured: whether AWT delivers several moves between two frames depends on the mouse) |
| Platforms | desktop mostly; all benefit |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/FastScroller.kt |
| Depends on / conflicts with | — |
| Commit message | `Scroll the list to the fast scroller's thumb at most once per frame.` |

## Problem
`FastScroller.kt:236-252` launches a scroll for every pointer event of a drag:

```kotlin
state.startDrag(pressY = down.position.y)?.let { fraction -> coroutineScope.launch { state.scrollToFraction(fraction) } }
...
drag(down.id) { change ->
    val fraction = state.dragBy(change.positionChange().y)
    change.consume()
    coroutineScope.launch { state.scrollToFraction(fraction) }
}
```

- `scrollToFraction` calls `gridState.scrollToItem`, which forces a synchronous remeasure of the grid.
- A fast-scroller drag jumps far, so each remeasure composes an entirely new screen of rows.
- Android batches input to vsync, so there it is about one per frame. On the desktop, AWT dispatches every mouse move, so a high-rate mouse can produce several jumps per frame. Every intermediate one composes and measures rows that are never drawn.
- Each event also allocates a coroutine that queues on the scroll mutex.

## Fix
Let the gesture only record where the list should go, and let one collector apply it once per frame:

```kotlin
// FastScrollerState
var pendingFraction by mutableFloatStateOf(NO_PENDING_SCROLL)

// FastScroller
LaunchedEffect(state) {
    snapshotFlow { state.pendingFraction }
        .filter { it != NO_PENDING_SCROLL }
        .collectLatest { fraction ->
            withFrameNanos { }                 // a newer fraction arriving before the frame cancels this one
            state.scrollToFraction(fraction)
        }
}
```

In `thumbDragGestures`, replace the three `coroutineScope.launch { state.scrollToFraction(f) }` calls with `state.pendingFraction = f`. The `coroutineScope` parameter is then unused and can go.

- The thumb itself is drawn from `draggedThumbTop` while dragging, so it keeps following the pointer on every event.
- After a drag ends, the last pending fraction is still applied, because `collectLatest` does not look at `isDragging`.
- Reset `pendingFraction` to `NO_PENDING_SCROLL` after applying it, so a later change of the list does not replay the jump.

What must not change:

- The tap-to-jump behavior.
- The grab-without-jump behavior (`startDrag` returns null).
- The Android back-gesture handling in `awaitThumbPress`.

## Verification
- `./gradlew :presentation:desktopTest :app:desktop:run`, and the Android debug build.
- Manual check:
  - Drag the thumb quickly up and down a 2,000-song library with a mouse on the desktop and with a finger on Android. The list must track the thumb, and the bubble must show the right letter.
  - A tap on the track must still jump the list.
- Measurement: log or trace the calls to `scrollToFraction` during a fast desktop drag. There should be no more than one per frame, where before there could be several.
