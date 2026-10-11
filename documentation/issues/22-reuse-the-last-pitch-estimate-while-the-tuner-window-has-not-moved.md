# Reuse the last pitch estimate while the tuner input's window has not moved

**Challenged:** sound

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** iOS above all; any input that hands out a window twice
**Files:** `tuner/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/implementation/TunerEngine.kt`,
`tuner/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/implementation/LatestEstimate.kt` (new),
`tuner/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/tuner/implementation/LatestEstimateTest.kt` (new),
`tuner/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/tuner/implementation/TunerEngineTest.kt`,
`tuner/implementation/CLAUDE.md`, `CLAUDE.md` (the root test list, only if it names the tuner's test classes)

## Problem

`TunerEngine.hear` polls the input every 33 ms and runs the whole pitch detector on every poll that is not stalled,
whether or not the input delivered anything new since the last one (`TunerEngine.kt:133-162` at ab97d3105):

```kotlin
val position = input.latest(window)
if (position != AudioInput.NO_WINDOW && position != lastPosition) {
    lastPosition = position
    movedAt = timeSource.markNow()
    ...
}
...
} else if (position != AudioInput.NO_WINDOW) {
    val range = PitchDetector.rangeFor(config.tuning, config.referencePitch)
    val estimate = detector.detect(window, range.start, range.endInclusive)
    val tracked = tracker.step(estimate, start.elapsedNow().inWholeMilliseconds, config)
    ...
}
```

`detect` high-passes 6,144 samples and runs two FFTs of a window padded to twice its length, the most expensive work the
tuner does. On iOS the input is an `AVAudioEngine` tap of 4,096 frames (`IosAudioInput`, `TAP_FRAMES`), about 85 ms at
48 kHz, so roughly two polls in three see the same position and the same samples, and the detector computes the same
estimate again; on Kotlin/Native, which is several times slower than the JVM's JIT for this arithmetic, that is most of
the tuner's CPU while it listens. Android and the desktop read 1,024-frame chunks (21 ms) and rarely repeat a position;
the web's position follows the `AudioContext`'s clock and repeats only while it is suspended.

The repeated windows must keep reaching the tracker: the engine deliberately feeds it a window handed out again (the
comment over `isStalled`: "The hold stays above the 400 ms an iOS tap may be handed at worst, so a window repeated
between two taps is still read"), and `PitchTracker`'s timings (the median of five answers, the three-answer target
hold, the 80 ms smoothing, the in-tune 300 ms) were tuned against that cadence. Skipping the step would change what
the tuner shows. Only the detection is redundant.

## Fix

Cache the last estimate by the window's position and the range it was searched in, and hand the cached estimate to
`tracker.step` exactly as today:

1. New `LatestEstimate.kt` in `commonMain`:

   ```kotlin
   /**
    * The detector's answer for the window at a position, kept until the input hands out a new one: an input that hands
    * out the same window again between two of its buffers (an iOS tap) is read again by the tracker, whose timings
    * expect a step every poll, but the window is not analysed twice.
    */
   internal class LatestEstimate(private val detect: (window: FloatArray, minFrequency: Float, maxFrequency: Float) -> PitchEstimate) {
       private var position = AudioInput.NO_WINDOW
       private var range: ClosedFloatingPointRange<Float>? = null
       private var estimate: PitchEstimate? = null

       fun of(window: FloatArray, position: Long, range: ClosedFloatingPointRange<Float>): PitchEstimate {
           estimate?.takeIf { position == this.position && range == this.range }?.let { return it }
           return detect(window, range.start, range.endInclusive).also {
               this.position = position
               this.range = range
               estimate = it
           }
       }
   }
   ```

   `PitchEstimate` is an immutable data class, so handing the same instance to the tracker twice is safe;
   `PitchDetector` keeps its buffers internally and returns a new estimate each call.

2. In `hear`, build it beside the detector, `val estimates = LatestEstimate(detector::detect)`, and replace the
   `detector.detect(...)` call with `estimates.of(window, position, range)`. Everything else in the loop (the stall
   check, the speaker check, the tracker reset paths, `setListening`) stays as it is. The cache lives inside one
   `hear` call, so a new session (a reopened input whose position restarts from zero) starts empty. A config change
   produces a new range from `rangeFor` (or an equal one, which is correct to reuse, since the window is the same), and
   the tracker is still reset for it as today.

A position identifies the window's samples: `AudioInput.latest` documents it as "how many frames the input has
delivered up to the end of that window", and the window's length is fixed per session, so the same position means the
same frames (the ring is written ahead of its count, `SampleRing`).

Update `tuner/implementation/CLAUDE.md`'s engine paragraph ("runs the detector and the tracker over it") to say a
window handed out again is not analysed again but its answer is still stepped through the tracker, and add
`LatestEstimateTest` to its test list.

## Tests

- `LatestEstimateTest` (pure, `commonTest`), with a counting `detect` lambda that answers a distinct estimate per call:
  the same position and range asked three times detects once and answers the first estimate each time; a new position
  detects again; the same position with another range detects again; and the answer for a position after another one
  is the new one, not the cached one.
- `TunerEngineTest`: the existing `a window repeated for less than the hold is still read` (which sets
  `input.advanceEvery = 3`, the iOS cadence) must keep passing unchanged, which shows the readings are still produced
  from repeated windows; every other engine test also stays as it is. Counting detections inside the engine itself
  would need a detector seam on `TunerEngine`'s constructor (a `createDetector` parameter, or making `PitchDetector`
  open); that is not worth widening the engine for, since `LatestEstimateTest` covers the caching and the engine only
  wires it in one line.

## Manual check

On an iPhone, open the Tuner tab, allow the microphone, and pluck each string of a guitar: notes, cents, the in-tune
check, and the hold after a string dies behave exactly as before. With Xcode's CPU gauge (or Instruments' Time
Profiler) while a string rings, the app's CPU while listening drops by roughly half or more against the build before,
with `PitchDetector.detect` running about twelve times a second instead of thirty.
