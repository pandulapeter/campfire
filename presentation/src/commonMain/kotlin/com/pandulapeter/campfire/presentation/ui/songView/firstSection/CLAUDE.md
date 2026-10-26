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
  in `GroupedRows`, in groups kept whole where they fit on a line: the instrument and tuning pill, key and capo, tempo
  and time signature. Groups share a line where they fit (16dp apart, 8dp within one); one too wide flows its items and
  the next starts under it (`groupedLines`, tested). A moved control springs on the sections' spring via its
  `layoutId`. A control whose label and stepper are wider than the line together puts its label above
  (`PlayingControl`), and only its own line grows for it; every line is at least a pill tall.
- **Kept short, and every line starts with a pill**: `[transposition] Capo [capo]` and `[tempo | Tap] 3/4 [metronome]`,
  no unit (the stepper says "BPM" aloud), 4/4 left out. The transposition has no label and reads the sounding key alone where
  the song names one (`transpositionValue`; the semitones are only said aloud), the reader's own semitones where it
  names none.
- **One keyline for the pills**: the controls start where the section pills reach out to (`HEADER_HORIZONTAL_PADDING`
  short of the lyrics), so the instrument pill, the transposition, and the tempo line up; a capo with no transposition
  before it starts on the lyrics' keyline, being a label.
- **Controls scale with the lyrics like the header pills**: `fontScale` grows their content, not their padding, so each
  is exactly `songControlHeight` (the pill's `titleStyle` plus `HEADER_VERTICAL_PADDING`). Scaling a pill whole towered
  over the page. `UserPreferences.MIN_FONT_SCALE` is what keeps them big enough to hit.
- Tool buttons (`SongPlayingTools`, `SongToolButton`): the tuner after the instrument and tuning pill where it is
  shown (while it fades, too, so it is never shown twice), else after the capo (or the transposition); the metronome
  after the time signature (the tempo at 4/4); a button whose group is absent goes after the other (`playingToolPlaces`, tested). With
  chords and metronome both off there is no section and the tuner is an overflow menu entry (`tunerAction`).
- Whether the section holds either button is part of `RenderSection.Metadata`, since measured sizes are keyed by it.
- Read only line (performance mode, archived setlist): always names capo, tempo, and time, since an absent value would
  read as unknown (`readsDefaults`: "Capo 0", 120, 4/4); the editor's preview names a capo only where the text writes
  one. It reads values as the click and the steppers do (tempo clamped to the click's range, `C` as 4/4, capo within the
  neck); split around the tools.
- In the editor's preview (`isSongInfoShown`) the section is the About the song tiles (`SongInfoCard`), emitted under
  one key whatever it says, so its cover waits for typing to pause rather than fetching half-typed addresses.
- The section's content holds what the instrument and tuning pill names (`InstrumentPillContent`), since label widths
  decide whether the tuner fits beside it and a section's measured size is keyed by its content; a leaving tuning half
  is named until it has faded out.
- Over the read only line, the pill reaches out of its column as far as a section's pill, so their edges line up
  (`reachingOutBy`, which also gives that width to the tuner beside it); the tuner goes under it whole where it does
  not fit. It has a line of its own over the About the song tiles, and over a read only line without tools.
