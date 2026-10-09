<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :tuner:api

The contract of the tuner, and nothing else: depends on nothing but coroutines (for `StateFlow`), like `:metronome:api`,
so that no data or domain type leaks into it and the engine reads no settings of its own. It builds in explicit API mode
(`explicitApi()`).

- `Tuner` — the one stateful interface: `state` (`TunerState`: `listening` and the `tone` sounding), `listen` (opens the
  microphone and reads against a `TunerConfig`; listening already is `update`), `update`, `stopListening`, `playTone`
  (a MIDI note at a reference pitch, until `stopTone`, replacing one that sounds) and `setStartable` (the web's gesture
  rule, as on `Metronome`). Every call returns at once and never throws. **It never asks for a permission**: `listen`
  opens the input only where the platform already allows it and ends in `Stopped(PERMISSION_DENIED)` where it does not,
  except on the desktop and the web, where opening the input is the only question there is. A reading is never
  published while a tone sounds, or for a moment after it stops, since the speaker is what the microphone would hear.
- `model/` — `TunerConfig` (the reference pitch and an `InstrumentTuning`, null being chromatic), `TunerListening`
  (`Stopped(reason?)`, `Starting`, `Hearing(reading?, issue?)`), `TunerReading` (the target note as a MIDI number, the
  cents off it — beyond ±50 where a preset's nearest string is further than half a semitone — the frequency heard and
  `isInTune`), `TunerStopReason`, `TunerInputIssue` (`SILENT`: digital silence for two seconds, which is how the
  desktop operating systems answer an app that may not record; `WAITING_FOR_GESTURE`), `NoteOffset`, and
  `InstrumentTuning` — the presets (guitar, drop D, bass, ukulele, violin, mandolin, banjo), each string a MIDI note in
  the order the tuning is written (`gCEA`), with the stable `id` a stored setting names one by (`chromatic` for none).
- `Pitch` — equal temperament from A4: `frequencyOf`, `centsBetween`, `noteOf` (the nearest semitone),
  `nearestString` and `targetOf`, and `REFERENCE_PITCH_RANGE` (415–466 Hz).

Tests (`desktopTest`): `PitchTest`, `InstrumentTuningTest`.
