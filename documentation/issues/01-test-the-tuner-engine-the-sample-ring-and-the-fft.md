# Test the tuner engine, the sample ring and the FFT

**Kind:** tests  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `tuner/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/tuner/implementation/TunerEngineTest.kt`
(new), `.../FakeAudioInput.kt` (new), `.../FakeToneOutput.kt` (new), `.../SampleRingTest.kt` (new), `.../FftTest.kt` (new),
`tuner/implementation/CLAUDE.md`, root `CLAUDE.md` (the `:tuner:*` clause of the tests bullet)

This plan only adds tests and lands before every other tuner plan of this sweep (02–06 add cases to `TunerEngineTest`
and use the fakes written here). It changes no production code.

## Problem

`tuner/implementation/CLAUDE.md` heads its common code "Hearing a note (`commonMain`, all of it tested)", but at
b5c8ed3b5 only `PitchDetector`, `PitchTracker` and `ToneSynthesizer` have tests (`PitchDetectorTest`,
`PitchTrackerTest`, `ToneSynthesizerTest`, `RecordedStringsTest`). Three pieces of `commonMain` have none:

- `TunerEngine` — the state machine every platform runs through: sessions, the late answer of a start that was
  stopped, the refused start, the one reopen after `MICROPHONE_DISCONNECTED`, the masking of readings under a tone.
  It already takes what a test needs:

  ```kotlin
  internal class TunerEngine(
      private val input: AudioInput,
      private val output: ToneOutput,
      private val scope: CoroutineScope,
      dispatcher: CoroutineDispatcher,
      private val timeSource: TimeSource = TimeSource.Monotonic,
  ) : Tuner {
  ```
- `SampleRing` — the lock-free ring three inputs write into, whose index arithmetic (`((start + index) % capacity)`)
  is only exercised on a device.
- `Fft` — the radix-2 transform the detector's autocorrelation is built on; `PitchDetectorTest` covers it only
  indirectly.

## Fix

Add the tests below in `tuner/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/tuner/implementation/`
(package `com.pandulapeter.campfire.tuner.implementation`, MPL header copied from a sibling, backtick sentence names,
`kotlin.test` only — see the `code-style` skill).

### Shared fakes (one file each, so later plans reuse them)

`FakeAudioInput.kt`:

```kotlin
/** An input that records what the engine asks of it and hands out whatever window [signal] writes. */
internal class FakeAudioInput : AudioInput {
    var result: AudioInputStart = AudioInputStart.Started(SAMPLE_RATE)
    /** When set, a start waits for it, as the web's does for the browser's answer. */
    var gate: CompletableDeferred<Unit>? = null
    /** Whether a start waiting at [gate] ignores being cancelled, as a platform call that cannot be interrupted does. */
    var isStartNonCancellable = false
    var signal: (FloatArray) -> Boolean = { window -> window.fill(0f); true }
    var listener: AudioInputListener? = null
        private set
    var startCount = 0
        private set
    var stopCount = 0
        private set

    override suspend fun start(listener: AudioInputListener): AudioInputStart {
        startCount++
        this.listener = listener
        gate?.let { gate -> if (isStartNonCancellable) withContext(NonCancellable) { gate.await() } else gate.await() }
        return result
    }

    override fun latest(window: FloatArray) = signal(window)

    override fun stop() {
        stopCount++
    }

    companion object {
        const val SAMPLE_RATE = 48_000
    }
}
```

`FakeToneOutput.kt`: `var isPlayable = true`, `var onLost: (() -> Unit)? = null` (kept from the last `play`),
`playCount`, `stopCount`, `play` returning `isPlayable`.

