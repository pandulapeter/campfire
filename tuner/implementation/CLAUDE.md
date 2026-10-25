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

`TunerImpl` (`@Single`) only builds a `TunerEngine` with the real scope, dispatcher, and time source. One `AudioInput`
and one `ToneOutput` per platform source set, found by `@ComponentScan` (Android's take the `@Provided` `Context`).
Only `:app:di` sees this module. Nothing reaches the network; nothing heard is kept beyond the window being read.

### Hearing a note (`commonMain`, all of it tested)

- `TunerEngine`: one confined coroutine like `MetronomeEngine`, sessions ignoring late callbacks; a start that throws
  is failed and closes the input. Polls the latest window every 33 ms, publishes only on change.
  - A window handed out again (an iOS tap spans ~3 polls) is not re-analysed but its answer is still stepped through
    the tracker, whose timings expect a step every poll (`LatestEstimate`).
  - A window whose frame position has not moved for 0.5 s (input silently stalled, suspended web context), or an input
    waiting for a gesture, is heard as nothing — else the tracker holds a repeated clear note forever.
  - While a tone sounds, and a window plus 150 ms after, the tracker is reset and nothing is read (the mic hears the
    speaker).
  - `MICROPHONE_DISCONNECTED` (route change) reopens the input once (until the reopened one has worked 2 s) before
    being reported.
- `PitchDetector`: McLeod pitch method over 4096 frames (next power of two at other rates), via FFT autocorrelation
  (`Fft`, its own radix-2).
  - High-passed at 100 Hz first (`HighPassFilter`): laptop/phone mics hear room rumble louder than a low string's
    fundamental; the period survives in the harmonics. The filter runs from rest over half a window more than it reads
    (`inputSize`, also what the web analyser takes), since its start-up ringing pulls a low string by cents.
  - Key maxima: up to 256 (a five-string bass's B0 crosses zero more than 64 times a period), first reaching 0.9 of the
    highest.
  - Thresholds: −80 dBFS after the filter, clarity 0.65 (a low string across a quiet room passes; a four-sine chord
    doesn't).
  - Range: 26–2100 Hz chromatic; for a preset, four semitones under its lowest string to an octave over its highest
    (`rangeFor` over `TunerConfig.strings`, so twelve-string octave strings fit) — the main guard against reading a
    bass an octave up.
- `PitchTracker` (pure, time handed in): skips 60 ms after an onset (level doubling); median of five; target changes
  only after a new one holds for three answers; an answer an octave off the target is ignored while the sound is more
  than 6 dB under its strike (dying strings read at double period); the reading is held 0.5 s after falling under twice
  the noise floor (tracked from pitchless windows); in tune at ±5 cents for 300 ms; `SILENT` after 2 s under −86 dBFS
  (`SILENT_LEVEL`; why that is ambiguous is `:tuner:api`'s), so outside Windows that notice is worded as a hint, not a
  fault.
- `ToneSynthesizer`: a loop of whole periods (frequency adjusted ≤ 0.02 cents) with three harmonics, since phone
  speakers cannot play a low string's fundamental.
- `ToneStream` (tested): the tones as samples for an output that fills its own buffers, every start, replacement
  (a crossfade), and stop a ramp written into them; a faded voice is dropped only after writing its silence.
- `SampleRing`: lock-free single writer/reader; the writer publishes its count after the samples.

### The platforms

Every input turns voice processing off (gain control and noise suppression remove a held note) and none starts
Bluetooth SCO. Every tone fades over 10 ms, sample-accurately, and **nothing audible is ever cut**: an output stops
only once its fade has been played (the scale sheet replaces a tone every quarter second).

- **Android**: `AudioRecord` at 48 kHz, `UNPROCESSED` where available else `VOICE_RECOGNITION`; refused before opening
  without `RECORD_AUDIO`; silenced by a call (`AudioRecordingCallback`, Android 10+) is busy. Tone: static `AudioTrack`
  with loop points and a `VolumeShaper` under transient focus (loss ends it), held while one tone replaces another (a
  scale), since giving it back between notes resumes and pauses another app's music; stopped only once the fade-out
  shaper reads silence (≤ 1 s), since the mixer applies fades late and an early stop pops.
- **iOS**: `IosTunerSession`, shared by input and tone: `playAndRecord` / `measurement` / `defaultToSpeaker`, no
  Bluetooth option, while the input holds it (a tone joining keeps it); a tone alone takes `playback`, louder on the
  speaker and never a recording category without permission (which makes the system prompt); an input joining a tone
  switches the category, which ends the tone. Input is a 4096-frame tap on the input node, started only when
  granted; interruption = busy, configuration change = disconnected, media services reset = failed, and no tap for 1 s
  = disconnected (reopened once), since another audio user's deactivation stops it with no notice. Tone: a
  `ToneStream` fed to a player node on its own engine (20 ms buffers, three in flight, as the metronome); the player's
  volume is never the fade (applied once per render cycle). A replacement crossfades on the same engine; a stop fades
  the stream and the feed thread tears the engine down after the silence and the output latency have played.
  `IosTunerSession` counts under a lock, since that release comes from the feed thread.
- **Desktop**: `TargetDataLine` at 48 kHz on a daemon thread. The JVM cannot ask for permission and a refusal arrives
  as silence, so a line hearing only zeros for 3 s is reopened (swapped in only if listening has not stopped or
  restarted meanwhile; each capture thread closes the line it ends with; macOS answers its prompt after the line
  opens). Tone: one `SourceDataLine` fed a `ToneStream` by a daemon thread, a replacement crossfading on the same
  line; closed once a line buffer of the stream's silence is written and drained, a tone asked for before that joining
  the stream.
- **Web**: `getUserMedia` (processing off) into an `AnalyserNode` on the tuner's `AudioContext`, shared with the tone
  (`WebTunerAudio`, `window.__campfireTuner`). Stream kept only once connected; a failed graph ends its tracks.
  Capture-phase listener resumes the context while a tuner screen shows (`setStartable`), `WAITING_FOR_GESTURE` until
  then; an opening waits up to 0.5 s for a new context to start running, which it does a few frames late even where
  it may, so the line never flashes. Closing ends the tracks (browser indicator off); a generation counter ends tracks
  of an answer arriving after a close. Nothing calls back into Kotlin, so ended tracks and context state are read every
  poll. Every gain change is scheduled a device callback plus two quanta past `currentTime` (a ramp at `currentTime`
  can arrive in the past and step: pops); a replacement crossfades at that one moment; a stop holds the ramp with
  `cancelAndHoldAtTime` (Firefox: a ramp ending at the computed value, never `gain.value`, a block stale); the context
  is suspended only once the fade and the output latency have played.

`RecordedStringsTest` (`desktopTest`) reads every 16-bit WAV dropped into `desktopTest/resources/tuner/`, named by its
note (see its README).
