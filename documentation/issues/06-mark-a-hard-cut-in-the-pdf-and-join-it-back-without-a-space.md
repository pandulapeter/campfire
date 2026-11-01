<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Mark a row the PDF cut inside a word, in lyrics, titles, comments, and the details row, and join it back without a space on import

**Challenged:** amended — the cut-at-a-space test now looks past the chord layout's invisible marks at both ends (a
padded piece's closing direction mark, and the marked padding a right-to-left row may open with), else those cuts would
be joined with no space; that one helper also fixes `isContinuation`, which takes over plan 09's part (b); no marks on
monospaced rows (tabs, grids); the importer's `clean` drops the word joiner, so a Campfire PDF copied as text leaves
none in the file; the German round trip expects the key 02a brings back.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintTextWrapping.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLyricRows.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRows.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintDocument.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRenderer.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PdfRoundTripTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetCampfireLayout.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`

## Problem

`wrapPrintText` cuts inside a word where the word fits no line of its own (the hard cut: "The cut only ever moves
back"). Every text it wraps can then come back from an import in pieces, and nothing in the PDF says the cut was inside
a word:

- **Lyrics** — `PrintLyricRows.layOut` marks a row as the rest of the line above only where the cut fell at a space:

  ```kotlin
  val isContinuation = fragmentIndex > 0 && !fragment.isPrintBlank() &&
      (fragments[fragmentIndex - 1].trimEnd('​').lastOrNull()?.isWhitespace() == true || fragment.firstOrNull()?.isWhitespace() == true)
  ```

  so a hard-cut row is unmarked and imports as a line of its own. Probed, 4 columns, default size:
  `[F]Another rather lengthy line, with [C]commas, and words like extraordinarily [G]and incomprehensibilities, to [C]end`
  comes back as three lines, `… [G]and i` / `ncomprehensibilitie` / `s, to [C]end`.
- **Titles** — `PrintRows.wrapped` marks nothing; the importer joins the title's rows by their type with a space
  (`extractTitle`: `(first..last).joinToString(" ") { lines[it].text.trim() }`). Probed, 4 columns:
  `Pasted Spaces` (a no-break space, no break opportunity) → `Pasted Spa ces`; `Árvíztűrő tükörfúrógép` →
  `Árvíztűrő tü körfúrógép`.
- **Details row** — `PrintRows.detailRows` marks every wrapped row as a continuation, and `joinWrappedDetails` always
  joins with a space:

  ```kotlin
  val text = previous?.let { "${it.text} ${line.text.substring(indent)}" }
  ```

  so a label cut inside (`Trasposizio` / `ne: +2`) no longer reads as a label, `labelledRow` refuses the joined row, and
  the rows stay apart. Probed: Italian, A4, 4 columns, size 20, transposed +2 → a stray lyric `Trasposizio ne: +2`;
  German, 4 columns, size 20, 25 mm margins, a capo with shapes → `Kapodaste` / `r: 2 (Em-G` / `riffe)`, so the import
  loses the capo altogether (the song comes back a whole step low) and gains a lyric `Kapodaste r: 2 (Em-G riffe)`. The
  round-trip test prints only size 12 at 15 mm margins, where no label is cut.

## Fix

One form for all three: the print marks a hard cut apart from a cut at a space, and the import joins a hard cut with
nothing between.

1. `PrintTextWrapping.kt`: one helper deciding where a row was cut, used by both marks:

   ```kotlin
   /** The chord layout's invisible marks: break opportunities, direction marks, and the mark opening a padding run. */
   private fun String.trimLayoutMarks() = trim('\u200B', '\u200E', '\u200F', PADDING_MARK)
   /** Whether the cut between [previous] and [fragment] fell at a space (a no-break space of the padding counting as one). */
   internal fun isCutAtSpace(previous: String, fragment: String) =
       previous.trimLayoutMarks().lastOrNull()?.isWhitespace() == true || fragment.trimLayoutMarks().firstOrNull()?.isWhitespace() == true
   ```

   (`trim` takes the marks off both ends; only the end of [previous] and the start of [fragment] are read.) Trimming
   only `'\u200B'`, as `PrintLyricRows` does today, is not enough for a hard-cut test: `padLyricsToFitChords` closes a
   piece read against its line with a direction mark after its padding (`…and‎ ⁠  ‎​`), and puts the extra padding
   a right-to-left row lacks *after* the break opportunity, so a row the line wraps onto opens with `⁠  `. Both
   are cuts at a space; read past only `'\u200B'` they would be hard cuts and their words joined with no space.
2. `PrintText` and `Part` gain `isHardCut: Boolean = false` (KDoc: the row continues a word the column cut, so its
   selectable text opens with two word joiners, which the import joins back with no space); `PrintLayout`'s copy of a
   part into a `PrintText` (the positional constructor call in `place`) carries it.
3. `PrintLyricRows.layOut`:
   `val isContinuation = fragmentIndex > 0 && !fragment.isPrintBlank() && isCutAtSpace(fragments[fragmentIndex - 1], fragment)`,
   `val isHardCut = fragmentIndex > 0 && !fragment.isPrintBlank() && !isCutAtSpace(fragments[fragmentIndex - 1], fragment)`;
   the lyric part takes `isContinuation = isContinuation || isHardCut, isHardCut = isHardCut`, and the first chord part
   `isContinuation = (isContinuation || isHardCut) && parts.isEmpty()` (the chord row keeps the one mark it has today).
   This also marks the rows plan 09 (b) found unmarked (a wrap right after a padded piece's direction mark), as a
   continuation: that part of plan 09 lands here.
4. `PrintRows.wrapped`, `formattedRows` (a comment with markup), and `detailRows`: mark each row after the first that
   `!isCutAtSpace(previous, row)` says is a hard cut (`isContinuation = true, isHardCut = true`), except in a monospaced
   style (`tabStyle`, `gridStyle`: a tab or grid row is read back by its own runs, and a mark would make it a
   continuation to the classifier); `detailRows` keeps marking the rest of its wrapped rows as continuations as now. A
   row cut at a space in `wrapped` stays unmarked here (plan 05 deals with wrapped artists and may mark those; the two
   marks compose).
5. `PrintRenderer.selectableText`: append `CONTINUATION_MARK` twice where `item.isHardCut`, and skip a cluster that is
   nothing but marks (`value.all { it == CONTINUATION_MARK }`, today `value.length == 1 && value[0] == CONTINUATION_MARK`),
   so the marks wait for the first cluster that writes anything. The item's own `PADDING_MARK`s are dropped there as
   now, so the only word joiners in the text layer are these.
6. `ChordSheetCampfireLayout`: document that two `CONTINUATION`s open a row that continues a word (no new constant
   needed). `ChordSheetConverter.render`: `isHardCut` on `Rendered` where the first non-empty span's text starts with
   two `CONTINUATION`s (only where `dropsPadding`, as `isContinuation` is, so another producer's PDF reads as now).
   `clean` drops `'\u2060'` too, as it drops the other invisible format characters (`\u200b`–`\u200d`, `\ufeff`):
   today a Campfire PDF's text copied out of a viewer and pasted (plain text, no export flag) keeps its word joiners in
   the song file, and this plan doubles them. `render` reads the marks off the raw span text before `clean`, so the
   export's reading is unchanged.
7. Joins in the converter, each with `if (line.isHardCut) "" else " "`:
   - `BodyWriter.addLyric(line, continues, isHardCut)` (both call sites pass the lyric row's flag);
   - `joinWrappedDetails` (and no extra position for the space it no longer inserts);
   - `extractTitle`'s join of the title's rows;
   - the `Kind.COMMENT` run's `joinToString(" ")`.
8. Docs: `ui/print/CLAUDE.md` (the word-joiner sentence: a cut inside a word opens with two; the chord layout's marks
   are looked past in telling the two apart), `convert/CLAUDE.md` (the continuation sentence: two word joiners join
   with no space).

Where the cut lands right after the export's padding (no-break spaces), the row counts as cut at a space, as today.
Pasted text, Word documents, and other producers' PDFs never carry the flag (`dropsPadding` is the export's alone), so
they read as now, apart from the dropped word joiner.

Touches `PrintRows.wrapped` and `extractTitle`, which the plan for finding 05 (a wrapped artist) also touches; land 06
first or rebase that one onto it. Plan 09 (b) relies on step 1's helper.

## Tests

- `PrintLayoutTest`: lay out, with the arithmetic measure, a lyric whose one word is wider than the column, a title
  likewise, and a details row whose label is; assert the rows after the cut have `isHardCut` and `isContinuation`, and a
  lyric cut at a space has `isContinuation` alone. Beside `wrapPrintText`'s tests, `isCutAtSpace` directly: true for
  `"and\u200E\u00A0\u2060\u00A0\u200E\u200B"` / `"ילדה"` and for `"word\u200B"` / `"\u2060\u00A0\u00A0more"`, false
  for `"and i"` / `"ncomp"`. The first two fail with `'\u200B'` trimmed alone.
- `ChordSheetConverterTest`: a Campfire export `ChordSheet` (as the tests at the `isCampfireExport = true` cases build
  it) holding `Trasposizio` / `\u2060\u2060ne: +2` in the header, a lyric `and i` / `\u2060\u2060ncomprehensible`, and a
  title `Árvíztűrő tü` / `\u2060\u2060körfúrógép` in the title's type; assert `Trasposizione` leaves no lyric, the lyric is one line
  `and incomprehensible`, and the title is whole. Fails before the fix.
- `PdfRoundTripTest`: a test printing, in 4 columns, `{title: Árvíztűrő tükörfúrógép}`, a `{title}` with a no-break
  space, and the long lyric above, expecting each to come back whole; and `{key: F#m}{capo: 2}` over Em shapes with
  `shapesKey = "Em"` in every `values*` folder at `PrintSettings(columns = 4, fontSize = 20, marginMm = 25)`, plus
  transposition 2, expecting the capo 2 back, `{key: Em}` (plan 02a, landed first), and no lyric but the verse's (as
  the capo test checks it). Fails before the fix (the German capo is lost, the Italian row is a lyric).
- `ChordSheetConverterTest`: a plain text holding `Hello\u2060\u2060 world` converts with no `\u2060` left. Fails
  before the fix.
