<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->


# :presentation — ui/dialogs/scales

- **The scale sheet is the one way a scale is shown, and it depends on no screen**: `DialogType.Scale` carries only a
  scale id and a root name (standard notation, spelled as its opener spells it); instrument, tuning, notation, and
  labels are read when it composes. An unknown id closes it.
- **Opened from a capoed song** (`DialogType.Scale.capo`) the scale is the sounding one on the real neck: frets
  below the capo veiled, the notes the capo stops drawn as rings on a muted bar at its fret (as open notes at the
  nut), the chord rows fingered above the capo; the Scales tab passes the tabs' own capo. The bar crosses only the
  strings from the nut (`nutRows`): the banjo's drone is spiked the capo's frets above its fifth fret, as the chord
  rows play it.
- Dots carry degrees or note names (device-local, one header toggle); the keyboard carries none and has no toggle. The
  title wraps to three lines, so a long name is never cut, even at a large text size.
- Chord rows (seven-note scales only) follow the Chords tab's switch and need an instrument with shapes; shapes come
  from the tables only, never a search during composition. A chord opens its sheet over this one without scales; opened
  from a chord sheet, this one has no chord rows, so dialogs never stack deeper than two.
- The neck gives the dots the room: rows about a fret tall, a narrow open column; inlays stay faint and small.
- Play (balanced by an empty space so the formula stays centered) plays the scale once up, root to octave over middle
  C, through the tuner's reference tones at its reference pitch, the tuner switched on or not. It stops the metronome
  (iOS shares the audio session), stops when the sheet closes or is covered and on ON_STOP, and the web's gesture rule
  counts the sheet as a tone screen.
- The degree sounding gets a circle in the formula, sized for the widest degree, sliding degree to degree (back to the
  first for the octave), growing in at the start and fading out at the end; the neck is left alone. The circle moves
  first and each note sounds as it arrives (`SCALE_NOTE_LEAD_MILLIS`), so the circle seems to play it.
