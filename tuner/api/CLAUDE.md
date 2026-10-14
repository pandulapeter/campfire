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

The contract of the tuner, and nothing else: depends on nothing but coroutines, like `:metronome:api`, so no data or
domain type leaks in and the engine reads no settings of its own. Explicit API mode.

- `Tuner`: every call returns at once and never throws. **It never asks for a permission**: `listen` opens the input
  only where the platform already allows it and otherwise ends in `Stopped(PERMISSION_DENIED)`, except on the desktop
  and the web, where opening the input is the only question there is. `setStartable` is the web's gesture rule, as on
  `Metronome`.
- **No reading is published while a tone sounds, or briefly after**, since the microphone would hear the speaker.
- `StringCourses`: `strings` includes each course's partner string, since a twelve-string's octave string plucked alone
  would otherwise read as the wrong string far out of tune. A twelve-string guitar has no presets of its own; it uses
  the six-string's, with `TWELVE_STRING.partnersOf` adding the other string of each course.
- `TunerReading.cents` may exceed ±50 when a preset's nearest string is further than half a semitone.
- `TunerInputIssue.SILENT` (digital silence for two seconds) is how desktop OSes answer an app that may not record, but
  also what Windows' audio enhancements leave of a held note and how a quiet interface idles, so it is ambiguous.
- `InstrumentTuning` presets list strings as MIDI notes in written order (`gCEA`). **Their `id`s are stored settings,
  and `:chordpro`'s `ChordTuning` and `:data:model`'s `Instrument` reuse them** (`chromatic` for none). Screen grouping
  is `:presentation`'s.
