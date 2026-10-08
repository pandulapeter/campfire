# Move the audio outputs' clock arithmetic into a tested commonMain AudioClock and PlaybackGapTracker

**Kind:** testability  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** all (Android, iOS, web outputs; the silent one)
**Files:** `metronome/implementation/src/commonMain/.../SilentAudioOutput.kt` (`start`'s loop, `heardFrame`); `androidMain/.../AudioOutput.android.kt` (`AndroidAudioOutput.heardFrame`, `NANOSECONDS_PER_SECOND`); `iosMain/.../AudioOutput.ios.kt` (`IosAudioOutput.feed`'s gap accounting, `heardFrame`, private class `SilentFrames`); `wasmJsMain/.../AudioOutput.wasmJs.kt` (`nowFrame` in `start`, `heardFrame`); new `commonMain/.../AudioClock.kt`; new `commonTest/.../AudioClockTest.kt`; `metronome/implementation/CLAUDE.md`
**Depends on:** none (if 45 lands first, `SilentAudioOutput`'s `TimeSource` parameter from it is reused here)

## Problem

Each output turns its platform's clock into a frame of the click stream with a line of arithmetic nobody can test,
because it sits in a platform source set (or, for the silent output, behind `TimeSource.Monotonic`):

- `SilentAudioOutput` writes `elapsed µs × rate / 1_000_000` twice:
  ```kotlin
  val now = mark.elapsedNow().inWholeMicroseconds * SAMPLE_RATE / 1_000_000
  …
  override fun heardFrame() = startMark?.elapsedNow()?.inWholeMicroseconds?.let { it * SAMPLE_RATE / 1_000_000 } ?: -1L
  ```
- Android extrapolates the last `AudioTimestamp`:
  `timestamp.framePosition + (System.nanoTime() - timestamp.nanoTime) * sampleRate / NANOSECONDS_PER_SECOND`, and falls
  back to `track.playbackHeadPosition.toLong() and 0xFFFFFFFFL`.
- iOS is the only non-trivial one — the frames the player ran with nothing queued, which its `heardFrame` subtracts:
  ```kotlin
  val expectedTime = scheduledFrames + silentFrames.count
  val now = playerSampleTime(player)
  if (now != null && now > expectedTime) silentFrames.count += now - expectedTime
  …
  return sampleTime - silentFrames.count - (AVAudioSession.sharedInstance().outputLatency * sampleRate).toLong()
  ```
  A mistake there releases every later beat early or late for the rest of the session, and it can only be checked on
  a device by starving the queue.
- The web output: `((contextTime() - startTime) * sampleRate).toLong()` and
  `((contextTime() - contextOutputLatency() - startTime) * sampleRate).toLong()`.

## Fix

1. Add `internal object AudioClock` in commonMain with pure functions whose bodies are the existing expressions,
   character for character in the same integer/floating types (the results must be bit-identical, so no switching a
   `Long` expression to `Double` or the order of `*` and `/`):
   ```kotlin
   fun framesIn(elapsedMicros: Long, sampleRate: Int): Long = elapsedMicros * sampleRate / 1_000_000
   fun extrapolatedFrame(framePosition: Long, framePositionNanos: Long, nowNanos: Long, sampleRate: Int): Long =
       framePosition + (nowNanos - framePositionNanos) * sampleRate / 1_000_000_000L
   fun framesSince(nowSeconds: Double, startSeconds: Double, sampleRate: Int): Long = ((nowSeconds - startSeconds) * sampleRate).toLong()
   fun latencyFrames(latencySeconds: Double, sampleRate: Int): Long = (latencySeconds * sampleRate).toLong()
   ```
   Watch the types: `SAMPLE_RATE` in `SilentAudioOutput` is an `Int` constant multiplied into a `Long`, Android's
   `sampleRate` is an `Int` field — keep `Int` parameters. For the web, `heardFrame` subtracts the latency *before*
   multiplying, so call `framesSince(contextTime() - contextOutputLatency(), startTime, sampleRate)`, not
   `framesSince(…) - latencyFrames(…)` (that would round twice).
2. Add `internal class PlaybackGapTracker` in commonMain replacing iOS's private `SilentFrames`:
   ```kotlin
   internal class PlaybackGapTracker {
       @Volatile var silentFrames = 0L; private set
       /** Called before each buffer is scheduled, with the frames scheduled so far and the player's time, if known. */
       fun onBufferFreed(scheduledFrames: Long, playerFrame: Long?) {
           val expected = scheduledFrames + silentFrames
           if (playerFrame != null && playerFrame > expected) silentFrames += playerFrame - expected
       }
       fun heardFrame(playerFrame: Long, latencyFrames: Long) = playerFrame - silentFrames - latencyFrames
   }
   ```
   (`kotlin.concurrent.Volatile`, as `ChordVoicings` and the iOS output already import.) Keep one tracker per session,
   created in `start`, for the reason `SilentFrames`' KDoc gives — move that KDoc onto the new class.
3. Switch the four outputs to these functions, one commit each or one together; no behaviour change.

## Tests

`AudioClockTest` (commonTest, runs on `desktopTest`):

- `framesIn(1_000_000, 48_000) == 48_000`, `framesIn(20_833, 48_000) == 999` (truncation, as today).
- `extrapolatedFrame` with `nowNanos == framePositionNanos` returns `framePosition`; 0.5 s later at 44 100 Hz adds 22 050.
- `framesSince` truncates toward zero, and a `now` before `start` gives a negative frame (the web output relies on
  `heardFrame` being negative before its start delay elapses, so no beat is released early).
- `PlaybackGapTracker`: no gap while `playerFrame <= scheduledFrames + silentFrames`; a starved queue (`playerFrame`
  1 000 frames past what was scheduled) adds exactly 1 000; a second starvation accumulates; `heardFrame` subtracts
  both the gap and the latency; a `null` player frame changes nothing.

Existing `MetronomeSequencerTest` / `ClickMixerTest` are unaffected. Compile every target:
`./gradlew :metronome:implementation:desktopTest :metronome:implementation:compileDebugKotlinAndroid :metronome:implementation:compileKotlinIosSimulatorArm64 :metronome:implementation:compileKotlinWasmJs`.

## Manual check

Play the click for a minute on an iPhone and on an Android phone with the screen locked and back on, and on the web
build in a background tab and back: the flash stays on the beat, as before. The arithmetic is identical, so this only
guards against a call site wired to the wrong argument.