### `TunerEngineTest`

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class TunerEngineTest {
    private val input = FakeAudioInput()
    private val output = FakeToneOutput()
    private val chromatic = TunerConfig(referencePitch = 440, tuning = null)

    /**
     * The engine runs in [TestScope.backgroundScope], which ends with the test: its poll never idles, so time is only
     * ever advanced by a given amount (never advanceUntilIdle). The scheduler's own time source is the engine's clock,
     * so that the tracker's time and the poll's delay move together.
     */
    private fun TestScope.engine() = TunerEngine(
        input = input,
        output = output,
        scope = backgroundScope,
        dispatcher = StandardTestDispatcher(testScheduler),
        timeSource = testScheduler.timeSource,
    )
}
```

A helper `sine(frequency: Float): (FloatArray) -> Boolean` filling the window with `TestSignals.sine(frequency,
FakeAudioInput.SAMPLE_RATE, window.size)` (copy into the window) makes the input "hear" a note. A steady sine from the
start is read after about 200 ms of virtual time (the onset is skipped for 60 ms and the target holds for three
answers), so advance one second before asserting a reading.

Cases (all verified against b5c8ed3b5 with a throwaway probe, which printed exactly these states):

1. `` `listening opens the input and reads what it hears` `` — `listen(chromatic)`, `runCurrent()` → `Hearing(reading =
   null, issue = null)`; input signal `sine(330f)`, `advanceTimeBy(1_000)` → the reading's `note` is 64, `|cents| < 5`.
2. `` `a refused start stops with its reason` `` — `input.result = Refused(PERMISSION_DENIED)` → `Stopped(PERMISSION_DENIED)`.
3. `` `a stop while the start is pending stops, and the late answer is ignored` `` — `input.gate = CompletableDeferred()`;
   listen → `Starting`; `stopListening()`, `runCurrent()` → `Stopped(null)`; complete the gate, `runCurrent()` → still
   `Stopped(null)`, `input.stopCount == 1`.
4. `` `a start that answers after a stop closes the input it opened` `` — the same with `isStartNonCancellable = true`:
   after the gate completes the state stays `Stopped(null)` and `stopCount == 2` (the stop, then the `else` branch of
   `open()` closing the input that opened for nobody).
5. `` `a disconnected input is opened again once` `` — listening, then `input.listener!!.onLost(MICROPHONE_DISCONNECTED)`,
   `runCurrent()` → `Hearing`, `startCount == 2`. Then a second `onLost(MICROPHONE_DISCONNECTED)` →
   `Stopped(MICROPHONE_DISCONNECTED)`, `startCount == 2`. (Plan 04 changes what happens when the two are far apart;
   keep this case with the two disconnects at most a second apart so that it stays true.)
6. `` `a lost input of an earlier session is ignored` `` — keep the first listener, `stopListening()`, `listen()` again,
   call `onLost(FAILED)` on the old listener → still `Hearing`.
7. `` `an update while hearing reads against the new tuning` `` — chromatic, signal `sine(Pitch.frequencyOf(46, 440))`,
   one second → note 46; `update(TunerConfig(440, InstrumentTuning.GUITAR))`, one second → note 45 and cents about
   +100 (the tracker was reset and the target is now the nearest string).
8. `` `an update while stopped does not start listening` `` — `update(chromatic)` on a fresh engine → `Stopped(null)`,
   `startCount == 0`. (The description's "update ignored while stopped" has no other observable effect: the next
   `listen` sets the config itself.)
9. `` `a tone sounding hides the reading` `` — listening with `sine(440f)`, `playTone(69, 440)` → `tone == 69` and
   `reading == null` while it sounds; `output.isPlayable = false` before a `playTone` → `tone == null`.
10. `` `a lost tone is no longer shown` `` — `playTone`, then `output.onLost!!.invoke()`, `runCurrent()` → `tone == null`,
    `output.stopCount == 1`.

### `SampleRingTest`

1. `` `nothing is handed out before a whole window has been written` `` — `SampleRing(16)`, a window of 4: `latest`
   false when empty and after writing 3 frames.
2. `` `the latest window is right across many wraparounds` `` — write a running count (`frame n = n.toFloat()`) in the
   uneven chunks `3, 5, 7, 2, 9, 4, 6, 1, 11, 8` (56 frames, 3.5 times the capacity); after every chunk past the first
   `latest` is true and the window equals the last four values written (`[n-4, n-3, n-2, n-1]`).
3. `` `16-bit samples are written at full scale` `` — `write(shortArrayOf(16_384, -32_768, ...), count)` reads back
   `0.5f`, `-1f`.
4. `` `clear starts over` `` — after `clear()`, `latest` is false again.

### `FftTest`

1. `` `a forward and an inverse transform give back the input times its size` `` — for sizes 8, 64 and 8192, a signal
   `sin(i·0.37) + 0.2·cos(i·1.3)`: after forward then inverse, `real[i] ≈ size·x[i]` and `imaginary[i] ≈ 0`, within
   `1e-5 · size` (the probe measured at most `5.4e-7 · size` at 8192 points).
2. `` `an impulse transforms to the direct DFT` `` — 8 points, `real[3] = 1`: `real[k] ≈ cos(−2π·3k/8)`, `imaginary[k] ≈
   sin(−2π·3k/8)` within `1e-6`.
3. `` `a size that is not a power of two is refused` `` — `assertFailsWith<IllegalArgumentException> { Fft(12) }`.

### Docs

- `tuner/implementation/CLAUDE.md`, the `Tests (desktopTest):` paragraph: add `TunerEngineTest` (sessions, the late
  answer, the refused start, the one reopen, updates, tones over readings), `SampleRingTest` and `FftTest`.
- Root `CLAUDE.md`, the tests bullet: `:tuner:*` (… the tracker and the tones) becomes "… the tracker, the tones, the
  engine's state machine, the sample ring and the FFT".

## Tests

This plan is the tests. Run `./gradlew :tuner:implementation:desktopTest`; every case above passes at b5c8ed3b5.

## Manual check

None; nothing a user sees changes.
