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
- Picked by one `InstrumentPill` of two halves split by a divider (the tuning half only for an instrument with more than
  one), opening `InstrumentSheet` / `TuningSheet`. No chevrons, one line (the tuning's name is cut short first, down
  to 40% of the pill, then the instrument's: `tuningHalfMaxWidth`), and everywhere exactly `songControlHeight` of its
  text style, so on a song it matches the transposition stepper. It
  stands on the song details first section (read only included, since it changes a device preference), the Tuner, the
  Chords, and the Scales tabs, and the onboarding; never Settings. Other is what the tuner reads every note for, so
  the Tuner leaves its Strings / Chromatic toggle out for it.
- **The twelve-string is the guitar to everything but the tuner** (`Instrument.isTwelveString`): guitar tunings and
  shapes, its own tuning pick, and the tuner hears each course's second string (`Instrument.courses`). So a tuning names
  no instrument: `withTuning` tunes the picked instrument, and the pill asks `isStandard`.

## How a song is played on the keyboard

- On Keyboard the capo is a transposition (`readsCapoAsTransposition`, whatever the Chord diagrams switch says): no capo
  stepper or capo text; the capo's frets are counted into the transposition (`withCapoSounding`), so chords, diagrams,
  chord sheet, and the stepper's key are the sounding key. The stepper still steps and resets only the transposition's
  own override. Capo overrides and the file's `{capo}` are kept for other instruments. The PDF follows the screen.
