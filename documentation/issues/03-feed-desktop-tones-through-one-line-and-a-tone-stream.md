<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Play the desktop's tones through one long-lived line fed by a `ToneStream`, so a replaced note crossfades instead of overlapping

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** desktop
**Challenged:** amended — ending the playback as soon as the stream is silent left the line's ~100 ms of queued full-level tone still to play, so a tone asked for then opened a second line over it (the overlap this fixes, after a stop instead of a replacement); the thread now feeds a line buffer of silence first, as iOS feeds its tail, and a play during it joins the same stream. A thread ending on a short write or an exception now also clears `playback`, which otherwise kept a dead stream that every later `play` fed in silence while returning true.
**Files:** `tuner/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/tuner/implementation/DesktopToneOutput.kt`,
`tuner/implementation/CLAUDE.md`

## Problem

The scale sheet replaces a tone every quarter second (`TunerController.toggleScale`). On the desktop every `play` stops
the old tone and opens a brand-new `SourceDataLine` (`DesktopToneOutput.kt:28-45` at 396acf923):

```kotlin
override fun play(render: (sampleRate: Int) -> ShortArray, onLost: () -> Unit): Boolean {
    stop()
    val line = try {
        AudioSystem.getSourceDataLine(FORMAT).apply {
            open(FORMAT, SAMPLE_RATE / 10 * 2)
            start()
        }
    ...
```

`stop()` only sets `isStopping`; the old feed thread writes its 10 ms fade into the *next* chunk, behind the ~100 ms
(`SAMPLE_RATE / 10 * 2` bytes = 4,800 frames) of full-level loop already queued in the old line. The new line starts at
once, so for ~100 ms two loops sound together. Each loop peaks at `ToneSynthesizer.PEAK` (0.6 of full scale), so the
system mixer sums them to up to ~1.2 and clips, and the note the circle arrives on is heard smeared into the last one.
iOS already solved this with one engine playing a `ToneStream` (`IosToneOutput.kt`), whose replacement is a crossfade
whose gains always sum to one (`ToneStreamTest`'s `a tone replaced at any moment crossfades into the next`).

## Fix

Rewrite `DesktopToneOutput` on the `IosToneOutput` model, keeping its `ToneOutput` contract:

- State: one `private val lock = Any()` guarding a `private var playback: Playback?`, where
  `private class Playback(val line: SourceDataLine, val stream: ToneStream, var onLost: () -> Unit)`. The
  `ToneStream` is not thread safe; every call on it (`play`, `stop`, `render`, `isSilent`) is made under the lock.
- `play`: render the loop first (`render(SAMPLE_RATE)`, outside the lock; the rate is fixed). Then under the lock, if a
  `playback` exists, `stream.play(loop)`, replace its `onLost`, and return true: the replacement crossfades in the
  same stream on the same line. Otherwise open a line exactly as today (same format and 100 ms buffer, `return false` on
  an exception), build `ToneStream((ToneOutput.FADE_SECONDS * SAMPLE_RATE).toInt())`, `stream.play(loop)`, store the
  `Playback` and start the daemon feed thread ("Tuner tone").
- `stop`: `synchronized(lock) { playback?.stream?.stop() }` — only a fade, as on iOS.
- Feed thread: a `ShortArray(CHUNK_FRAMES)` and its byte buffer, and `tailChunks = line.bufferSize / 2 / CHUNK_FRAMES
  + 1` (the buffer the line actually got, which may be larger than asked for, in chunks). Each round, under the lock:
  if `this.playback !== playback` return; `stream.render(samples, CHUNK_FRAMES)`; count the rounds in a row that
  rendered with `stream.isSilent`, reset by any that did not; once that count reaches `tailChunks`, set
  `this.playback = null` there and then and end. Until then a `play` still finds the playback and goes into the same
  stream, which is what keeps a tone asked for just after a stop from opening a second line over the one still
  playing the end of the last: when `write` returns, the line still holds a buffer of what was written before, so
  only after a buffer of silence has been written is all that is left in it silence. Outside the lock: convert to
  little-endian bytes and `line.write`. When ended, `line.drain()` and `close()` in a `finally`, as today; what a new
  line can overlap with is then only that drained silence.
- A thread that ends any other way — a short write, or an exception — first clears the playback under the lock (if
  `this.playback === playback`, set it to null), so that the next `play` opens a new line rather than feeding a stream
  no thread renders, and then calls the playback's `onLost` (read under the lock) unless the stream had already gone
  silent, as today.
- The old line kept today's 100 ms buffer and this keeps it (the metronome's desktop output queues the same,
  `AudioOutput.QUEUED_SECONDS`, which survives a GC pause). A replacement is then heard one buffer after it is asked
  for, the same for every note after the first; do not shrink the buffer in this change.
- Replace the class KDoc with one that says what it now is: one line fed by a daemon thread with a `ToneStream`, a
  replacement crossfading in the same stream, a stop only fading it, and the thread writing a line buffer of silence
  after it (a tone asked for meanwhile joins the stream) before draining and closing the line.
- Drop the `Playing` class and the hand-written fade loop; leave nothing unused.

`tuner/implementation/CLAUDE.md`, "The platforms", desktop bullet: replace "Tone: `SourceDataLine` writing its own
fades." with "Tone: one `SourceDataLine` fed a `ToneStream` by a daemon thread, a replacement crossfading on the same
line; closed once a line buffer of the stream's silence is written and drained, a tone asked for before that joining
the stream."

## Tests

The crossfade and its sum are `ToneStream`'s, already tested in `ToneStreamTest` (`commonTest`). The output itself
needs a sound device, which CI's desktop runners lack, so no test is added;
`./gradlew :tuner:implementation:desktopTest` compiles `desktopMain` and runs the module's tests.
