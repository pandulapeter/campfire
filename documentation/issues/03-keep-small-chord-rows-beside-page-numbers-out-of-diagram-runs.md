<!--
This file is part of Campfire.
Copyright (c) Pandula Péter 2017-2026.
https://github.com/pandulapeter/campfire

This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
https://mozilla.org/MPL/2.0/.
-->

# Keep a small chord row beside a page number or a count row out of a chord diagram run

**Challenged:** amended — both proposed rules fail on the one diagram fixture (`PdfLineLayoutTest`, `chord diagrams
over the song are left out…`): its fret rows reach 10 pt left of the first name and 20 pt right of the last (a grid is
wider than its centred name), so "marks within the names' horizontal extent" keeps a real diagram as text, while an
overlap test would still take a centred page number under an outro spanning the page; its name-to-frets gap is 40 pt,
5 ems of the 8 pt names but 6.7 of the 6 pt frets, and a full page's last line sits about that far over its footer, so no
gap separates them. The fix now narrows what counts as a mark instead.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (PDF import)
**Files:** `data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/document/PdfChordDiagrams.kt`,
`data/formats/src/commonTest/kotlin/com/pandulapeter/campfire/data/formats/document/PdfLineLayoutTest.kt` (holds the one
diagram test), `data/formats/CLAUDE.md`

## Problem

`PdfChordDiagrams.withoutDiagrams` groups a page's glyphs into rows by baseline and drops any run of consecutive
small-type rows holding both chord names and marks:

```kotlin
while (isLabel[start] && end + 1 < rows.size && isLabel[end + 1]) end++
val run = (start..end).filter { isLabel[it] }.map { kinds[it] }
if (Kind.NAMES in run && Kind.MARKS in run) (start..end).forEach { rows[it].forEach { index -> isDropped[index] = true } }
```

A run has no limit on the vertical distance between its rows, `isMark` takes any number of up to two digits and any
single non-alphanumeric character, and this runs before running lines (the page-number footer) are removed. Scenarios:

- Lyrics 12 pt, chord rows 10 pt (0.83, under `SMALL_TYPE` 0.85); the song ends in a chord-only outro row
  `G   C   D   G`; the footer is `3` or `- 3 -` in 9 pt. The outro row (NAMES) and the footer (MARKS) are consecutive
  small rows: both dropped, and the outro's chords vanish from the import.
- A small chord row over a small strumming count row (`1 & 2 & 3 & 4 &`): both dropped.

## Fix

What a diagram's marks are and what a footer or a count row is differ in the characters and their number, not in
place:

1. **`isMark`'s lone symbol is not ASCII punctuation.** A diagram draws an open or muted string as `x`, `o`, or a
   symbol (`○`, `●`, `•`, `×`); `&`, `+`, `-`, `–`, `—`, `/`, `|`, `.` are what count rows (`1 & 2 &`, `1 + 2 +`),
   page footers (`- 3 -`, `3 / 5`), and bar lines are made of. Keep a single non-letter-or-digit character a mark only
   when it is not ASCII and not a dash (`–`, `—`). Those rows become `TEXT`, which ends a run.
2. **A run is a diagram only with a `MARKS` row of at least two marks.** A diagram's mark row has one mark per string
   (or per fingered string); a lone page number `3` is one. A one-mark row still joins a run (a barre's position `5`
   beside its grid), it just does not make one by itself.

So the outro + `3` run keeps its names (no qualifying marks row), the outro + `- 3 -` and the chord + count rows are
broken by a `TEXT` row. Known residual: a small chord row over a small count row of bare digits (`1 2 3 4`) is still
taken for a diagram; nothing in the glyphs tells them from a diagram whose open-string row sits right under its name.

Run the import corpus (`ImportCorpusTest`, `campfire.importCorpus`) before and after: no document's overall score may
fall (`libs/chordsheet/.../convert/CLAUDE.md`). If a document whose diagrams draw a string with ASCII punctuation
(`*`, `-`) regresses, narrow rule 1 to the characters seen in count rows and footers (`&`, `+`, `-`, `/`, `|`) rather
than dropping it. Drop the plan if the corpus loses diagrams either rule cannot be adjusted for. Update
`data/formats/CLAUDE.md`'s diagram line with both rules and the residual.

## Tests

Beside the existing diagram test in `PdfLineLayoutTest` (`Placed` glyphs through `PdfTextExtractor`, which runs
`withoutDiagrams`), with the song text at 12 pt as there: (1) a 9 pt chord-only row `G   C   D   G` ending the song, then
an 8 pt page number `3` far below: the chord row is kept; (2) the same with `- 3 -`; (3) a 9 pt chord row over a 9 pt
`1 & 2 & 3 & 4 &` row over a 12 pt lyric: the chord row is kept; the existing diagram test still passes unchanged.
