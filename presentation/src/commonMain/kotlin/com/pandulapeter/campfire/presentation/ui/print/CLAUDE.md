<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->


# :presentation — ui/print

The PDF pipeline behind the export screen (`ui/screens/export` has the formats and options). The import reads
Campfire's PDFs back, so much here exists to keep the text layer importable.

### Source

- Read once, when the screen opens, each song rendered as the viewer shows it; a missing file keeps its slot. Chords are
  collected whether or not diagrams are asked for, so toggling re-lays out rather than re-reads; none while chords or
  diagrams are off in the app. Degrees follow the Chord degrees switch (no option of their own), printed as on screen;
  one scale walk serves degrees and scales. Scale texts are worded by the screen only while printed, since the layout
  cannot read resources. The setlist date uses the date picker's formatter in the app's language.

### Layout

- `layoutPrintDocument` is **pure**: PDF points, measured by a function it is handed, so tests measure with arithmetic.
- Rows are never split; a heading stays with the first segment that prints anything, a lyric line with its chords, a
  tab system whole. A segment that fits a column is kept whole, except sheet music, which flows a staff system at a
  time. A verbatim run (LilyPond, SVG, an ABC tune that did not render) prints preformatted, only with
  `showSheetMusic`, and never `isSelectable` (read back, ABC's `"G"` and `[CEG]` would be chords).
- An `abc` run prints engraved (a row per staff system): the export screen renders every song's tunes in its own
  transposition and notation, each copy under its own key change, for the column width right before each layout, which
  waits for them, at the size printing its lyrics as large as the song's (smaller in a narrower column). Engravings are
  kept per width (the last two) for the export's source, so a step that keeps the width engraves nothing. A score of
  several parts prints the reader's part where their instrument has one, as the screen shows it, and whole otherwise:
  the PDF has no picker. Drawn in black, the frets' boxes in white, never in the text layer; a tune that does not render
  prints as its text.
- Sections and runs are grouped by the screen's own `joinCutSections` and `groupIntoRuns`: a chorus is one unit under
  one bar, a break the one cut the PDF keeps. Section labels hide when the options hide all their lines; an unnamed
  verse is "Verse", an unnamed paragraph has none.
- A row with markup prints as one text with spans, laid out once so right-to-left lines keep their order. A line with
  any bold wraps measured in bold, so no bold word passes the column. **Bold is written into the text layer regular**:
  read back, a bold chord-name word would become a chord. Likewise a tab run's bold chord line.
- A chorus bar is a rule per row, so it continues across columns and pages. Wrapping never breaks inside a grapheme
  cluster.
- **Not `isSelectable`**, since read back they'd be chords or a lyric: diagram names, the instrument/tuning line above
  diagrams (printed unless guitar or keyboard in standard tuning), and the suggested scales.
- Padding under wide chords is written as no-break spaces and the words' own spaces as spaces, so the import leaves
  the padding out (and drops the direction marks closing in a piece read against its line).
- Tablature is engraved as on screen, none of it selectable; each line reaches the text layer as the file has it, an
  invisible monospaced text sized to the columns on the string's baseline, so it reads back as a tab.
- The playing row **follows the reader's settings as the screen does**: transposition, capo, and tempo are the
  setlist's, else the reader's, else the file's. Key (the inferred one where the file names none), transposition, and
  non-zero capo with `showKey` ("2 (Em shapes)" where chords are named by the shapes, whose key the import reads back as
  the song's; "2 (chords as they sound)" otherwise, read back as the shapes over the same capo; diagrams drawn above the
  printed capo); tempo and time with `showTempo`, which also prints later changes. **Values are three spaces apart**, so
  the importer reads them back one by one. A value too wide for its row wraps.
- `showMetadata` is the song sheets' artist; the running order has `showOverviewDetails` (read as `showMetadata` from a
  file older than it) and `overviewColumns`, its list under a heading spanning the page.

### Direction

- **A song's texts are set in its direction**, the running order's in none. A right-to-left song is laid out from the
  right: texts end at the column's right edge, columns fill from the right (a song turning the direction starts a page
  when there are columns), the chorus is indented and barred on the right, diagrams listed from the right.
- A right-to-left text other than a lyric starts its layout's width before its edge, never its advance: the layout is a
  whole number of points wide with its glyphs at its right, so set by advance a text ends up to a point past the edge,
  and a boxed comment's text less than its 5 points in, by which the import finds the box.
- Music keeps reading left to right: tab, grids, preformatted runs, and engraved tunes move to the right edge as one
  block; degrees and chord names are never set right to left.
- A right-to-left lyric row takes its chords' anchors from the renderer's layout, as the screen finds them; every other
  row is measured by its widths. An overflowing chord starts the next chord line at the row's start, in a chords-only
  row marked as a continuation.
- A right-to-left row is laid out again while a chord would land off its edge (past the margin or into the chord
  before), the missing room added as marked padding on the chord's side: the import reads chords by the exact edge
  they hang from, and a moved one lands a letter or a word off.
- "Key: Am" is put together by a string (`print_label_value`), which a language may space or order otherwise.

### Renderer

- The file is the preview at 216 dpi, same fonts. A script's characters are spanned with its bundled face
  (`scriptFontFamilies`), never left to the platform's fallback, so a PDF is the same on every platform.
- **The layout measures the advance a text is drawn with**
  (`PrintRenderer.width`), never the layout's size, rounded up to a whole point: each padding space's rounding adds up,
  and a chord after a padded lyric would hang a letter late.
- Export reuses one bitmap, read back in bands, checking cancellation between them. Text layouts and the notation
  systems a page draws (painted one system at a time, never a whole tune) are cached by page (the 8 drawn last), never
  by text or tune: a frame's pages always fit, while a frame drawing more texts than a per-text bound misses every one.
- Diagrams draw in black (root mid gray), thinnest line one page pixel.

### Writer

- Each page a 4-bit gray image (16 levels print like 256), compressed by `:data:formats`' `Deflater` (pure Kotlin: no
  platform offers common code a compressor; the zip writer uses it too), wrapped as zlib by `deflateZlib`, which also
  packs the share link's QR PNG.
- Invisible text (`3 Tr`) at the shaped rectangles, with tiny glyphless Type 3 fonts and ToUnicode maps, so exports are
  searchable, copyable, and reimportable without OCR. Runs on one baseline, font, and size share one `TJ` with explicit
  advances, or PDFium reads dense columns as vertical text. Fonts keep bold and monospace classification for import and
  split after 255 clusters.
- A run holding any RTL cluster is wrapped in a `/Span` with `ActualText`, since readers ordering by position would read
  it backwards; the importer honours it.
- No font programs are embedded, so visible glyphs match the preview.
