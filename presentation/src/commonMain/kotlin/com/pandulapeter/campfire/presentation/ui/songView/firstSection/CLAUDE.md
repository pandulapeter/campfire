<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->


# :presentation — ui/songView/firstSection

- Flows through the same rows and columns, never cut. Controls come in groups kept whole where they fit on a line
  (instrument and tuning pill; key and capo; tempo and time signature), sharing a line where they fit; one too wide
  flows its items and the next starts under it (`groupedLines`, tested). A control whose label and stepper are wider
  than the line puts its label above, and only its own line grows; every line is at least a pill tall.
- **Kept short, and every line starts with a pill**: `[transposition] Capo [capo]` and `[tempo | Tap] 3/4
  [metronome]`, no unit (the stepper says "BPM" aloud), 4/4 left out. The transposition has no label and reads the key
  of the page's chords where the song names one (semitones only said aloud), else the reader's semitones. Where that key
  names the shapes above a capo, the capo's label reads "shapes, capo", crossfading as it changes. The keyboard has the
  capo too.
- **One keyline for the pills**: controls start where the section pills reach out to, so the instrument pill, the
  transposition, and the tempo line up; a capo with no transposition before it starts on the lyrics' keyline, being a
  label.
- **Controls scale with the lyrics like the header pills**: `fontScale` grows their content, not their padding, so each
  is exactly `songControlHeight`; scaling a pill whole towered over the page. `MIN_FONT_SCALE` keeps them big enough to
  hit.
- Tool buttons (`playingToolPlaces`, tested): the tuner after the instrument pill where it is shown (while it fades
  too, so it is never shown twice), else after the capo or transposition; the metronome after the time signature (the
  tempo at 4/4); a button whose group is absent goes after the other. With chords and metronome both off there is no
  section and the tuner is an overflow menu entry.
- Whether the section holds either button, and what the instrument pill names (label widths decide whether the tuner
  fits beside it; a leaving tuning half is named until faded), are part of its content, since measured sizes are keyed
  by it.
- Read only line (performance mode, archived setlist): always names capo, tempo, and time, since an absent value would
  read as unknown ("Capo 0", 120, 4/4); the preview names a capo only where the text writes one. Values read as the
  click and steppers read them (tempo clamped to the click's range, `C` as 4/4, capo within the neck).
- In the preview the section is the About the song tiles, under one key whatever it says, so its cover waits for typing
  to pause rather than fetching half-typed addresses.
- Over the read only line the pill reaches out as far as a section's pill, edges lined up (the tuner beside it takes
  that width too); the tuner goes under it whole where it does not fit. It has a line of its own over the About the song
  tiles and over a read only line without tools.
