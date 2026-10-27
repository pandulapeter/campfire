# Count a nudge of a song scrolling by itself from where the one still moving is headed, so two quick pedal presses are two nudges

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/autoScroll/SongAutoScroller.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/autoScroll/CLAUDE.md`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/AutoScrollTest.kt`
(created by plan 03; this plan comes after it in lane A)

## Problem

In the one-column layout (`SongLayout.CONTINUOUS`), Up / Down, the step buttons, and the tap halves nudge the song two
chorded lines. `SongAutoScroller.nudge` scrolls by a distance from wherever the song is:

```kotlin
suspend fun nudge(direction: Int) {
    val distance = flingBehavior.readingWindow.overlap.coerceAtLeast(MIN_NUDGE_PX) * NUDGE_LINES
    scrollState.animateScrollBy(
        value = (distance * direction).toFloat(),
        animationSpec = tween(durationMillis = NUDGE_DURATION, easing = FastOutSlowInEasing),
    )
}
```

A second press within `NUDGE_DURATION` (250 ms) cancels the first animation (the scroll's mutex) and scrolls its own
distance from where the first was cut off, so two quick pedal presses move somewhere between one and two nudges
(about 1.3 to 1.6, depending on when in the eased curve the second lands). A player who taps the pedal twice to skip
four lines lands mid-line.

The pages layout already solved this: `stepping/SongStepper.kt` keeps `stepTarget` ("A press mid-step steps on from its
target: two quick presses are two steps", `stepping/CLAUDE.md`):

```kotlin
private var stepTarget by mutableStateOf<Int?>(null)
val origin get() = stepTarget ?: scrollState.value
…
suspend fun step(direction: Int, from: Int? = null) {
    val target = stepTargetFrom(from ?: origin, direction) ?: return
    stepTarget = target
    try {
        …
        scrollState.animateScrollTo(target, tween(durationMillis = duration, easing = FastOutSlowInEasing))
    } finally {
        if (stepTarget == target) stepTarget = null
    }
}
```

## Fix

Match `SongStepper` in `SongAutoScroller`:

```kotlin
// Where the nudge being animated is headed, which the next one is counted from: two quick presses of a pedal are two
// nudges from where the song was, not one and a bit from wherever the first had got to. State, since whether there
// is anything left to nudge to is read from it.
private var nudgeTarget by mutableStateOf<Int?>(null)

private val nudgeOrigin get() = nudgeTarget ?: scrollState.value

fun canNudge(direction: Int) = if (direction < 0) nudgeOrigin > 0 else nudgeOrigin < scrollState.maxValue

suspend fun nudge(direction: Int) {
    val distance = flingBehavior.readingWindow.overlap.coerceAtLeast(MIN_NUDGE_PX) * NUDGE_LINES
    val target = (nudgeOrigin + distance * direction).coerceIn(0, scrollState.maxValue)
    nudgeTarget = target
    try {
        scrollState.animateScrollTo(target, tween(durationMillis = NUDGE_DURATION, easing = FastOutSlowInEasing))
    } finally {
        if (nudgeTarget == target) nudgeTarget = null
    }
}
```

- The second press sets the new target before its `animateScrollTo` cancels the first, so the first's `finally` leaves
  it alone; a drag or fling cancelling a nudge clears it.
- `canNudge` counted from the target, as `SongStepper.canStep` is from `origin`: a press while a nudge is already headed
  for the end goes on to the setlist's next song (`SongDetailsScreen.stepForward` falls through when `nudge` answers
  false) rather than being spent on nothing. `canScrollForward` / `canScrollBackward` were the old test; at rest they
  agree with this one.
- Update `nudge`'s KDoc: a press while a nudge moves counts from where it is headed.
- Remove the now unused `animateScrollBy` import.

If plan 03 has landed, nothing else is needed: `animateScrollTo` sets `isScrollInProgress`, so `play()` skips those
frames and the anchor is retaken at rest as before.

Docs: in `autoScroll/CLAUDE.md`'s "What moves and what stops it", after "nudge two chorded lines (smoothly, playing or
not)", add "; a press while one moves nudges on from where it is headed, so two quick presses are two nudges".

## Tests

A UI test in `AutoScrollTest` (from plan 03, which adds `runAppTest`'s `songLayout` parameter):
`` `two quick presses of the pedal nudge a scrolling song twice as far as one` ``. With
`songLayout = SongLayout.CONTINUOUS` on `TestWindow.PHONE` and `campfire_tests-the_long_road_north.cho`, open the
song, note a lyric line's top (semantics `positionInRoot`), press Down, let it settle (`waitForIdle`), and measure how
far the line moved (`one`). Then with `mainClock.autoAdvance = false`, press Down, `mainClock.advanceTimeBy(100)`,
press Down again, turn `autoAdvance` back on, settle, and measure how far a line moved over both presses (`two`).
Assert `abs(two - 2 * one) <= 1`. Before the fix `two` falls well short. Don't play the song in this test, so nothing
else moves it.

Run `./gradlew :tools:screenshots:test --tests '*AutoScrollTest*'`.
