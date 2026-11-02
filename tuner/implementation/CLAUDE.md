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

`TunerImpl` only builds a `TunerEngine` with the real scope, dispatcher, and time source. Nothing reaches the network;
nothing heard is kept beyond the window being read.

### Hearing a note (`commonMain`, all of it tested)

- `TunerEngine`: one confined coroutine like `MetronomeEngine`, sessions ignoring late callbacks; a start that throws
  is failed and closes the input.
  - A window handed out again (an iOS tap spans ~3 polls) is not re-analysed but its answer is still stepped through
    the tracker, whose timings expect a step every poll.
  - A window whose frame position has not moved for 0.5 s (input silently stalled, suspended web context), or an input
    waiting for a gesture, is heard as nothing — else the tracker holds a repeated clear note forever.
  - While a tone sounds, and a window plus 150 ms after, the tracker is reset and nothing is read.
  - `MICROPHONE_DISCONNECTED` (route change) reopens the input once (until the reopened one has worked 2 s) before
    being reported.
- `PitchDetector` (McLeod pitch method):
  - High-passed at 100 Hz first: laptop/phone mics hear room rumble louder than a low string's fundamental; the period
    survives in the harmonics. The filter runs from rest over half a window more than it reads (`inputSize`, also what
    the web analyser takes), since its start-up ringing pulls a low string by cents.
  - Up to 256 key maxima (a five-string bass's B0 crosses zero more than 64 times a period).
  - Thresholds: −80 dBFS after the filter, clarity 0.65 (a low string across a quiet room passes; a four-sine chord
    doesn't).
  - A preset's range runs from four semitones under its lowest string to an octave over its highest (twelve-string
    octave strings fit) — the main guard against reading a bass an octave up.
- `PitchTracker` (pure, time handed in): skips 60 ms after an onset; median of five; target changes only after a new one
  holds for three answers; an answer an octave off the target is ignored while the sound is more than 6 dB under its
  strike (dying strings read at double period); the reading is held 0.5 s after falling under twice the noise floor;
  `SILENT` is ambiguous (`:tuner:api`), so outside Windows that notice is worded as a hint, not a fault.
- `ToneSynthesizer`: a loop of whole periods with three harmonics, since phone speakers cannot play a low string's
  fundamental.
- `ToneStream`: every start, replacement (a crossfade), and stop a ramp written into the samples; a faded voice is
  dropped only after writing its silence.

### The platforms

Every input turns voice processing off (gain control and noise suppression remove a held note) and none starts
Bluetooth SCO. Every tone fades over 10 ms, sample-accurately, and **nothing audible is ever cut**: an output stops
only once its fade has been played (the scale sheet replaces a tone every quarter second).

- **Android**: `AudioRecord`, `UNPROCESSED` where available else `VOICE_RECOGNITION`; silenced by a call is busy. Tone:
  static `AudioTrack` with loop points and a `VolumeShaper` under transient focus (loss ends it), held while one tone
  replaces another, since giving focus back between notes resumes and pauses another app's music; stopped only once
  the fade-out shaper reads silence (≤ 1 s), since the mixer applies fades late and an early stop pops.
- **iOS**: `IosTunerSession`, shared by input and tone: `playAndRecord` / `measurement` / defaultToSpeaker, no
  Bluetooth option, while the input holds it; a tone alone takes `playback`, louder on the speaker and never a
  recording category without permission (which makes the system prompt); an input joining a tone switches the
  category, which ends the tone. Input starts only when granted; interruption = busy, configuration change =
  disconnected, media services reset = failed, and no tap for 1 s = disconnected (reopened once), since another audio
  user's deactivation stops it with no notice. Tone: a `ToneStream` fed to a player node on its own engine; the
  player's volume is never the fade (applied once per render cycle). A stop fades the stream and the feed thread tears
  the engine down after the silence and the output latency have played, so `IosTunerSession` counts under a lock.
- **Desktop**: the JVM cannot ask for permission and a refusal arrives as silence, so a line hearing only zeros for 3 s
  is reopened (swapped in only if listening has not stopped or restarted meanwhile; each capture thread closes the line
  it ends with; macOS answers its prompt after the line opens). Tone: one `SourceDataLine`, a replacement crossfading
  on it; closed once a line buffer of silence is written and drained, a tone asked for before that joining the stream.
- **Web**: `getUserMedia` (processing off) into an `AnalyserNode` on an `AudioContext` shared with the tone
  (`WebTunerAudio`). A failed graph ends its tracks. The context is resumed by a capture-phase listener while a tuner
  screen shows (`setStartable`); an opening waits up to 0.5 s for a new context to start running, which it does a few
  frames late even where it may, so the line never flashes. Closing ends the tracks (browser indicator off); a
  generation counter ends tracks of an answer arriving after a close. Nothing calls back into Kotlin, so ended tracks
  and context state are read every poll. Every gain change is scheduled a device callback plus two quanta past
  `currentTime` (a ramp at `currentTime` can arrive in the past and step: pops); a stop holds the ramp with
  `cancelAndHoldAtTime` (Firefox: a ramp ending at the computed value, never `gain.value`, a block stale); the context
  is suspended only once the fade and the output latency have played.

`RecordedStringsTest` (`desktopTest`) reads every 16-bit WAV dropped into `desktopTest/resources/tuner/`, named by its
note (see its README).
