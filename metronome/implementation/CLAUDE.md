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

`MetronomeImpl` (`@Single`) only builds a `MetronomeEngine` with the real scope and dispatcher, so the state machine is
testable on virtual time. One `AudioOutput` per platform source set, found by `@ComponentScan` (Android's takes the
`@Provided` `Context`). Only `:app:di` sees this module.

## Timing

- **Every click is placed by sample position, never a timer.** `MetronomeSequencer` computes each tick's frame as the
  timing's start frame plus `ticks × sampleRate × 60 / (bpm × clicksPerBeat)` in one integer division — never more
  than a frame off; summing rounded intervals drifts.
- A pattern change applies from the next tick (sound) and next beat (placement), so a subdivided beat is never split
  into two lengths; a `fromNextBar` change waits for the bar to end and starts at beat one of bar zero, and later
  changes queue behind it. Each such change has an id carried by its ticks; `Playing.pendingPattern` until the first
  is heard, and `Playing.pattern` is always what reaches the ear (the engine keeps every unheard change).
- `ClickSynthesizer` builds the voices once per sample rate, with a seeded `Random` (same on every platform); its golden
  test checksums the 16-bit samples, never floats (they differ by an ulp between JVM interpreter and JIT).
  `ClickMixer` must give the same result however the stream is chunked. Volume is squared into a gain.
- `AudioClock` (platform clock → `heardFrame`) and `PlaybackGapTracker` are common and tested; platform outputs only
  call them.

## Engine

- Every call runs on one confined coroutine (`limitedParallelism(1)`), so UI, platform callbacks, and media buttons
  apply in order without a lock; each start is a session whose late callbacks are ignored.
- **`beats` are released when heard**: polled from `heardFrame()` (playback position including reported latency) every
  5 ms only within 50 ms of the next tick (or before a position or the first tick), otherwise once a chunk. Only
  Android's position moves continuously, so a wait computed to the frame could end just short.
- `AudioOutputStart.Unavailable` (no device, refused `AudioContext`) swaps in `SilentAudioOutput` clocked by
  `TimeSource.Monotonic` (`audioIssue = UNAVAILABLE`), so the UI has one beat source. `Refused` (audio taken by a call)
  ends in `Stopped(reason)`.
- A preview sounds only while stopped (a playing click already changes sound on its next tick; a preview mixed in
  would be a doubled beat), holding the output open 1.5 s after the last tap, with preview focus (Android transient
  duck, iOS ambient), not playback's.

## Outputs

~20 ms chunks, ~100 ms queued (the slack a locked phone, a busy desktop, or a Kotlin/Native GC needs; latency is only
heard at start and stop); a stop flushes. The durations and the 48 kHz default live on `AudioOutput`'s companion.

- **Android**: `AudioTrack` at the native rate, default performance mode (low latency buys nothing audible and
  underruns with the screen off), fed from a `THREAD_PRIORITY_URGENT_AUDIO` thread. Owns audio focus (refusal =
  `Refused`, any loss stops) and `ACTION_AUDIO_BECOMING_NOISY`, so calls and pulled headphones stop it with or without
  the service. `heardFrame` from `getTimestamp`, extrapolated when one is missed — never the playback head, which runs
  ahead by the whole latency; only a track with no timestamp for 1.5 s is clocked by its head
  (`AudioClock.heardFrameBeforeTimestamp`).
- **iOS**: `AVAudioEngine` + `AVAudioPlayerNode`, five buffers in flight from a Kotlin `NSThread`; the completion
  handler only signals a semaphore. Never an `AVAudioSourceNode` (Kotlin/Native on the real-time thread). The player
  starts once five buffers are queued, in `start`, never from the feed thread (`play()` on a stopped engine throws).
  `heardFrame` excludes frames played with nothing queued. Owns the session (playback category, so the silent switch
  does not mute; deactivated with `notifyOthersOnDeactivation`) and stops on interruption, `OldDeviceUnavailable`,
  engine configuration change, or media services reset.
- **Desktop**: `SourceDataLine` (16-bit mono, 48 kHz) from a max-priority daemon thread. No focus, no media keys.
- **Web**: clicks within the next 100 ms scheduled on the `AudioContext` clock, woken every 25 ms by
  `metronome-timer.js` in `:app:web` (worker timers are not throttled in hidden tabs); each wake-up is a promise
  awaited in a loop since a Kotlin lambda cannot be passed to `js(...)`. A capture-phase listener resumes (or creates)
  the context on every press and key, before Compose sees it, installed only while `Metronome.setStartable` says a
  screen can start a click, so the rest of the app never opens audio; until then `WAITING_FOR_GESTURE`. A start always
  resumes, since the previous stop's suspend may be in flight. An idle context is suspended 3 s after the last gesture.

## The click as the app plays it

- **Playback is media**: Android `mediaPlayback` foreground service with media session and notification (`app/android`);
  iOS `audio` background mode, Now Playing, and remote commands (`app/ios`); web, a best-effort media session
  (`:presentation`'s `wasmJsMain`); desktop nothing. A click that stopped on its own says why.
- **A click outlives the app going to the background, not the app being left**: locked screen or another app in front
  keeps it (a phone on a music stand); swiped away, backed out of (Android), or quit (desktop) stops it.
