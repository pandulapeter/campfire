<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/instrument

One instrument for the whole app.

- `UserPreferences.instrument` plus a tuning per instrument in `UserPreferences.tunings`. Diagrams, tuner, scales, and
  the keyboard capo rule read it. The scales read `InstrumentTuning.strings` directly (every stringed instrument, not
  only the five with shapes), as the tuner, the tab retuning, and the tuning names do.
- This package is the one place joining `:data:model`'s instruments, the tuner's tunings, and `:chordpro`'s shapes;
  every caller goes through it.
- Picked by two `PickerChip`s (`InstrumentChips.kt`; the tuning chip only for an instrument with more than one) opening
  `InstrumentSheet` / `TuningSheet`. They stand on the song details first section (read only included, since they change
  a device preference), the Tuner tab, the Chords and the Scales tabs, and the onboarding; never
  Settings. Other is what the tuner reads every note for; there is no Chromatic switch.
- **The twelve-string is the guitar to everything but the tuner** (`Instrument.isTwelveString`): guitar tunings and
  shapes, its own tuning pick, and the tuner hears each course's second string (`Instrument.courses`). So a tuning names
  no instrument: `withTuning` tunes the picked instrument, and chips ask `isStandard`.

## How a song is played on the keyboard

- On Keyboard the capo is a transposition (`readsCapoAsTransposition`, whatever the Chord diagrams switch says): no capo
  stepper or capo text; the capo's frets are counted into the transposition (`withCapoSounding`), so chords, diagrams,
  chord sheet, and the stepper's key are the sounding key. The stepper still steps and resets only the transposition's
  own override. Capo overrides and the file's `{capo}` are kept for other instruments. The PDF follows the screen.
