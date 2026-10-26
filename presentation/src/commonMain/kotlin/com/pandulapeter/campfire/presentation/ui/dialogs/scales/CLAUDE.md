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

- **The scale sheet is the one way a scale is shown, and it depends on no screen**: `DialogType.Scale` carries a scale
  id and a root name (standard notation, spelled as its opener spells it) only; instrument, tuning, notation, and labels
  are read when it composes, so it opens over the Scales tab, either chord sheet, or anything else. An unknown id closes
  it.
- **Opened from a capoed song** (its Scales section or its chord sheet, `DialogType.Scale.capo`) the scale is the
  sounding one on the real neck, the frets below the capo veiled (`ScaleDiagramGeometry.Fretboard.capo`, sliding as it
  changes), the notes the capo stops drawn as rings on a muted bar at its fret as the open notes are at the nut
  (`scaleNoteOpenness`, turning between dot and ring as the capo slides), and the chord rows draw the shapes fingered above the capo; the Scales tab opens it with
  the tabs' own capo (`scaleLibraryCapo`). The bar lies across the strings from the nut only (`nutRows`): the banjo's
  drone is spiked the capo's frets above its fifth fret, ringing there as the chord rows play it.
- The neck's dots carry degrees or note names (`UserPreferences.areScaleNoteNamesShown`, kept on the device), flipped by one icon toggle
  in the header, showing the mode it is in; the keyboard carries none, and its header has no toggle. The title
  wraps to two lines (`titleMaxLines`), so a long scale's name is never cut short.
- The chord rows (seven-note scales only), their numerals following Chord degrees, follow Chord diagrams and need an instrument with shapes; shapes come from the
  tables only, never a search during composition. A chord tap opens its `ChordReference` sheet over this one, without
  scales; opened from a chord sheet, this sheet has no chord rows (`showsChords`), so dialogs never stack deeper than two.
- The sheet's neck gives the dots the room: rows about a fret tall (`sheetHeight`), a narrow open column, fret numbers
  sized from the frets; inlays stay faint and small.
- A play toggle beside the formula (balanced by an empty space so the formula stays centered) plays the scale once up,
  root to octave over middle C, through the tuner's reference tones (`TunerController.toggleScale`, `scaleToneNotes`),
  at the tuner's reference pitch whether the tuner is switched on or not. It stops the metronome (iOS shares the audio
  session) and stops when the sheet closes or is covered, and when the app leaves the front (ON_STOP), and the web's
  gesture rule counts the sheet as a tone screen.
- The degree sounding gets a circle behind it in the formula (`ScaleDegrees`), sliding from degree to degree, growing
  and fading in at the start, shrinking and fading out at the end, sized for the widest degree, and back to the first
  degree for the octave; the neck is left alone. The circle moves first and each note sounds as it arrives
  (`SCALE_NOTE_LEAD_MILLIS`, the circle's travel time), so the circle seems to play it.
