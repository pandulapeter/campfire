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

The PDF pipeline behind the export screen (`ui/screens/export`, which has the formats and options).

## Pipeline

### Source

`ExportController.preparePrintSource` reads a `PrintSource` once, when the screen opens: each song rendered as the
viewer shows it (transposition, chord spelling); a missing/unreadable file is a `PrintSong` with no song, keeping its
slot. `PrintSong.chords` (`printChordsOf`, the reader's instrument) is collected whether or not diagrams are asked for,
so toggling them re-lays out rather than re-reads; none while chords or diagrams are off in the app (which also hides
the option). Each carries the screen's degree and key group (`chordKeysOf`, the degree with the Chord degrees switch, no option of its own),
printed as a numbering's letters are, in the detail style on a line over the name, as on screen; one scale walk
(`printScaleAnalysisOf`) serves the degrees and the scales. `PrintSong.scaleGroups` (`printScaleGroupsOf`, the song details screen's Scales section) likewise, put in
words by the screen (`rememberPrintScales`) only while printed, since the layout cannot read resources. The setlist date uses the date picker's formatter in the app's language (`calendarLocale`).

### Layout

- `layoutPrintDocument` is **pure**: PDF points, measured by a function it is handed, so tests measure with arithmetic.
  Rows are the unit never split; a heading stays with the first segment that prints anything, a lyric line with its
  chords, a tab system whole, a grid run aligned into columns as the viewer does. A segment that fits a column is kept
  whole, except one holding sheet music, which flows on from where it starts a staff system at a time. A verbatim run (LilyPond, SVG, an ABC
  tune that did not render) prints preformatted like a tab without a staff, only with `showSheetMusic`, and never `isSelectable` (read back, ABC's
  `"G"` and `[CEG]` would be chords).
- An `abc` run prints engraved where `printNotationsOf` rendered it (`PrintNotation`, a row per staff system): the
  export screen renders every song's tunes in its own transposition and notation (`PrintSong.notationChords`), each
  copy of a tune under its own key change (`AbcTune`, each group's from `groupStartOffsetsOf`), for the column width
  (`printColumnWidth`) right before each layout, which waits for them, at the size that prints its lyrics as large as
  the song's (`printNotationUnit`), smaller where the column is narrower (a chorus). Drawn by the screen's
  `drawNotationSystem` in black, never in the text layer; a tune that does not render prints as its text.
- Sections and runs are grouped by the screen's own `joinCutSections` and `groupIntoRuns` (`ui/songLayout/`):
  a chorus is one unit under one bar, a break the one cut the PDF keeps.
- Section labels: hidden when the options hide all its lines; an unnamed verse is "Verse", an unnamed paragraph has none.
- A lyric row or a comment with markup prints as one text carrying its markup as spans, laid out once so right-to-left
  lines keep their order; the text layer keeps the text's own style. A line with any bold wraps measured in bold, so no
  bold word passes the column.
- A chorus bar is a `PrintRule` per row so it continues across columns and pages. `wrapPrintText` never breaks inside a
  grapheme cluster.
- Diagram names and the instrument/tuning line above diagrams (`PrintLabels.chordTuning`, printed where not guitar or
  keyboard, or not standard tuning) and the suggested scales under the diagrams are **not `isSelectable`**: read back by the importer they'd be chords or a lyric.
- Markup's bold is drawn but written into the text layer in the regular weight: read back, a bold word that is a chord
  name would become a chord.
- Lyrics are padded under wide chords with marked runs (`padLyricsToFitChords`' `marksPadding`): the text layer writes
  the padding as no-break spaces and the words' own spaces as spaces, so the import leaves the padding out, and drops
  the direction marks closing in a piece read against its line.
- Tablature is engraved as on screen (`PrintTabEngraver`, see `ui/songView`): figures and rules, none of it
  selectable; each line reaches the text layer as the file has it, an invisible monospaced text (`isDrawn` false) sized
  to the columns and on the string's baseline, so it reads back as a tab. A tab run's chord line prints bold, written
  regular the same way.
- The playing row (`playingDetails`) **follows the reader's settings as the screen does**: transposition, capo, and
  tempo are the setlist's, else the reader's own, else the file's. The sounding key (the inferred one where the file
  names none, as on screen), the reader's transposition, and non-zero capo with `showKey` (none on the keyboard, as on
  screen; diagrams drawn above the printed capo); tempo and time signature with `showTempo`, which also prints later changes. **Values are three spaces apart**, which lets the importer read them back one by one. `showMetadata` is the
  song sheets' artist; the running order has its own: `showOverviewDetails` (the setlist's date and description and its
  songs' artists, read as `showMetadata` from a file older than it) and `overviewColumns`, its song list flowing in them
  under a heading that spans the page.

### Direction

- **A song's texts are set in its direction** (`PrintStyle.direction`, from `ChordProSong.textDirection`), the running
  order's in none. A right-to-left song is laid out from the right: its texts end at the column's right edge, its
  columns fill from the right (a song turning the direction starts a page when there are columns), its chorus is
  indented and barred on the right, and its diagrams are listed from the right.
- Music keeps reading left to right: tab systems, grids, and preformatted runs move to the right edge as one block, an
  engraved tune too, and degrees and chord names are never set right to left.
- A lyric row that is right to left or holds such text takes its chords' anchors from the renderer's layout
  (`PrintRenderer.lyricLayout`, as `SongLineWithChords` finds them) and places them with `printChordLeft`; every other
  row is measured by its widths. An overflowing chord starts the next chord line at the row's starting edge.
- A right-to-left row is laid out again while `printChordLeft` would move a chord off its edge (past the right margin,
  or into the chord before it), with the missing room added as marked padding where it opens on the chord's side
  (`PrintLyricRows`): the import reads Campfire's chords by the exact edge they hang from, and a moved one lands a
  letter or a word off.
- "Key: Am" and the other values are put after their labels by a string (`print_label_value`), which a language may
  space or order otherwise.

### Renderer

`PrintRenderer` draws the preview and the file and measures for the layout, with the same fonts: the file is the
preview at 216 dpi. Export reuses one bitmap, read back in bands, checking cancellation between them. Text layouts
are cached by page (`PrintPageCache`, the 8 pages drawn last, matched by identity), never by text: a frame's pages
always fit, while a frame drawing more texts than a per-text bound misses every one of them.
`selectableText` takes each cluster's rectangle from the same `TextLayoutResult` (surrogate pairs and combining marks
kept). Diagrams use the screen's `drawChordDiagram` in black (root mid gray), thinnest line one page pixel
(`minimumStroke`).

### Writer

- `PrintPdfWriter`: each page a 4-bit gray image (16 levels print like 256), compressed by
  `PrintDeflater` (pure-Kotlin fixed-Huffman zlib; no platform offers common code a compressor). Carries `/Title` and a
  binary header line. `PrintDeflater` also compresses the share link's QR code PNG (`qrCodePng`), whose chunks take
  `crc32` from `Checksums.kt`.
- Invisible text (`3 Tr`) at the shaped rectangles, with tiny glyphless Type 3 fonts and ToUnicode maps, so exports are
  searchable, copyable, and reimportable without OCR. Runs on one baseline, font,
  and size share one `TJ` with explicit advances, or PDFium reads dense columns as vertical text. Fonts keep bold and
  monospace classification for import and split after 255 clusters.
- A run holding any RTL cluster (`rtlRuns`, the whole run) is wrapped in a `/Span` with `ActualText`, since readers
  ordering by position would read it backwards; the importer honours it.
- No font programs are embedded, so visible glyphs match the preview.
