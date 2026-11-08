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
  the pill takes the line to itself wherever it does not fit whole beside them (`pillSharesLine`). Always exactly
  `songControlHeight`, matching the transposition stepper, touched 48 dp tall. On the song's first section (read only
  included, since it changes a device preference), Tuner, Chords, Scales, onboarding, and the Preferences' Chords page.
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
