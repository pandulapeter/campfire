<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :data:formats

Pure-Kotlin PDF and Word text extractors and zip reader/writer, on no third-party library (only `kotlinx.coroutines`)
and no module but `:data:model`, `:chordpro`, and `:chordsheet` (a document is extracted into its `ChordSheet`): no
`FileStorage`, no Koin, no network. Public is only what `:data:source:local:implementation` uses. Documents are read locally, never rendered, uploaded, or kept.

## `document/`

- Images and font programs are never decoded. `DocxTextExtractor` opens only `word/document.xml` and `word/styles.xml`;
  the XML parser refuses DTDs. Deleted text and external parts are left out.
- **Every limit bounds untrusted input; keep them all**, including the per-document work budget (re-reading a shared
  form or contents stream is paid for) and the font-table-entry budget (shared `/W` arrays and CMaps paid for each
  time). Bridging spaces are charged to the text budget, so a tiny monospace font cannot pad a page to hundreds of
  megabytes. A budget running out is `PdfLimitException`, not a malformed object.
- Damage is local: a malformed page, font, or form costs only itself; the document fails only when more than half its
  glyphs or pages do. Budgets, encryption, a broken page tree, and over 2,000 pages fail it whole (a songbook silently
  losing its tail is worse than one reported unreadable).
- Must stay linear: `PdfStreamEnds` finds every `endstream` once per file; at most 1 KiB of whitespace is walked past a
  declared `/Length`.
- Columns: a gutter is a 0.8 em whitespace band crossed by at most a fifth of the lines, with a fifth of the characters
  on each side, counting no line that crosses it (a lyric beside the gap in its chord row is no second column) and
  leaving a crossing first or last line of the page out as well, **found at every half point, not sampled** (Campfire's
  export leaves only 18 points between 20-point text). A band no line crosses that opens a column beats one a heading
  crosses; among equally crossed bands, the fewest characters crossing wins (a page number under the gutter, not a lyric
  filled to the edge), then the widest. Each side is searched again (three and four columns). A page whose letters
  mostly read from the right is read from its right column.
- Running lines (`PdfRunningLines`) are judged on margins laid out across the whole width, never by the columns: a short
  last page can read as columns and cut its header apart. A dropped margin line takes its whole baseline. A number in a
  running line is the same on every page or counts the pages, so a heading opening two pages (`Verse 2`, `Verse 6`)
  stays; a page count stands alone (`Page 1 of 2`), never a bare `1/2`, which a time signature also reads as. A page's
  lowest line counting this page (`2 / 3`) is dropped anywhere in the lower half (a wide margin lifts it).
- A PDF whose `/Producer` is Campfire is flagged (`ChordSheet.isCampfireExport`) for the converter to read by
  layout; a span is italic by its font's name or italic flag; a line carries its baseline.
- An `ActualText` over glyphs on one baseline replaces them (how Campfire's export carries right-to-left runs in logical
  order); the glyphs stay as its `pieces` where they spell its text in order, so the converter still knows where each
  character is drawn. One spanning several baselines (a hyphenated word) is ignored.
- Other producers draw right-to-left text glyph by glyph from the left, so `PdfBidi` reorders such a line (a subset of
  UAX #9, `:chordpro`'s `BidiOrder`, runs by `isStrongRtl`) into one span with `pieces`; a line with an `ActualText` is
  left alone. A line's direction is the side of its column it aligns to, else its column's letters, chord rows left out.
  A mirrored bracket is read as drawn (ReportLab) or as meant (Chrome), whichever pairs the line's brackets; a mark goes
  with the letter under it.
- Page, operator, and work yields keep the web build responsive.

## `zip/`

Exists because no multiplatform zip library covers wasmJs. Reads STORED and DEFLATE (`Inflater` follows `puff.c`); ZIP64
archives are rejected, ZIP64, encrypted, or otherwise compressed entries left out.

- **`ZipWriter` writes STORED only, on purpose**: a stored archive's size is what an import unpacks, so the export can
  warn past `ImportLimits.MAX_IMPORT_SIZE`. Over 65,534 entries are refused rather than written as ZIP64.
- **Declared sizes are only a first guess.** Entries are judged by name and declared size before inflating
  (`EntryPolicy`), or under an uninformative name by a 512-byte peek (`Sniff`); a declined peek costs no budget. The
  caller's import budget is charged before an entry is read; inflation stops at the declared size. Each entry's
  compressed bytes are charged once, and may not add up to more than the archive holds (zip bombs pointing many entries
  at the same bytes). The buffer starts at no more than 1 MiB whatever the directory claims.
- An unreadable entry is left out and reported by name (the module's one print); only an unwalkable archive is a
  `ZipException`.
- Entry names are UTF-8 where flagged or valid (macOS does not flag), else code page 437. A backslash is a separator too
  (Windows PowerShell 5.1).

## Tests

End-to-end document goldens live in `:data:source:local:implementation`. An extraction change is judged by the same
import corpus as a converter change (`chordpro/convert/CLAUDE.md`).
