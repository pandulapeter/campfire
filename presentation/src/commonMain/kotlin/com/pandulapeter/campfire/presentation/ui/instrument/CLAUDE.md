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

One instrument for the whole app, with a tuning per instrument. This package is the one place joining `:data:model`'s
instruments, the tuner's tunings, and `:libs:chordpro`'s shapes; every caller goes through it. The scales read the
tuning's strings directly (every stringed instrument, not only the five with shapes).

- `InstrumentPill`: two halves (the tuning half only for an instrument with more than one), no chevrons, one line: the
  tuning's name is cut first, down to 40% of the pill, then the instrument's; on a tool's line with trailing controls
  the pill takes the line to itself wherever it does not fit whole beside them (`pillSharesLine`). Exactly
  `songControlHeight`, matching the transposition stepper, touched 48 dp tall, except on the Preferences' Chords page,
  where it takes the picker chips' height (`PICKER_CHIP_HEIGHT`) among them, and in song details' dock, where it takes
  the dock's controls' height, outline, and corners, stacked (`isStacked`: the tuning under the instrument, a line
  between them) beside the diagrams, and ends in the tuner (`trailing`, in the same container,
  `InstrumentPillOverTrailing`: the halves fill the whole pill, their presses and ripples running under the tuner to
  its end, the names kept clear of it, the tuner standing over them and taking its own presses, read after them). A
  pill stretched wider than its names gives the rest to its halves, never empty room. Its names shrink to
  `minFontSize` (the dock's 11 sp) before they are cut short. In song details' dock, Tuner, Chords, Scales, onboarding,
  and the Preferences' Chords page; never in song details' Playing sheet or tuner sheet.
  A swap between two tools carries the pill (and between Chords and Scales the capo) over as one (`ui/chrome`).
- The Chords and Scales tabs end the line with the Preferences button (`endAction`), outside the line's own layout, so
  the pill and the capo share what it leaves and the button stays on the pill's line.
- Other is what the tuner reads every note for, so the Tuner leaves its Strings / Chromatic toggle out for it.
- **The twelve-string is the guitar to everything but the tuner**, which hears each course's second string. So a
  tuning names no instrument: `withTuning` tunes the picked instrument, and the pill asks `isStandard`.

### The tools' capo

- `UserPreferences.toolCapo`: one capo for the Chords and Scales tabs, device-local like the instrument, never touching
  a song's. Same meaning as a song's: names as they sound, shapes fingered above it, necks veiled below it.
- Placed right after the pill (the Tuner's reference pitch stays at the line's end), sliding to the line's start under
  it where the pill does not fit whole beside it (a 360 dp phone in most languages). The line always measures one
  trailing child, so a hidden capo keeps its height (room above the pill, none under). Hidden (faded and folded) where
  the tab draws nothing it changes (`chordLibraryCapo`, `scaleLibraryCapo`, tested); the value is kept.

### The keyboard

- Takes no capo (`playsWithoutCapo`): its chords are always named as they sound (the Chord names row is disabled
  for it), its diagrams the sounding chords, the effective capo counted in. It still shows the capo stepper and line, a
  capo raising the band's pitch, except where the reader keeps the key, when a capo changes nothing for it. The PDF
  follows the screen.
