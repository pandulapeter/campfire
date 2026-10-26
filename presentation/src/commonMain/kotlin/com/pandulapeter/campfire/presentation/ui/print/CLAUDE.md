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
the option). Each carries the screen's degree (`degreeKeyOf`, with the Chord degrees switch, no option of its own),
printed as a numbering's letters are, in the detail style on a line over the name, as on screen; one scale walk
(`printScaleAnalysisOf`) serves the degrees and the scales. `PrintSong.scaleGroups` (`printScaleGroupsOf`, the song details screen's Scales section) likewise, put in
words by the screen (`rememberPrintScales`) only while printed, since the layout cannot read resources. The setlist date uses the date picker's formatter in the app's language (`calendarLocale`).

### Layout

- `layoutPrintDocument` is **pure**: PDF points, measured by a function it is handed, so tests measure with arithmetic.
  Rows are the unit never split; a heading stays with the first segment that prints anything, a lyric line with its
  chords, a tab system whole, a grid run aligned into columns as the viewer does.
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
- The playing row (`playingDetails`): the sounding key, the reader's transposition, and non-zero capo with `showKey`
  (none on the keyboard, as on screen; diagrams drawn above the printed capo, the setlist's else the file's); tempo and time signature with `showTempo`, which also prints later
  changes. **Values are three spaces apart**, which lets the importer read them back one by one. `showMetadata` is the
  song sheets' artist; the running order has its own: `showOverviewDetails` (the setlist's date and description and its
  songs' artists, read as `showMetadata` from a file older than it) and `overviewColumns`, its song list flowing in them
  under a heading that spans the page.

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
