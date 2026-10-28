<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# chordpro/convert

`ChordSheetConverter` is one deterministic conversion for plain text, PDF, and Word; it knows no file format. Its
heuristics and near misses are pinned in `ChordSheetConverterTest`: a change belongs in a test first.

- **No recognized song structure means escaped prose, not a guessed song** (`ChordProLiteralText`), and a line that is
  as likely sung (a single chord, `La La La`, "Time after time") is a chord or a field only with corroborating evidence.
- It reads back Campfire's own PDF export (the heading row, wrapped chord lines, narrow-column titles). A sheet flagged
  as one (`ChordSheet.isCampfireExport`) is read by the type each line is printed in (`ChordSheetCampfireLayout`):
  labels open sections (a label over a tab is the tab's), grids, italic comments, and boxed comments come back as
  written, the padding under wide chords is left out, a chord is set on the character it stands over, and staff lines
  are told by the test the export engraves by. A wrapped tab system comes back as the system it was printed as.
- A document is cut into songs by `ChordSheetPageSplit`: a later page opens a song only with a title in the type of
  the document's first one (a bold title of the body's size not followed by more bold lyrics, which is a chorus), and
  a page repeating the current song's title continues it, the repetition left out where it is marked (`(cont.)`, a
  page number) or set in the title's type.
- Chord shapes listed one a line (`Bb(#11)/A = x03330`) become `{define}` lines in the header
  (`ChordSheetDefinitions`), except a chord listed both plain and starred (`Bb`, `Bb*`): the star is dropped where the
  chord is played, so one definition would stand for both.
- A lyric indented under its first chord starts at that chord; two chords or more in the indent keep it.
- A header labelled with a colon or a semicolon names title (`Song;`), album, and year; a credit ending in a year
  gives both, and a credit printed twice is written once; an Artist label's value is a name, kept whole unless a comma
  sets a year off (`The 1975`).
- `convertFragment` converts what is pasted into the editor: the body as `convert` writes it (chords set, headings as
  sections, prose escaped), but no title or credit guessed and metadata lines kept as text, since the song it goes into
  has its own header. Only a paste with a chord row or a bracketed heading
  (`[Verse]`, which ChordPro would read as a chord) is converted.
- `setChordRows` sets the rows a format marks itself as they are marked, guessing nothing, for OpenSong.
- `convert` can be told not to guess a title (`guessesTitle`), for a caller that has the song's title from another
  app's field.
- A row of chord names right over a tab staff is a line of the tab, kept as it stands: its columns name the staff's
  bars, and it was never written over a lyric.
- The heading vocabulary lives in `ChordSheetHeadings`. A heading word that is also sung (`Hook`, `Tag`, `Coro`, `R`)
  is one only in brackets, with a colon, or with a chord row or a blank line after it.
- Section labels printed in a margin column (the ChordPro app's `label`, `V1`, `Ch1`, `A1`) open the sections they
  label (`ChordSheetMarginLabels`): only where every other line between a page's first and last label starts right of
  all of them, so a chord row or a lyric set against the left edge keeps its first word. A label no heading word or
  abbreviation names is a verse under its own name.
- The rest of what the ChordPro app prints is read back too: a header line printed in left, centre, and right parts
  (`ChordSheetHeaderParts`, a gap of three ems, above the first chord row only) is one line per part, the part beside
  the title or a line centred under a centred title being its subtitle; a note among a row's chords (`(x)`,
  `(to v:)`) is an annotation where it stands, one ending the row staying text after the lyric; the dashes it fills
  a chord's width with at a lyric's end are left out; and a `©` line is the copyright wherever it stands.
- The title shapes a copied page opens with come from `ChordSheetTitles`. `Artist - Title` is read artist first, a
  documented limit, since both orders are common on the web; a title ending in a page word with no credit after it
  (`Three Chords`) loses that word, the same documented limit.
- A row's columns are read only in text laid out in characters: a row of three or more chords typed one space apart
  over a much longer lyric lost them on copy and is written above its lyric, not into it.
- Genius's injected `You might also like` and closing `12Embed` are left out of a song (never of prose). Ultimate
  Guitar's page furniture (`Difficulty:`, `Author:`, a rating) is many lines in no fixed shape and is not filtered.
- `ChordSheetStructure.hasSongStructure` is the one answer to "is this text a chord sheet at all", stricter than the
  converter (a single-token row, a Markdown link, a lone `(a)` never count): the import's rule for text under an
  unknown name, where a README or a licence must stay skipped rather than become prose.
