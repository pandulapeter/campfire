<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Keep an italic lyric line under a chord row a lyric when importing Campfire's own PDF

**Kind:** bug · **Severity:** medium · **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetCampfireLayout.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheet.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt` (only if roles
need the chord rows from there), `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/importing/ExtractedDocument.kt`,
`data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/document/PdfTextExtractor.kt`,
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/mapper/DocumentMappers.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetCampfireLayoutTest.kt`

**Challenged:** amended — "a comment is never printed under a chord row" is false: a chord-only line (`[Am] [G]`)
prints as a chord row of its own, and a `{comment}` after it would be swallowed as its lyric. The two cases differ only
in the vertical gap (1.3 × size inside one row, 1.45 × size between rows), which `ChordSheet.Line` does not carry, so
the fix now plumbs each line's baseline through and decides by the gap; it also covers the wrapped continuation of an
italic lyric.

## Problem

`ChordSheetCampfireLayout.roles` (new in ae33e509e) gives any line set wholly in italics the comment role:

```kotlin
spans.all { it.isItalic } -> Role.COMMENT
```

But the export prints `<i>` lyrics in the italic face too (`PrintLyricPieces.kt` sets the italic flag in
`PrintPdfWriter`); a comment differs only by its gray, which the PDF reader does not keep. So a lyric line wholly in
`<i>…</i>` (a backing vocal, `[Am]<i>Ooh ooh baby</i>`) comes back from Campfire's PDF as `{comment: Ooh ooh baby}`,
its chords stranded as a chord-only line. At cfcf08150 Campfire's PDFs were read like any sheet and that line came back
as a lyric with its chords. `ChordSheetCampfireLayoutTest` has only chordless comment lines.

What tells the two apart (`PrintLyricRows`, `PrintRows`):

- A lyric with chords is **one** `Row`: chord parts at `chordY`, the lyric at `chordY + chordLineHeight`, where
  `chordLineHeight = fontSize * 1.3f`.
- A chord-only line (`[Am] [G]`, `isChordsOnly`) is a row of its own, `fontSize * 1.45f` tall, and a `{comment}` after
  it is the next row, its text at the row's top: the comment's baseline is 1.45 × size under the chords. So the line
  before an italic comment **can** be a chord row (an intro's chords, then `{c: x2}`; a tab run's bold chord line, then
  a comment inside the tab, `isInTabOrGrid`). "Previous line is a chord row" alone would turn those comments into lyrics.
- A wrapped italic lyric continues on rows 1.45 × size apart with no chords over them, exactly as a wrapped comment does.
- `ChordSheet.Line` (and `ExtractedDocument.Line`) carry no vertical position, so `roles` cannot see the gap today;
  `PdfLineLayout.Line.y` has it (top-down, used by `PdfTextExtractor` for its blank-line rule).

## Fix

1. Carry the baseline: add `val baseline: Double? = null` to `ExtractedDocument.Line` and to the public
   `ChordSheet.Line` (explicit API, KDoc: page units, growing downwards, null where unknown — Word, plain text);
   `PdfTextExtractor` fills it from `line.y` (not for the blank lines it inserts), `DocumentMappers.toChordSheet` copies
   it, and `ChordSheetHeaderParts` / `ChordSheetMarginLabels` keep it when they rebuild a line (`copy` instead of the
   constructor).
2. In `roles`, a line wholly in italics is a lyric (role `null`, read like any line) when the previous non-blank line on
   the page is a chord row (the converter's own `rowChord` test over its tokens, or the chord-row flags passed in — no
   new heuristic) **and** both baselines are known **and** the gap is under `1.375 × size` (between 1.3 and 1.45, the
   size being the italic line's span size). Every other all-italic line stays `COMMENT`, including any line whose
   baseline is unknown (today's behaviour).
3. The continuation of an italic lyric: an all-italic line directly after an italic line just given the lyric role, at
   a gap under `1.5 × size` and with no chord row between, is a lyric too. (A real comment printed directly under an
   all-italic lyric is then read as lyric; accept it, it is the rarer case, and say so in a comment.)
4. Columns are emitted one after another, so a baseline that goes *up* from the previous line is a new column: no gap
   rule applies across it.

A fuller fix (the export marking comments in a way the import can read, e.g. a tagged text run) changes the PDF format
and is out of scope.

## Tests

`ChordSheetCampfireLayoutTest`: an italic line 1.3 × size under a chord row is not a comment; an italic line 1.45 × size
under a chord-only row (an intro's chords, then a comment) still is; an italic line under a lyric or a blank still is;
an italic line with no baseline still is; the wrapped second row of an italic lyric is not a comment. A converter test
that an exported `[Am]<i>Ooh</i> [G]baby` line (as `roles` sees it, baselines included) converts back to a lyric with
both chords, and that `[Am] [G]` followed by `{comment: x2}` converts back to both. `PdfTextExtractor`'s tests: lines
carry their baselines.

Lane note: plan 01 edits the same `when` in `roles`; land them one after the other in the same lane.
