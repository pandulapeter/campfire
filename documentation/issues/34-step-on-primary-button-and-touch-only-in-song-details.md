# Step the song on a tap only for a touch or the mouse's primary button

**Challenged:** amended — the pointer kind is read from the change (`down.type`), not from `currentEvent.type`, which is a `PointerEventType` and would have made the check a no-op; `currentEvent` is confirmed as the down's event, so no extra `awaitPointerEvent`.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** desktop, web (mouse); Android (cancelled gesture, optional part)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/StepTaps.kt`,
`presentation/CLAUDE.md` (the step-on-tap description, if it names the pointer)

## Problem
`stepOnTap` starts a gesture with `awaitFirstDown(requireUnconsumed = false)`, which accepts a press of any mouse
button. A right or middle click on the song area (the one a user makes to open a context menu, or while pasting with the
wheel) steps the song a page forward or back on desktop and web. Nothing in the loop looks at `PointerEvent.buttons`.

Cancelled gestures: Compose synthesizes a cancel as an up change with `isInitiallyConsumed = true`
(`SuspendingPointerInputFilter.onCancelPointerInput`). The non-drag branch already ignores it (`!up.isConsumed`). The
"sloppy tap" branch, reached when `isDragged`, does not check `up.isConsumed`, so a gesture the system took away
(e.g. an edge back-swipe starting inside the area and ending under 250 ms and under 10 % of the height) can still
step. A normal sloppy tap's up is also commonly consumed by the scroll, so `isConsumed` cannot be used there — this
part is not provable from reading alone; see the optional step.

## Fix
1. Right after `awaitFirstDown`, look at the event the down came in (`currentEvent`, a property of
   `AwaitPointerEventScope`, is that event once `awaitFirstDown` returns — no extra `awaitPointerEvent`, which could
   swallow an up arriving with the down). The pointer's kind is a property of the **change**, `down.type`
   (`PointerType`); `PointerEvent.type` is a `PointerEventType` (Press/Move/Release) and would never equal
   `PointerType.Mouse`, so the check would always pass:
   ```kotlin
   val down = awaitFirstDown(requireUnconsumed = false)
   // A right or middle click is a context menu or a paste, not a reach for the next line. Touch and stylus presses
   // report no mouse buttons (Android's button state is 0 for a finger), so only a mouse is asked which one it was.
   val isOtherMouseButton = down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed
   if (isOtherMouseButton || down.isConsumed || currentIsMovingFreely()) return@awaitEachGesture
   ```
   Imports: `androidx.compose.ui.input.pointer.PointerType`, `androidx.compose.ui.input.pointer.isPrimaryPressed`.
   Touch (Android, iOS, a phone's browser) and stylus are unaffected whatever buttons they report; a trackpad click or
   tap-to-click arrives as a mouse press with the primary button and still steps. Keep the rest of the gesture exactly
   as it is.
2. Optional, only if the cancelled-gesture case is reproduced on a device: in the sloppy branch ignore gestures whose
   down started within the horizontal system-gesture inset (`WindowInsets.systemGestures` in the composable, passed in as
   `edgeInsetPx`), since those are the ones the system can take. Drop this step if it cannot be reproduced.
3. The accessibility part of the original suggestion (custom actions) is not part of this plan: the step buttons are
   still on screen and reachable.

## Tests
No pure logic beyond a boolean; none. If step 2 is done, extract `isInSystemGestureEdge(x: Float, width: Float, edge: Float)`
and test it in `presentation/src/commonTest` next to the other song details helpers.

## Manual check
Desktop: a right click and a middle click on the song do nothing; a left click on the top and bottom halves still step.
Android (optional): start a back swipe from the left edge on the song screen and cancel it half way: the song does not
step.
