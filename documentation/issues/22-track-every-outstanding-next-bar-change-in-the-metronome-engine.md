# Track every next-bar change the metronome engine has not heard yet, so a second one made before the first is heard does not leave the first's bar reported as the old pattern

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `metronome/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/metronome/implementation/MetronomeEngine.kt`,
`metronome/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/metronome/implementation/MetronomeEngineTest.kt`,
`metronome/implementation/CLAUDE.md`

Execute before plan 21, which reads `Playing.pattern` as the bar that is heard.

## Problem

`MetronomeEngine` remembers only the newest change made from the next bar (`MetronomeEngine.kt` at d650f3cb0):

```kotlin
private var lastBarChangeId = 0
private var pendingBarChangeId: Int? = null
...
fromNextBar -> {
    val barChangeId = ++lastBarChangeId
    pendingBarChangeId = barChangeId
    stream?.update(pattern, barChangeId)
    _playback.value = playing.copy(pendingPattern = pattern)
}
```

and turns `pendingPattern` into `pattern` only when a heard tick carries exactly that id (`releaseHeardBeats`, L188–196):

```kotlin
val barChangeId = pendingBarChangeId
if (barChangeId != null && tick.barChangeId == barChangeId) {
    pendingBarChangeId = null
    (_playback.value as? MetronomePlayback.Playing)?.let { playing ->
        _playback.value = playing.copy(pattern = playing.pendingPattern ?: playing.pattern, pendingPattern = null)
    }
}
```

The sequencer applies a change when it *renders* the barline, which is up to the output's queue (about 100 ms,
`AudioOutput`'s companion) before the barline is *heard*. If the player pages A → B and then B → C inside that window —
B's first ticks already rendered with id 1 but not yet heard — the engine forgets id 1: B's whole first bar is heard with
ticks carrying id 1, none matches id 2, and `Playing` stays `pattern = A, pendingPattern = C` until C's first click. For
that bar the song details panel draws A's time signature (`MetronomePanelState.kt` L52), the metronome icon's swing uses
A's tempo (`MetronomeIcon.kt` L149) and the Android notification says A's tempo and signature (`CampfireApp.kt` L396),
while B is what is heard.

Re-proved with a throwaway engine probe (deleted): start 4/4 at 120, render the first bar, `update(B (3/4 at 90),
fromNextBar = true)`, render B's first tick, `update(C, fromNextBar = true)`, then let the output's heard frame pass B's
first tick and on into its bar — `playback` stayed `Playing(pattern = A, pendingPattern = C)` throughout.

Correction to the review: the window is not "C within B's first bar" but C before B's first click is *heard* while it is
already rendered, about the output's queue depth before the barline — two page steps in quick succession across a
barline (a pedal pressed twice, or a fling the pager retargets). Display only; what is heard is right.

## Fix

Keep every outstanding change from the next bar, in order, instead of one id:

```kotlin
/** The changes from the next bar not heard yet, oldest first: their ids, and the pattern each one brings. */
private val pendingBarChanges = ArrayDeque<Pair<Int, MetronomePattern>>()
```

- `updatePlaying`, `fromNextBar`: `pendingBarChanges.addLast(barChangeId to pattern)` instead of setting
  `pendingBarChangeId`.
- `updatePlaying`, a change from the next beat while `playing.pendingPattern != null`: also replace the pattern of the
  newest entry (`pendingBarChanges.lastOrNull()?.let { (id, _) -> pendingBarChanges[pendingBarChanges.lastIndex] = id to pattern }`),
  since the sequencer makes such a change wait with the newest bar change (or, when that one is already rendered, places
  it with that change's id), which is what `pendingPattern` already says.
- `releaseHeardBeats`: for a heard tick whose `barChangeId` is the id of an entry, drop that entry and every older one,
  and set `pattern` to that entry's pattern; `pendingPattern` becomes the newest remaining entry's pattern, or null when
  none remains:

  ```kotlin
  val heardIndex = tick.barChangeId?.let { id -> pendingBarChanges.indexOfFirst { it.first == id } } ?: -1
  if (heardIndex >= 0) {
      val heard = pendingBarChanges[heardIndex].second
      repeat(heardIndex + 1) { pendingBarChanges.removeFirst() }
      (_playback.value as? MetronomePlayback.Playing)?.let { playing ->
          _playback.value = playing.copy(pattern = heard, pendingPattern = pendingBarChanges.lastOrNull()?.second)
      }
  }
  ```

- `closeOutput`: `pendingBarChanges.clear()` where it now nulls `pendingBarChangeId`.
- `applyPendingNow` checks `pendingBarChangeId != null`; make it `pendingBarChanges.isNotEmpty()` (or drop it with plan
  23 if that plan's option (a) has landed first).

Remove `pendingBarChangeId`. Update the KDoc of `updatePlaying` to say that a second change from the next bar made
before the first is heard queues behind it, each becoming `pattern` with its own first heard click.

In `metronome/implementation/CLAUDE.md`, the sentence "Such a change has an id, carried by every tick it places, and the
engine reports it as `Playing.pendingPattern` until the first of those ticks is heard" gains: "the engine keeps every
such change not yet heard, so one made while another is rendered but not heard leaves `Playing.pattern` following each
in turn".

## Tests

In `MetronomeEngineTest`, `a change from the next bar made before the one before it is heard is reported in turn`:
start `pattern` (4/4 at 120); `output.scheduleBeats(from = 0, to = 3)`; `engine.update(b, fromNextBar = true)` with
`b = MetronomePattern(bpm = 120, timeSignature = TimeSignature(3, 4))` (the same tempo keeps the frames simple);
`runCurrent()`; `output.scheduleBeats(from = 3, to = 5)` (renders A's last beat and B's first, with id 1);
`engine.update(c, fromNextBar = true)` with `c = MetronomePattern(bpm = 90)`; `runCurrent()`; assert
`Playing(pattern, pendingPattern = c)`; then set `output.heardFrame = 4 * BEAT_FRAMES`, `advanceTimeBy(RELEASE_STEP_MILLIS)`,
`runCurrent()`, and assert `Playing(b, pendingPattern = c)`. Before the fix the last assertion sees
`Playing(pattern, pendingPattern = c)`.

The existing `a change from the next bar is pending until its first click is heard` and `a tempo stepped while a change
waits for the next bar replaces what waits` must still pass. Run `./gradlew :metronome:implementation:desktopTest`.

## Manual check

On Android (the deepest queue), play the metronome on a setlist whose first song is 4/4 and second 3/4 with a different
tempo, and page forward twice in quick succession right at a barline (two quick pedal presses or arrow keys on the
desktop). While the second song's bar plays, the panel's beat row should show three beats and the notification its
tempo, before moving on to the third song's.
