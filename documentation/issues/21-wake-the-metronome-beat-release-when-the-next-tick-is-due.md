# Wake the metronome's beat release when the next tick is due instead of every 5 ms

**Challenged:** amended — a wait computed straight to the tick's frame is up to a step late on stepped clocks (iOS `lastRenderTime`, desktop `longFramePosition`, web quanta); now chunk waits until 50 ms before the next tick, then today's 5 ms polls, and close polling until the session's first release (the silent output hears frame 0 at once); added a stepped-clock test, call bound 800.

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all (Android and iOS above all: locked-screen playback)
**Files:** `metronome/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/metronome/implementation/MetronomeEngine.kt`,
`metronome/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/metronome/implementation/MetronomeEngineTest.kt`,
`metronome/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/metronome/implementation/FakeAudioOutput.kt`,
`metronome/implementation/CLAUDE.md`

## Problem

While a click plays, `MetronomeEngine.releaseHeardBeats` (`MetronomeEngine.kt:182-203` at ab97d3105) wakes every 5 ms
whatever the tempo:

```kotlin
private fun releaseHeardBeats() = scope.launch(confined) {
    ...
    while (isActive) {
        while (true) pending.addLast(stream.renderedTicks.tryReceive().getOrNull() ?: break)
        val heardFrame = output.heardFrame()
        while (pending.isNotEmpty() && pending.first().frame <= heardFrame) {
            ...
            _beats.tryEmit(MetronomeBeat(tick.beatIndex, tick.barIndex, tick.level, tick.isSubdivision))
        }
        delay(RELEASE_INTERVAL_MILLIS)
    }
}
...
const val RELEASE_INTERVAL_MILLIS = 5L
```

That is 200 wake-ups a second for a click that, at 120 bpm with no subdivision, has two beats a second to release. Each
wake-up calls `heardFrame()`, which is not free: on Android a JNI `AudioTrack.getTimestamp` plus `AudioClock`'s
extrapolation; on iOS `player.lastRenderTime`, `playerTimeForNodeTime` (two `AVAudioTime` objects allocated through
Kotlin/Native interop) and `AVAudioSession.outputLatency`; on the web a call into the `AudioContext` on the page's one
thread. A metronome is used for a whole song or a practice session, often on a locked phone on a music stand, where
these wake-ups keep a CPU core out of its idle states for the whole time (`Dispatchers.Default`). The feed thread
already wakes only once per 20 ms chunk; the release loop is the busiest part of playback.

## Fix

Wake once a chunk while the next tick is far, and every 5 ms, as today, once it is near:

```kotlin
val remainingMillis = pending.firstOrNull()?.let { (it.frame - heardFrame) * 1_000 / stream.sampleRate }
val wait = when {
    // Nothing heard yet (Android before its first timestamp answers -1), or nothing released yet this session (the
    // silent output hears frame 0 the moment it starts): poll closely, as today.
    heardFrame < 0 || !hasReleased -> RELEASE_INTERVAL_MILLIS
    // A tick is rendered about a queue's depth (~100 ms, AudioOutput.QUEUED_SECONDS) before it is heard, so one chunk's
    // wait never misses one arriving.
    remainingMillis == null -> MAX_RELEASE_WAIT_MILLIS
    // Near the next tick, poll as today: a position that moves in steps is only seen to pass the tick at a step.
    remainingMillis <= NEAR_RELEASE_MILLIS -> RELEASE_INTERVAL_MILLIS
    else -> (remainingMillis - NEAR_RELEASE_MILLIS).coerceIn(1L, MAX_RELEASE_WAIT_MILLIS)
}
delay(wait)
```

`hasReleased` is a local of `releaseHeardBeats`, set when the session's first tick is released.
`MAX_RELEASE_WAIT_MILLIS = (AudioOutput.CHUNK_SECONDS * 1000).toLong()` (20 ms) and `NEAR_RELEASE_MILLIS = 50L` are
declared beside `RELEASE_INTERVAL_MILLIS`. The wait is worked out after the release loop, so
`pending.first().frame > heardFrame` and the difference is positive and small (no overflow even where a test sets
`heardFrame = Long.MAX_VALUE`, which releases everything and leaves `pending` empty).

