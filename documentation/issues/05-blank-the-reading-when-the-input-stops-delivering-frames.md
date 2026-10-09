# Blank the reading when the input stops delivering new frames

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (the web most readily: a suspended `AudioContext`)
**Challenged:** amended — corrected the iOS tap figure (Apple documents a tap's buffers as 100–400 ms, not 85 ms) and made the hold's margin over it explicit; gave the fake the `advanceEvery` that test 3 needs; said that 02 and 04, landing after this, carry the merged loop.
**Files:** `tuner/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/implementation/AudioInput.kt`,
`.../commonMain/.../SampleRing.kt`, `.../commonMain/.../TunerEngine.kt`, `.../wasmJsMain/.../WebAudioInput.kt`,
`.../wasmJsMain/.../WebTunerAudio.kt`, `.../commonTest/.../FakeAudioInput.kt`, `.../commonTest/.../SampleRingTest.kt`,
`.../commonTest/.../TunerEngineTest.kt`, `tuner/implementation/CLAUDE.md` (all under
`tuner/implementation/src/<sourceSet>/kotlin/com/pandulapeter/campfire/tuner/implementation/`). The Android, desktop and
iOS inputs (`override fun latest(window: FloatArray) = ring.latest(window)`) compile unchanged, their return type
being inferred.

Lands after plan 01 (it changes the fake and the ring test plan 01 writes). Touches `TunerEngine.hear` like plans 02
and 04, which land after it in the lane and give their fixes in the form that fits this one's loop.

## Problem

When an input stops delivering frames without saying so, the tuner shows the last note heard, frozen, until the screen
is left:

- `SampleRing.latest` answers `true` and copies the same window again whenever a window has ever been written:

  ```kotlin
  fun latest(window: FloatArray): Boolean {
      val end = written
      if (end < window.size) return false
      ...
      return true
  }
  ```

  so a capture thread that blocks (a `TargetDataLine` whose device went away without an error, an `AVAudioEngine` tap
  that stops being called) leaves the engine reading one window for ever.
- `WebAudioInput.latest` keeps calling `getFloatTimeDomainData` after the context stopped running (it only reports
  `WAITING_FOR_GESTURE`), and a suspended analyser hands back its last frames unchanged:

  ```kotlin
  val running = isTunerContextRunning()
  if (running != isRunning) { ... listener.onIssueChanged(if (running) null else TunerInputIssue.WAITING_FOR_GESTURE) }
  if (!readTunerWindow()) return false
  ```

  and `TunerEngine.hear` publishes the reading alongside the issue
  (`Hearing(reading = tracked.reading…, issue = gestureIssue ?: …)`).

The tracker cannot let go on its own: a repeated window of a clear note is still "heard", so `HOLD_MILLIS` never runs
out. Proved at b5c8ed3b5 with a probe whose input returned one 330 Hz window on every poll: `Hearing(reading =
TunerReading(note = 64, cents = 1.96, isInTune = true))` at 1 s and unchanged at 11 s. Simply returning `false` from
`latest` when nothing new arrived would freeze the reading just the same, since the tracker only releases inside
`step`, which the engine calls only when `latest` is true.

## Fix

Let `latest` say where the input is, and let the engine notice a position that stops moving.

1. `AudioInput.latest` returns the input's frame position instead of a `Boolean`:

   ```kotlin
   /**
    * Copies the latest `window.size` frames into [window], full scale being ±1, and answers how many frames the input
    * has delivered up to the end of that window: a position that grows only while frames arrive, so that a window
    * handed out twice is known for one. [NO_WINDOW] while fewer than a window have arrived.
    */
   fun latest(window: FloatArray): Long

   companion object {
       const val NO_WINDOW = -1L
   }
   ```
2. `SampleRing.latest` returns `end` (the `written` it read) instead of `true` and `AudioInput.NO_WINDOW` instead of
   `false`; update its KDoc ("answers whether…" → "answers the position of the window's end, …").
3. Web: add to `WebTunerAudio.kt`

   ```kotlin
   /** The frames the tuner's context has rendered, which stand still while it is suspended. */
   internal fun tunerFramePosition(): Double = js("window.__campfireTuner.context ? Math.round(window.__campfireTuner.context.currentTime * window.__campfireTuner.context.sampleRate) : -1")
   ```

   and in `WebAudioInput.latest` return `AudioInput.NO_WINDOW` where it returns `false` today, and
   `tunerFramePosition().toLong()` after copying the window.
4. `TunerEngine.hear` tracks when the position last moved, and lets go of the reading once it has stood still for the
   tracker's hold, or at once while the input says it cannot hear (the web waiting for a gesture):

   ```kotlin
   var lastPosition = AudioInput.NO_WINDOW
   var movedAt = timeSource.markNow()
   while (sessionId == session) {
       ...
       val position = input.latest(window)
       if (position != AudioInput.NO_WINDOW && position != lastPosition) {
           lastPosition = position
           movedAt = timeSource.markNow()
       }
       // A window handed out again is still a clear note to the tracker, which would hold it for ever: an input that
       // has stopped delivering frames, or a context waiting for a gesture, is heard as nothing.
       if (gestureIssue != null || movedAt.elapsedNow().inWholeMilliseconds >= PitchTracker.HOLD_MILLIS) {
           tracker.reset()
           setListening(TunerListening.Hearing(issue = gestureIssue))
       } else if (position != AudioInput.NO_WINDOW) {
           ... detect and step as today
       }
       delay(POLL_INTERVAL_MILLIS)
   }
   ```

   A window repeated for less than the hold is still stepped as today: iOS's tap asks for 4096 frames, but
   `installTapOnBus` documents its buffer size as a request within 100–400 ms, and in practice a tap is handed about
   100 ms (4800 frames at 48 kHz) at a time, so several polls in a row see the same window, and the tracker's timing
   (three answers to take a target, the median of five) must not change for it. The hold (500 ms) has to stay above
   the 400 ms the tap may be handed at worst plus a poll; do not shorten it below that for this check.

## Tests

- `FakeAudioInput` (plan 01): keep `signal` writing the window (`false` meaning no window yet), and add
  `var isFrozen = false` and `var advanceEvery = 1`; `latest` returns `NO_WINDOW` when `signal` returns false, else a
  private position that grows by 1 584 frames (33 ms at 48 kHz) on every `advanceEvery`-th call unless `isFrozen`.
- `SampleRingTest`: the cases written by plan 01 assert positions instead of booleans — `NO_WINDOW` before a window,
  the running total of frames written after each chunk, and the same position when `latest` is called twice without a
  write (`` `a window handed out again has the same position` ``).
- `TunerEngineTest`:
  1. `` `an input that stops delivering frames lets go of the reading` `` — `sine(330f)` for one second → reading 64;
     `isFrozen = true`; after `advanceTimeBy(600)` the reading is null, and still null at 5 s. Fails at b5c8ed3b5
     (with the fake adapted, the reading stays).
  2. `` `an input waiting for a gesture shows no reading` `` — reading 64, then
     `input.listener!!.onIssueChanged(WAITING_FOR_GESTURE)`; after one poll `Hearing(reading = null, issue =
     WAITING_FOR_GESTURE)`; `onIssueChanged(null)` and a second later the reading is back.
  3. `` `a window repeated for less than the hold is still read` `` — `advanceEvery = 3` (iOS's tap) still publishes
     reading 64 within a second, and keeps it for five seconds. (A guard, not a failing case: it fails only if the
     let-go is made too eager.)

## Manual check

Web (Chrome): open the tuner, allow the microphone, play a note so a reading shows, then suspend the context from the
DevTools console (`window.__campfireTuner.context.suspend()`): the meter returns to rest within half a second and the
gesture notice shows; a tap resumes it and notes are read again. Desktop: with a reading showing, unplug a USB
microphone; the meter rests within half a second rather than holding the note.

## Docs

`tuner/implementation/CLAUDE.md`: in the `TunerEngine` bullet, add that a window whose position has not moved for half
a second, or an input waiting for a gesture, is heard as nothing; in the `SampleRing` bullet, that `latest` answers the
position of the window's end.
