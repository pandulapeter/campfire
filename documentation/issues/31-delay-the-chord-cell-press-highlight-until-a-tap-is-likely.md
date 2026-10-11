# Delay the chord cell's press highlight until a tap is likely, so a scroll that starts on a diagram does not flash it

**Challenged:** amended — the recommended quick-tap pulse left `isPressed` stuck true when a new gesture cancelled it mid-delay (the next touch lit at once, and a scroll started within the delay kept the cell lit); the plan now requires the job held outside the `pointerInput` block and `isPressed = false` set synchronously on every new first down, and spells out that the up must still be consumed on both paths.

**Kind:** polish  ·  **Severity:** low  ·  **Platforms:** Android, iOS (touch); the web and desktop on touch screens
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongChordsSection.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/CLAUDE.md`

## Problem

The chord diagrams at the top of a song are drawn in one `Layout`, whose `pointerInput` (`SongChordsSection.kt` ~L196–214
at d650f3cb0) lights the pressed cell the moment a finger lands on it:

```kotlin
awaitEachGesture {
    val down = awaitFirstDown()
    // A right or middle click is a context menu or a paste, as it is to the step taps around the song.
    if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
    val cell = layouts.cellAt(down.position.x, down.position.y, size.width, firstSlot, lastSlot, isLastSlot, isRightToLeft)
    if (cell < 0) return@awaitEachGesture
    pressedCell = cell
    isPressed = true
    // Null where the scroll took the press as a drag, which then scrolls the song as anywhere else.
    val up = waitForUpOrCancellation()
    isPressed = false
    ...
```

`isPressed` drives `pressProgress` (`animateFloatAsState`, `fastEffectsSpec`), which `drawBehind` draws as a rounded
rectangle behind `pressedCell`. The diagrams sit inside the song's vertical scroll, and on a phone they are a large part
of the first screen, so a scroll is very often started on one: the cell starts lighting up on touch, the scroll takes the
drag after the touch slop, `waitForUpOrCancellation` returns null, and the highlight fades out again — a flash on every
such scroll, which reads as the app thinking it was tapped. Material's own `clickable` avoids exactly this inside a
scrolling container by delaying its press indication (Compose's `TapIndicationDelay`, the platform tap timeout, about
100ms) and showing it at once only for a tap that ends before that.

## Fix

Hold the highlight back for a short delay on touch, and show it on release for a tap quicker than that:

1. Add a private constant, e.g. `private const val PRESS_HIGHLIGHT_DELAY_MILLIS = 100L` with a KDoc saying it is the tap
   timeout Material waits inside a scrolling container before lighting a press, so that a drag the scroll takes never
   lights it. (Compose's `ViewConfiguration` has no tap timeout in common code; 100ms is Android's `getTapTimeout()`.)
2. In the gesture, after the cell is found, for `down.type == PointerType.Mouse` keep today's behavior (a mouse never
   starts a scroll by dragging the content). For everything else:

   ```kotlin
   pressedCell = cell
   var isFinished = false
   var up = withTimeoutOrNull(PRESS_HIGHLIGHT_DELAY_MILLIS) { waitForUpOrCancellation().also { isFinished = true } }
   if (!isFinished) {
       isPressed = true
       up = waitForUpOrCancellation()
       isPressed = false
   }
   ```

   (`withTimeoutOrNull` is a member of `AwaitPointerEventScope`, so it is allowed inside `awaitEachGesture`. The flag is
   needed because `waitForUpOrCancellation` itself returns null for a cancelled press.) Both paths then end as today,
   `if (up != null) { up.consume(); currentOnCellClicked?.invoke(cell) }`: the consumed up is what keeps `stepOnTap`
   from also turning the page (it returns on a consumed up, the home-gesture fix d63d758ff), and the down is still left
   unconsumed so the scroll can take a drag — nothing about consumption changes. A drag the scroll takes within the
   delay makes `waitForUpOrCancellation` return null inside the timeout, so the cell is never lit at all; one it takes
   later fades the highlight out as today, as Material's own `clickable` does.
3. A tap that ends before the delay still needs to be seen: setting `isPressed` true and false in the same frame draws
   nothing. Options:
   - **(recommended)** Hold the highlight for a short pulse after a quick tap: take `val scope = rememberCoroutineScope()`
     in the composable and, when `isFinished && up != null`, `scope.launch { isPressed = true; delay(PRESS_HIGHLIGHT_DELAY_MILLIS); isPressed = false }`
     before invoking the click (a gesture handler cannot `delay` itself; its scope is restricted). Keep the pulse's `Job`
     in a remembered holder, not in a local of the `pointerInput` block, which a restart of it (its keys change with a
     width or a transposition) would lose. **When a new gesture finds its first down, cancel that job and set
     `isPressed = false` there, synchronously, before anything else**: a pulse cancelled mid-`delay` never runs its
     `isPressed = false`, so without the reset the next touch would light its cell at once (defeating the delay) and a
     scroll the next touch turns into within the delay (`up == null`, `isFinished`) would leave the highlight stuck on
     until some later tap. Do not reset in a `finally` of the pulse instead: cancellation resumes it later, on the
     dispatcher, where it would put out the highlight a mouse press has lit meanwhile.
   - Skip the highlight for quick taps altogether, since the tap opens the chord's sheet at once. Simpler, but a tap
     with no press feedback breaks the cell's affordance on a slow device where the sheet takes a moment.
4. Update the comment on `pressedCell` ("The last cell pressed, kept while its highlight fades out after the finger has
   gone.") if needed, and the `ui/chords/CLAUDE.md` sentence "the cell pressed is lit behind it while the finger is down"
   (~L48–49) to say it is lit once the press has lasted the tap timeout, or briefly on a quicker tap, so a scroll started
   on a diagram never lights it.

## Tests

None: this is pointer input timing inside a composable, which the project does not unit test.

## Manual check

On an Android phone and an iPhone, open a song with chord diagrams. Start a vertical scroll with the finger on a
diagram and drag quickly: no cell lights up. Press and hold a diagram without moving: it lights after a moment and the
chord's sheet opens on release. Tap a diagram quickly: a brief highlight shows and the sheet opens. On the desktop,
clicking a diagram with the mouse still lights it at once.
