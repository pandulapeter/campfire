<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :metronome:implementation

The engine (`MetronomeImpl`, `@Single`) and one `AudioOutput` per platform source set, found by the module's
`@ComponentScan` like any platform definition (the Android one takes the `@Provided` `Context`). `:app:di` names
`MetronomeModule`; only `:app:di` sees this module.

**Every click is placed by its position in the output's sample stream, never by a timer.** `MetronomeSequencer` (pure,
the most tested class here) answers which ticks fall before a frame: each tick's frame is the frame its timing
started at plus `ticks × sampleRate × 60 / (bpm × clicksPerBeat)` in one integer division, so it is never more than a
frame off however long it runs, where adding rounded intervals drifts. A pattern change replaces the pattern of every
tick not yet handed out — what sounds from the next tick, what places them from the next beat, so a subdivided beat is
never cut in two lengths. `ClickSynthesizer` computes every sound's three voices (accent, normal, subdivision) once per
sample rate — decaying sines, partial pairs, first-differenced noise from a seeded `Random`, the same generator on
every platform — with ramps at both ends and peaks below full scale; its golden test compares checksums of the 16-bit
samples, never the floats, which may differ by an ulp between the JVM's interpreter and JIT. `ClickMixer` mixes clicks
into 16-bit PCM a chunk at a time, carrying a click across chunk boundaries (tested to be the same however it is
chunked), reusing every buffer. `ClickStream` is one session: the sequencer, the mixer, and channels (whose
non-suspending ends are safe from any thread) for the changes going in and the rendered ticks coming out. The volume is
squared on its way to a gain.

`MetronomeImpl` runs every call on one confined coroutine (`limitedParallelism(1)`), so calls from the UI, platform
callbacks and media buttons apply in order without a lock, and each start is a session whose late listener callbacks
are ignored. **`beats` are released when heard**: a coroutine polls the output's `heardFrame()` (the reported playback
position, the route's latency included where the platform reports it) every 5 ms and emits the queued ticks it has
passed. An output that cannot open (`AudioOutputStart.Unavailable`: no device, a refused `AudioContext`) is swapped for
`SilentAudioOutput`, the same stream clocked by `TimeSource.Monotonic`, so the UI has one source of beats either way and
`playback` says `audioIssue = UNAVAILABLE`. A refusal (`Refused`: the audio taken by a call) ends in `Stopped(reason)`. A
preview while stopped opens the output for one and a half seconds after the last tap, a preview's focus (Android's
transient duck, iOS's ambient category) rather than playback's.

The outputs (~20 ms chunks, ~100 ms queued — latency is only heard at start and stop, and the depth is the slack a
locked phone, a busy desktop or a Kotlin/Native collection needs; a stop flushes rather than plays out):

- **Android** — `AudioTrack` streaming at the device's native rate, default performance mode (not low latency, which
  buys nothing audible and costs underruns with the screen off), fed from a thread at `THREAD_PRIORITY_URGENT_AUDIO`
  that also releases the track. It owns the `AudioFocusRequest` (a refusal is `Refused`, any loss stops) and the
  `ACTION_AUDIO_BECOMING_NOISY` receiver, so a click stops for a call or pulled headphones whether or not the service is
  up. `heardFrame` from `getTimestamp`, else the playback head.
- **iOS** — `AVAudioEngine` with an `AVAudioPlayerNode` fed five buffers in flight from a Kotlin `NSThread`, the
  completion handler only signalling a semaphore (never an `AVAudioSourceNode`, which would run Kotlin/Native on the
  real-time thread). The player starts once the first five buffers are queued, in `start` and never from the feed
  thread, since `play()` on a stopped engine throws; `heardFrame` leaves out the frames the player ran with nothing
  queued (counted per session by the feed thread), since its own time keeps running through them. It owns the session (playback category, so the silent switch does not mute it; activated on start,
  deactivated with `notifyOthersOnDeactivation`) and the interruption, route change (`OldDeviceUnavailable`), engine
  configuration change and media services reset observers, each of which stops the click with its reason.
- **Desktop** — a `SourceDataLine` (16-bit mono, 48 kHz, its buffer the queue) fed from a daemon thread at max priority;
  `LineUnavailableException` and friends are `Unavailable`. No focus, no media keys.
- **Web** — the two clocks: clicks within the next 100 ms scheduled with `AudioBufferSourceNode.start(time)` on the
  `AudioContext`'s clock, woken every 25 ms by `metronome-timer.js` in `:app:web` (a worker's timer is not throttled
  in a hidden tab), each wake-up a promise awaited in a loop since a Kotlin lambda cannot be handed to a `js(...)`
  block. A capture-phase listener resumes (creating on the first) the context on every press and key, which is before
  Compose sees the tap; it is installed only while the UI reports a screen that can start a click
  (`Metronome.setStartable`), so the rest of the app never opens the audio device; until the context runs,
  `audioIssue = WAITING_FOR_GESTURE`. A start always resumes the context, whatever its state reads, since a suspend from the
  previous session's stop may still be in flight. Voices are copied into `AudioBuffer`s a sample at a time, once each.
  An idle context is suspended again three seconds after the last gesture.

Tests (`desktopTest`): `MetronomeSequencerTest`, `ClickSynthesizerTest`, `ClickMixerTest`; `TapTempoTest` and
`TimeSignatureTest` in `:metronome:api`.
