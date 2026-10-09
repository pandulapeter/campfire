# Stop the tracker learning from the speaker while a tone sounds, and blank readings briefly after it stops

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** amended — written against b5c8ed3b5's `if (input.latest(window))`, which plan 05 (landing first) replaces; added the merged loop, which keeps calling `latest` on every poll (the web's ended-track and gesture checks live in it) and folds the speaker check into plan 05's let-go branch.
**Files:** `tuner/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/implementation/TunerEngine.kt`,
`tuner/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/tuner/implementation/TunerEngineTest.kt`,
`tuner/implementation/CLAUDE.md`, `tuner/api/CLAUDE.md`

Lands after plan 01 (which creates `TunerEngineTest`, `FakeAudioInput` and `FakeToneOutput`) and after plan 05 (see
Fix, step 3).

## Problem

While a reference tone sounds the microphone stays open (by design: reopening it would ask again), and the contract in
`tuner/api/CLAUDE.md` says "A reading is never published while a tone sounds, since the speaker is what the microphone
would hear." `TunerEngine.hear` only masks the *publishing*; the tracker keeps learning from the speaker:

```kotlin
if (input.latest(window)) {
    val range = PitchDetector.rangeFor(config.tuning, config.referencePitch)
    val estimate = detector.detect(window, range.start, range.endInclusive)
    val tracked = tracker.step(estimate, start.elapsedNow().inWholeMilliseconds, config)
    // What the microphone hears while a tone sounds is the speaker, so it is not read.
    setListening(
        TunerListening.Hearing(
            reading = tracked.reading.takeIf { _state.value.tone == null },
            issue = gestureIssue ?: TunerInputIssue.SILENT.takeIf { tracked.isSilent },
        )
    )
}
```

So the moment `stopTone` clears `tone`, the next poll publishes the tracker's reading of the synthesized tone itself —
the exact note, in tune — and keeps it for the tracker's 500 ms `HOLD_MILLIS`, plus however long the 85 ms window and
the input's latency still carry the tone. A player who taps a string chip to hear E2, taps again to stop it, and starts
tuning sees a green "in tune E2" for half a second or more that their string never played.

Proved at b5c8ed3b5 with a probe on virtual time (fake input returning a 440 Hz sine while the fake tone played, then
digital silence from the instant of `stopTone`): every poll from +33 ms to +495 ms after the stop published
`TunerReading(note=69, cents=-0.003, isInTune=true)`; it went null at +528 ms. On a device the tail is longer, since
the window and the output/input latency still hold the tone after the stop.

## Fix

In `TunerEngine`:

1. Add `private var toneEndedAt: TimeMark? = null` (import `kotlin.time.TimeMark`), set to `timeSource.markNow()`
   whenever a sounding tone ends. Route both places that clear `tone` through one helper so that neither forgets it:

   ```kotlin
   private fun setTone(note: Int?) {
       if (_state.value.tone != null && note == null) toneEndedAt = timeSource.markNow()
       _state.value = _state.value.copy(tone = note)
   }
   ```

   `playTone` ends with `setTone(note.takeIf { isPlaying })` (a replacement that fails to play also ends the old
   tone), and `stopToneNow` with `setTone(null)`.
2. In `hear`, before the detector runs, work out whether the window can hold the speaker: a tone sounds, or one ended
   less than a window plus a margin ago. Then the tracker is reset rather than stepped, and nothing is read:

   ```kotlin
   // The window still holds the tone for its own length after the speaker stops, and the input's latency on top.
   val toneTail = (detector.windowSize * 1_000L / sampleRate + TONE_TAIL_MARGIN_MILLIS).milliseconds
   ...
   val isHearingSpeaker = _state.value.tone != null || toneEndedAt?.let { it.elapsedNow() < toneTail } == true
   if (isHearingSpeaker) {
       tracker.reset()
       setListening(TunerListening.Hearing(issue = gestureIssue))
   } else if (input.latest(window)) {
       ... as today, with `reading = tracked.reading`
   }
   ```

   with `const val TONE_TAIL_MARGIN_MILLIS = 150L` in the companion (about 235 ms in all at 48 kHz). Replace the
   existing `// What the microphone hears while a tone sounds…` comment with one on the new branch saying why the
   tracker is reset rather than only hidden (it would otherwise come out of the tone holding the tone's own reading).
   The `takeIf { _state.value.tone == null }` goes, since the branch above covers it.
3. **Plan 05 lands before this one** (lane order 01, 03, 05, 02), so the `if (input.latest(window))` quoted above is by
   then a frame position, and the snippet in step 2 is merged into plan 05's loop rather than applied as written.
   `input.latest(window)` stays the first thing every poll does, tone or not: on the web it is also where an ended
   track is reported and the context's state is noticed, and plan 05's position bookkeeping must keep moving under a
   long tone, or the first poll after it would be taken for a stalled input. Only the detector and the tracker are
   skipped:

   ```kotlin
   val position = input.latest(window)
   if (position != AudioInput.NO_WINDOW && position != lastPosition) {
       lastPosition = position
       movedAt = timeSource.markNow()
   }
   val isHearingSpeaker = _state.value.tone != null || toneEndedAt?.let { it.elapsedNow() < toneTail } == true
   val isStalled = movedAt.elapsedNow().inWholeMilliseconds >= PitchTracker.HOLD_MILLIS
   if (isHearingSpeaker || gestureIssue != null || isStalled) {
       // (one comment for the three: the tracker is reset rather than only hidden, or it would come out of the tone,
       // or of a window handed out again, still holding what it read there)
       tracker.reset()
       setListening(TunerListening.Hearing(issue = gestureIssue))
   } else if (position != AudioInput.NO_WINDOW) {
       ... detect and step as plan 05 left it, with `reading = tracked.reading`
   }
   ```
4. `SILENT` is not computed while the speaker is heard (the tracker is not stepped); that is fine, as the tone is not
   digital silence, and two seconds of zeros after it still raise it.

The tracker's `reset()` already clears the onset state, so the first string plucked after the tail is read normally.

## Tests

In `TunerEngineTest` (collect states with
`backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { engine.state.toList(states) }`, as
`MetronomeEngineTest` does):

1. `` `the tone itself is never read after it stops` `` — listen chromatic; `playTone(69, 440)`; input returns a
   440 Hz sine while `speaker` is true; `advanceTimeBy(1_000)`; `stopTone()`; keep the sine for another 100 ms
   (the window and latency still holding it), then zeros; advance 1 s. Assert that no state recorded from the
   `stopTone` onward has a non-null `reading`. Fails at b5c8ed3b5 (the probe above).
2. `` `a string plucked after the tone's tail is read` `` — the same, then the input switches to a 330 Hz sine 300 ms
   after the stop; within a second a reading with `note == 64` is published.
3. `` `a tone that fails to replace a sounding one ends it for the reading too` `` — a tone sounding, then
   `output.isPlayable = false` and `playTone(64, 440)` → `tone == null`, and the reading stays null for the tail.

## Manual check

On a phone with the tuner open: tap a string chip, let the tone sound for two seconds, tap it again to stop it, and do
not play anything. The meter must stay at rest ("Play a note") rather than flashing the tone's note in tune. Then
pluck the string: it is read at once as usual.

## Docs

`tuner/implementation/CLAUDE.md`, the `TunerEngine` bullet: add that while a tone sounds, and for a window plus
150 ms after it stops, the tracker is reset and nothing is read. `tuner/api/CLAUDE.md`: "A reading is never published
while a tone sounds" → "… while a tone sounds, or for a moment after it stops, …".
