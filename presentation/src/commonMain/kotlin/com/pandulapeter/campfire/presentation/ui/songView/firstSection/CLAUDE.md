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

The controls in a song's first section (`SongMetadataSection.kt`): key, capo, transposition, tempo, and the playing
tools. What the first section holds is in `ui/CLAUDE.md`.

- Flows through the same rows and columns, never cut. Controls are `SongPlayingControls` (`SongPlayingControlsRow.kt`)
  in `BalancedRows`: one line, two and two, or one per line, never three over one; a moved control springs on the
  sections' spring via its `layoutId`. A control whose label and stepper are wider than the line together puts its label
  above (`PlayingControl`), and only its own row grows for it; every row is at least a pill tall.
- **Controls scale with the lyrics like the header pills**: `fontScale` grows their content, not their padding, so each
  is exactly `songControlHeight` (the pill's `titleStyle` plus `HEADER_VERTICAL_PADDING`). Scaling a pill whole towered
  over the page. `UserPreferences.MIN_FONT_SCALE` is what keeps them big enough to hit.
- Tool buttons (`SongPlayingTools`, `SongToolButton`): the tuner after the instrument and tuning chips where they are
  shown (while they fade, too, so it is never shown twice), else after the capo (or the transposition); the metronome
  after the time signature; a button whose group is absent goes after the other (`playingToolPlaces`, tested). With
  chords and metronome both off there is no section and the tuner is an overflow menu entry (`tunerAction`).
- Whether the section holds either button is part of `RenderSection.Metadata`, since measured sizes are keyed by it.
- Read only line (performance mode, archived setlist): always names capo, tempo, and time, since an absent value would
  read as unknown (`readsDefaults`: "Capo 0", 120, 4/4); the editor's preview names a capo only where the text writes
  one. It reads values as the click and the steppers do (tempo clamped to the click's range, `C` as 4/4, capo within the
  neck); split around the tools.
- In the editor's preview (`isSongInfoShown`) the section is the About the song tiles (`SongInfoCard`), emitted under
  one key whatever it says, so its cover waits for typing to pause rather than fetching half-typed addresses.
