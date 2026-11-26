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

The song's first section, which only the editor's preview draws, and what sets the values it says, which song details'
app bar, dock, and sheets use. Song details draws none of the section: its app bar's key chip (`screens/songDetails`)
names the key and capo, its Playing sheet (`dialogs/playing`) holding the transposition and capo steppers
(`TranspositionControls`, `CapoControls`), and its dock (`screens/songDetails/dock`) the tempo on its tempo chip, whose
Tempo sheet holds the tempo stepper (`metronome/TempoStepper`); the values and what steps them are
`rememberSongPlayingControls`'s (`SongPlayingControls`).

- **In the preview the section is the About the song tiles** (`SongInfoCard`), under one key whatever it says, so its
  cover waits for typing to pause rather than fetching half-typed addresses. The instrument and tuning pill, where there
  is one, has a line of its own over it, reaching out as far as a section's pill, edges lined up; a leaving tuning half
  is named until faded, since measured sizes are keyed by what the section holds.
- The tiles' values are read as the click and steppers read them (`withMetadataSection`: tempo clamped to the click's
  range, `C` as 4/4, capo within the neck); the chords switched off take the key and capo away, the metronome the tempo
  and time.
- The transposition reads the key of the page's chords where the song names one (semitones only said aloud), else the
  reader's semitones; where that key names the shapes above a capo, the sheet says so.
- `SongMetronomeTool` is what the dock's tempo chip says and opens; the tuner is the song's menu's and the Playing
  sheet's, never the dock's.
