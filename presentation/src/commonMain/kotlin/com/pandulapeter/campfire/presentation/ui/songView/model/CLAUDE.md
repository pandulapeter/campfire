<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/songView/model

What a song page draws, built from the parsed song (pure): `SongLyricsModel`, `RenderSection`, the units the layout cuts
(`SongUnits`), fold keys, and chord cells.

### Sections

- **A section cut by a comment, break, or `{transpose}` is drawn whole again** (`ui/songLayout/`'s `joinCutSections`,
  shared with the PDF), so the comment stays between its lines rather than becoming a unit that could land elsewhere.
  Comments opening or ending a section join it (`Comment.placement`), so every comment inside a section folds with it;
  one between sections is a unit of its own that never folds. A chorus recall is the one cut not joined.
- A later `{transpose}` leaves a "Key: A" line (`RenderSection.KeyChange`) in the reader's transposition and notation,
  the capo left out as in the Transposition control; omitted where the song has no `{key}` or the chords are off.
- Lyrics-only mode drops tab and grid lines (and comments inside them, `isInTabOrGrid`), a section left empty whole, and
  the info section's key, capo, tempo, and time line (the section itself where nothing else is, `withMetadataSection`).
- A comment's bracketed chords are transposed and colored (`ChordProHighlighter.chordsOfShownText`, shared with the
  editor).

### Tempo and time changes

Product side in `ui/metronome/CLAUDE.md`.

- **A later `{tempo}` / `{time}` starts a page of its own** (`RenderSection.Timing`, `showsTiming`): never joined into
  its section; a read-only `SongTimingLine` stands in its place; the rest of the section is headed by its fold toggle
  alone and folded under the section's key plus `~n`, so switching the Metronome feature does not renumber later folds.
  With the feature off it is joined over like a `{transpose}`.
- A timing line with no song line after it is dropped (`withoutEmptyTimings`, after `LayoutBudget`).
- A change before the first line starts no page (`timingStretchesOf`); `SongRows.timingSections` reports it at section
  0.

### Fold keys

- **A fold is keyed by what the file calls the section** (label as written, else its kind) plus which occurrence
  (`chorus#2`), and a run by its section's key plus its own label or kind (`Intro#1/Picking pattern#1`). Never by
  position (a verse added above would shift every fold) and never by the shown heading (translated).
- Every section has a header: unnamed kinds labelled in the app's language (`DefaultSectionLabels.labelOf`, shared with
  the PDF), numbered where a song has several (`withNumberedSections` in `SectionNumbering.kt`,
  `UserPreferences.shouldNumberSections`). Only unlabelled sections and those labelled with the kind's own name are
  numbered. The number is `Section.number`, not part of the label, so saved folds do not move with the setting. An
  unnamed `{chorus}` recall takes the repeated chorus's number.
- A run's own toggle is labelled by its environment's label, else "Tab" / "Grid"; two back-to-back environments with
  different labels fold apart; a section that is only one run folds as the section, no second toggle.

### Building the model, and the budget

- `rememberSongLyricsModel` parses, transposes, and splits off the main thread, except the first rendering of the page
  on screen (and pages before it), built in place (`buildsModelInPlace`) so the page never opens on an empty frame and a
  step back from a song's top has the previous song's end to land on. Later builds keep the previous rendering until the
  next is ready.
- **`LayoutBudget` caps what is laid out** (3,000 lines, 200,000 characters): every section is fitted and every chorded
  line measured on the main thread, and a concatenated songbook froze the app for seconds. The model stops after the
  last section that fits and says so; the file is untouched and the header is built from all of it.
