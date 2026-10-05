# Keep the time the iOS player ran with nothing queued out of the heard position

**Challenged:** amended — calling `player.play()` from the feed thread races `stop()` (and a configuration change) and `AVAudioPlayerNode.play()` on a stopped engine raises an Objective-C exception that ends the process; the first buffers are now scheduled in `start`, on the engine's coroutine, before `play()`, the semaphore/index bookkeeping is spelled out, and the silent-frame count is per session so a stale feed thread cannot add to the next session's.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** iOS
**Files:** metronome/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/metronome/implementation/AudioOutput.ios.kt, metronome/implementation/CLAUDE.md

## Problem

`IosAudioOutput.heardFrame()` maps the player's own timeline straight onto the stream's frames:

```kotlin
override fun heardFrame(): Long {
    val player = player ?: return -1L
    val nodeTime = player.lastRenderTime ?: return -1L
    val playerTime = player.playerTimeForNodeTime(nodeTime) ?: return -1L
    return playerTime.sampleTime - (AVAudioSession.sharedInstance().outputLatency * sampleRate).toLong()
}
```

`AVAudioPlayerNode`'s player time starts at 0 when `play()` is called and keeps advancing while the node plays, whether
or not anything is scheduled; a buffer scheduled with no time (`scheduleBuffer(buffer) { … }`, as `feed` does) plays
after the ones before it or, with the queue empty, as soon as possible — i.e. at the current player time, not at the
frame the stream gave it. So every frame the player spends with nothing queued is counted in `playerTime.sampleTime`
but not in the stream, and `heardFrame` runs ahead of what is heard by that much, for the rest of the session. The
engine releases `beats` (the flash, the haptics, the beat row) when `heardFrame` passes them, so they come early.

Two sources:

1. **Start-up:** `start` calls `player.play()` and only then starts the `NSThread` that renders and schedules the first
   buffer, so the thread's start and the first render (a few milliseconds) are counted.
2. **Underruns:** the feeder keeps five 20 ms buffers (100 ms) queued; a stall longer than that (a Kotlin/Native
   collection pause, the thread starved under load with the screen locked) leaves the queue empty, and every such gap
   is added permanently.

The effect is small (milliseconds at start, an underrun's length after one), which is why this is low. Drop this plan if
a device check shows `playerTime.sampleTime` does not advance while nothing is scheduled (log it from `heardFrame`
between `play()` and the first `scheduleBuffer`).

## Fix

1. **Queue the first buffers before the player plays, in `start`.** Do not move `play()` onto the feed thread: `stop()`
   runs on the engine's coroutine at any moment (Play then Stop, a preview tap) and calls `player.stop()` and
   `engine.stop()`, and an `AVAudioEngineConfigurationChangeNotification` stops the engine on its own; a `play()` the
   feed thread makes after either finds the engine not running, which AVFoundation answers with an Objective-C
   exception (`required condition is false: _engine->IsRunning()`) that Kotlin/Native turns into a crash. Instead, in
   `start`, after `createStream`, render and schedule all [BUFFER_COUNT] buffers on the calling thread (move the
   render-convert-schedule body of `feed`'s loop into a small `scheduleNext(...)` helper both use), then call
   `player.play()` where it is today, then start the thread. Scheduling before `play()` is allowed, so player time 0 is
   the stream's frame 0. The semaphore is then created at 0 and **not** signalled up front (the five buffers are already
   in flight; drop the `repeat(BUFFER_COUNT) { dispatch_semaphore_signal(...) }`, keeping the comment's point that it
   must never be created holding a count), and `feed` starts at `next = 0` (5 % 5), its first wait returning when the
   first buffer is consumed. A `stop()` before the thread runs still wakes it: `player.stop()` calls the five completion
   handlers. Rendering 100 ms of clicks on the engine's coroutine costs microseconds, and `start` and `stop` both run
   there, so nothing races. `heardFrame` already returns -1 until the player plays.
2. **Count the silent frames and subtract them** (recommended). Count the silent frames (per session, see below). In `feed`, keep `scheduledFrames` (frames scheduled so far). Before each `scheduleBuffer`, read the player's
   current sample time the same way `heardFrame` does (without the latency); if it is past `scheduledFrames + silentFrames`,
   the queue ran dry and the buffer will start now, so `silentFrames += now - (scheduledFrames + silentFrames)`. Then
   `scheduledFrames += frames`. `heardFrame` returns `playerTime.sampleTime - silentFrames - latency`. Because
   `lastRenderTime` lags by one render cycle, a measured gap can be a few milliseconds short; that is far below what a
   flash shows, and nothing accumulates when nothing ran dry. (With the screen locked iOS may lengthen the I/O buffer,
   so the shortfall per underrun can approach that buffer's length; it is always an underestimate, never a beat
   released late.) The buffers queued in `start` count in `scheduledFrames` (the feed thread starts it at
   `BUFFER_COUNT * frames`) and are not checked, the player not having played yet.

   Keep the count **per session**, not in a plain field reset by `start`: the previous session's feed thread can be
   between its `this.player !== player` check and its write when `stop()` and the next `start()` run, and would add its
   gap to the new session's count. Hold it in a small object created in `start` and handed to `feed` (e.g.
   `private class SilentFrames { @Volatile var count = 0L }`, a `@Volatile private var silentFrames: SilentFrames?`
   next to `player`, set together with it in `start` and cleared in `stop`), and have `heardFrame` read the current
   one; a stale thread then writes only into its own, discarded object.

   Alternative: schedule every buffer at an explicit player time (`scheduleBuffer(buffer, atTime = AVAudioTime(sampleTime
   = start, atRate = sampleRate), options = 0u) { … }`) with `start = max(scheduledEnd, now + margin)` and keep the same
   offset. It needs the same bookkeeping and adds a dependence on how the player treats a time already in the past, so
   the counting above is preferred.

Update the iOS bullet of `metronome/implementation/CLAUDE.md`: the player starts once the first buffers are queued (in `start`, never from the feed thread, since `play()` on a stopped engine throws), and
`heardFrame` leaves out the frames the player ran with nothing queued, since its own time keeps running through them.

## Tests

None: the bookkeeping is a few lines inside AVFoundation glue; extracting it into a pure helper just to test a
subtraction is not worth it.

## Manual check

On an iPhone (or the simulator), Metronome tab at 60 BPM with flash on, play with a metronome app or a recording next to
it and compare flash and click: they should coincide from the first beat. Then stress the device (scroll a long song
list hard, or play while the Xcode memory graph is captured) and check the flash does not drift ahead of the click after
an audible hiccup.