**Why not sleep until the tick's computed frame** (this plan's first version): only Android's `heardFrame` moves
continuously (an extrapolated timestamp). iOS reads `player.lastRenderTime`, which moves once per render cycle (about
1,024 frames, ~21 ms, with the screen on; up to 4,096 with it locked, since the session sets no preferred I/O buffer
duration); the desktop's `SourceDataLine.longFramePosition` moves by the device's period; the web's `currentTime` by a
render quantum or the platform's callback size. A wait computed from a stale stepped reading can end just before the
step that crosses the tick, and the next computed wait is then up to a whole step (capped at 20 ms) long, so the flash
and the haptics would land up to ~20 ms after the click where today they land within 5 ms of the step. Polling every
5 ms inside a 50 ms window (more than one unlocked iOS render cycle or a desktop period) keeps today's lateness exactly,
at most 5 ms after the reading passes the tick, wherever anyone can see the flash. With the iOS screen locked a step
can be longer than the window and a beat may be released a few tens of milliseconds late, which nothing on a locked
screen shows.

Pattern changes are not delayed: a change made `fromNextBar` is reported through `pendingBarChanges` only when its
first tick is heard, and that tick is released by the same loop (ticks are rendered in frame order, so the first
pending tick is always the next one heard; a tempo or pattern change only replaces ticks not rendered yet). A change
from the next beat updates `_playback` immediately in `updatePlaying` and never waited on this loop. Stop, a lost
output, and a new start cancel `releaseJob` as today, which cancels the `delay` at once. A position that stalls (an iOS
gap, a suspended web context) only keeps the loop in its near polls or its chunk waits; one that moves backward (a gap
counted late) only makes a wake early.

At 120 bpm with no subdivision the loop wakes about 65 times a second (once a chunk until 50 ms before the tick, then
ten 5 ms polls) instead of 200; at 60 bpm about 58; at a fast subdivided tempo whose ticks are under 50 ms apart it
falls back to today's 5 ms polling, never more.

Update `metronome/implementation/CLAUDE.md`, which says "a coroutine polls the output's `heardFrame()` ... every 5 ms
and emits the queued ticks it has passed", to say it polls every 5 ms only within 50 ms of the next tick (and before
the output reports a position or the session's first tick is released) and once a chunk otherwise, since only the
Android position moves continuously.

## Tests

In `MetronomeEngineTest`:

- Give `FakeAudioOutput` a `heardFrameCalls` counter and an optional `clock: (() -> Long)?` that, when set, answers
  `heardFrame()` instead of the field (the existing tests keep setting the field).
- New test, `beats are released when heard without polling every few milliseconds`: start the engine on the fake, set
  `output.clock = { testScheduler.currentTime * FakeAudioOutput.SAMPLE_RATE / 1_000 }`, and from a coroutine in
  `backgroundScope` call `output.stream!!.schedule(nowFrame = now, untilFrame = now + AudioOutput.queuedFrames(SAMPLE_RATE)) { _, _, _, _ -> }`
  every 20 virtual ms, as `SilentAudioOutput` does. Collect the beats with `testScheduler.currentTime` at emission.
  Advance 10 virtual seconds at 120 bpm. Assert that every beat whose tick's frame falls within the ten seconds was
  emitted, each at a virtual time between its frame's time (`frame * 1_000 / SAMPLE_RATE`) and 5 ms after it, and that
  `heardFrameCalls` is under 800 (today's loop makes about 2,000; the new one about 660).
- New test, `a position that moves in steps releases a beat within a poll of the step that passes it`: the same, with
  the clock quantized to 1,024-frame steps (`now * SAMPLE_RATE / 1_000 / 1_024 * 1_024`, which never lands on a 120 bpm
  beat's frame). Assert that each beat is emitted no earlier than the first virtual millisecond at which the stepped
  clock reaches its frame and no more than 5 ms after it. This is the test that fails for a wait computed straight to
  the tick's frame.
- The existing tests that set `heardFrame` by hand and advance `RELEASE_STEP_MILLIS` (6 ms) expect the next poll within
  that step; with chunk waits the next poll can be up to 20 ms away. Raise `RELEASE_STEP_MILLIS` to 21 ms (one chunk
  and a millisecond) and keep every assertion as it is; none of them depend on the step being short, only on it
  covering one wake, and every wait is capped at 20 ms, so a hand-set jump is always seen within it.

## Manual check

On an Android phone, start a click at 100 bpm from the Metronome tab, lock the screen, and leave it for ten minutes
(`adb shell top -H -p $(pidof com.pandulapeter.campfire.debug)`, or Android Studio's CPU profiler with the screen off):
the `DefaultDispatch` threads' CPU time and wake-ups drop noticeably against the build before, while the click still
sounds evenly. Unlock and watch the beat flash against the sound at 60, 120, and 200 bpm with subdivisions: the flash
and the haptics still land on the clicks, with no audible-versus-visible lag, and a time signature change made "from the
next bar" still switches the shown pattern on the bar's first click. On iOS, do the same with Instruments' Time
Profiler or the Energy gauge in Xcode.
