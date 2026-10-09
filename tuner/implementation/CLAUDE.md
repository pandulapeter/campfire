<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :tuner:implementation

The engine (`TunerImpl`, `@Single`, which only builds a `TunerEngine` with the real scope, dispatcher and time source
and delegates to it) and one `AudioInput` and one `ToneOutput` per platform source set, found by the module's
`@ComponentScan` (the Android ones take the `@Provided` `Context`). `:app:di` names `TunerModule`; only `:app:di` sees
this module. Nothing here reaches the network, and nothing heard is kept beyond the window being read.

### Hearing a note (`commonMain`, all of it tested)

- `TunerEngine` runs every call on one confined coroutine, as `MetronomeEngine` does, each start a session whose late
  callbacks are ignored. While listening it polls the input's latest window every 33 ms, runs the detector and the
  tracker over it and publishes a state only when it differs. A `MICROPHONE_DISCONNECTED` (a new route: headphones
  plugged in or pulled) opens the input again once before it is reported.
- `PitchDetector` — the McLeod pitch method over about 85 ms of input (4096 frames at 44.1 or 48 kHz, the next power of
  two for another rate): the normalized square difference function, its autocorrelation through two FFTs of the window
  padded to twice its length (`Fft`, a radix-2 one of its own), the first key maximum reaching 0.9 of the highest,
  refined by a parabola. Nothing is reported below −60 dBFS or a clarity of 0.8. The range searched is 30–2100 Hz in
  chromatic mode and from four semitones under a preset's lowest string to an octave over its highest
  (`rangeFor`), which is most of what keeps a bass from being read an octave up. Buffers are allocated once.
- `PitchTracker` — pure, driven by the time it is handed: the 60 ms after an onset (the level doubling) skipped, the
  median of the last five answers, the cents smoothed over about 80 ms, a target that only changes once a new one has
  held for three answers, the last reading held for half a second after the sound falls under twice the noise floor
  (tracked from the windows with no pitch in them: the room, not the strings), in tune once within ±5 cents for
  300 ms, `SILENT` after two seconds of exact zeros.
- `ToneSynthesizer` — about a second of a note, a whole number of periods so that the buffer loops without a seam (the
  frequency moved to the one that fits, never more than 0.02 cents off), the fundamental with three falling harmonics,
  since a phone's speaker cannot play a low string's fundamental.
- `SampleRing` — the latest frames of the three inputs that are handed chunks, one writer and one reader without a
  lock: the writer publishes its count after the samples, and the ring holds several windows.

### The platforms

Every input turns its voice processing off, since gain control and noise suppression remove exactly a held note, and
none ever starts Bluetooth SCO, so a headset never becomes the input. Every tone fades in and out over 10 ms.

- **Android** — `AudioRecord` at 48 kHz from `UNPROCESSED` where the device has it, `VOICE_RECOGNITION` elsewhere, read
  by a thread of its own; refused before anything is opened without `RECORD_AUDIO`; a client silenced by a call
  (`AudioRecordingCallback`, Android 10+) is busy. The tone is a static `AudioTrack` with `setLoopPoints` and a
  `VolumeShaper`, under transient audio focus whose loss ends it.
- **iOS** — `IosTunerSession` is the session both share, active while either is in use: `playAndRecord` in
  `measurement` mode with `defaultToSpeaker` and no Bluetooth option where the record permission is granted, `playback`
  for a tone alone otherwise (touching a recording category without it makes the system ask on its own). The input is
  `AVAudioEngine`'s input node with a 4096-frame tap (its block runs on the engine's queue, not the real-time thread);
  started only when `recordPermission` is granted; an interruption is busy, a configuration change disconnected, a
  media services reset failed. The tone is an `AVAudioPlayerNode` on an engine of its own: one faded copy of the loop,
  then the loop scheduled to repeat.
- **Desktop** — a `TargetDataLine` at 48 kHz read by a daemon thread; no line is no microphone. The JVM cannot ask
  whether it may record and a refusal arrives as silence, so a line that has heard only zeros for three seconds is
  opened again (macOS answers its own prompt after the line is open). The tone is a `SourceDataLine` fed the loop by a
  daemon thread that writes the fades itself.
- **Web** — `getUserMedia` with the processing off into an `AnalyserNode` of the tuner's own `AudioContext`, shared
  with the tone (`WebTunerAudio`, on `window.__campfireTuner`); the rejection's name is the reason, no secure context or
  `mediaDevices` is not supported. A capture-phase listener resumes the context while a tuner screen shows
  (`setStartable`), and until it runs the input reports `WAITING_FOR_GESTURE`. Closing ends the stream's tracks, which
  puts the browser's indicator out, and a generation counter ends the tracks of an answer that arrives after a close.
  Nothing calls back into Kotlin, so an ended track and the context's state are read on every poll. No static file is
  added.

Tests (`desktopTest`): `PitchDetectorTest` (sines and Karplus-Strong strings at every preset's strings, 16, 44.1 and
48 kHz, under noise, a fundamental 20 dB under its second harmonic, silence, noise and a chord), `PitchTrackerTest`,
`ToneSynthesizerTest` (the seam, the level, the tone read back by the detector), and `RecordedStringsTest`, which reads
every 16-bit WAV dropped into `desktopTest/resources/tuner/`, named by the note it holds (see its README), the way the
microphone is read.
