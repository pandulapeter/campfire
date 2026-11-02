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

### Sections

- **A section cut by a comment, break, or `{transpose}` is drawn whole again** (`joinCutSections`, shared with the
  PDF), so the comment stays between its lines rather than becoming a unit that could land elsewhere. Comments opening
  or ending a section join it, so they fold with it; one between sections is its own unit, never folding. A chorus
  recall is the one cut not joined.
- A later `{transpose}` leaves a "Key: A" line in the reader's transposition and notation, the key of the page's chords
  as the Transposition control names it; omitted where the song has no `{key}` or chords are off.
- Lyrics-only mode drops tab, grid, and verbatim lines (comments inside a tab or grid too), a section left empty whole,
  and the info section's key, capo, tempo, and time line (the section where nothing else is).
- A comment's bracketed chords are transposed and colored, as in the editor.

### Tempo and time changes (product side: `ui/metronome/CLAUDE.md`)

- **A later `{tempo}` / `{time}` starts a page of its own**, never joined into its section, even in a song that fits one
  screen, since the page is the signal; a read-only timing line stands in its place; the rest of the section is headed
  by its fold toggle alone, folded under the section's key plus `~n`, so switching the Metronome feature does not
  renumber later folds. With the feature off it is joined over like a `{transpose}`.
- A timing line with no song line after it is dropped; a change before the first line starts no page.

### Fold keys

- **A fold is keyed by what the file calls the section** (label as written, else kind) plus occurrence (`chorus#2`), a
  run by its section's key plus its own label or kind (`Intro#1/Picking pattern#1`). Never by position (a verse added
  above would shift every fold), never by the shown heading (translated).
- Unnamed kinds are labelled in the app's language and numbered where a song has several (a preference); only
  unlabelled sections and those labelled with the kind's own name are numbered. The number is `Section.number`, not
  part of the label, so saved folds do not move with the setting. An unnamed `{chorus}` recall takes the repeated
  chorus's number.
- A run's toggle is labelled by its environment's label, else "Tab" / "Grid" / the verbatim kind ("Sheet music" for
  ABC); back-to-back environments with different labels fold apart; a section that is only one run folds as the
  section, with no second toggle (always so for a verbatim run).

### Building the model, and the budget

- Built off the main thread except the first page on screen and those before it, so a page never opens empty.
- **One scale walk a model** (`songScaleAnalysisOf`), shared by the Scales section and the Chords section's key groups
  and degrees, in the in-place first model too so degrees are right on the first frame; never for a songbook. Without a
  home the degrees count from `SongKeys.ofChords` (the capo chart rule).
- **The scales read the sounding song with the file's capo line** (`standardSong`, before `withCapo`): that line only
  says how a capo chart's `{key}` is meant, so the section names the key the capo played sounds in, and its sheet dims
  the neck below that capo. The chord a row is "over" is named as the page names it; its scales stay the sounding ones.
- **`LayoutBudget` caps what is laid out** (3,000 lines, 200,000 characters): every chorded line is measured on the
  main thread, and a concatenated songbook froze the app for seconds. The model stops after the last section that fits
  and says so; the file is untouched and the header is built from all of it.
