<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# chordpro/tabs

- `ChordProTabTransposer` moves frets, not the tuning: a run moves as a whole, **by octaves if a fret would leave the
  neck**, and one that fits no octave is left alone rather than half moved. Only staff lines are fretted.
- `ChordProTabColumns` keeps a system's columns: a column widens or narrows on every line by exactly what its widest
  fret gained or lost, never borrowing a neighbouring dash, so **transposing up and back gives back the same text**
  (the editor writes it), and an author's padding stays.
- `ChordProTabs.select` keeps, of adjacent selector tabs, the reader's, else `guitar`, else the first. `TabPlayer`
  comes from `:presentation`, which knows the instruments.
- **A staff's string names are its tuning**; an unnamed staff is in the standard tuning `TabPlayer.assumedTuning` picks.
  Retuning happens in the same move as the transposition, so one octave decision covers both; another instrument's
  strings line up as one block. A run that cannot be moved is shown as written, transposition only. Every staff line is
  renamed for the reader's string. **Text transposition and notation conversion never retune.**
- `TabNotice` records only judgment calls (octave move, another instrument, failure); a `hidesTabs` instrument
  (keyboard, violin) gets a notice in place of every tab.
- `ChordProTabWrapper` cuts a run into rows (**nothing scrolls sideways**: songs are read with an up/down pedal), every
  line at the same columns, never inside a surrogate pair or combining mark. `wrapLines` marks every piece of a staff
  line as one, since a short piece (`--|`) no longer passes `isStaffLine` by itself.
