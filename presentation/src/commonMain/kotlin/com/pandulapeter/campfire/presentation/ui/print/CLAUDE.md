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

The PDF pipeline behind the export screen (`ui/screens/export`, see its notes).

## Export

The PDF pipeline has four steps, each its own:

- **Source.** `CampfireViewModel.preparePrintSource` reads a `PrintSource` once, when the screen opens: every song as the
  viewer reads it (rendered, in the transposition and chord spelling the song details screen shows), a missing or
  unreadable file as a `PrintSong` with no song, which keeps its slot. The screen leaves out the songs that are unticked
  and adds the setlist's date, formatted with the date picker's formatter in the app's language (`calendarLocale`). Each song also carries its chords (`PrintSong.chords`, `printChordsOf`: `songChordsOf` and `selectShape`
  turned into `ChordDiagramGeometry`, the song details screen's Chords section on the reader's instrument), collected
  whether or not they are asked for, so that the Chord diagrams box lays out again rather than reading again, and none
  at all while the chords or the diagrams are switched off in the app, which is also what hides the box.
- **Layout.** `layoutPrintDocument` is pure: a `PrintDocument` of `PrintText`s and `PrintRule`s in PDF points, measured
  by a function it is handed, so the tests measure with arithmetic. A private `PrintLayouter` holds the cursor and
  `place`s rows, the unit it never splits; one function per kind of block or line builds them (`lyricsRows`, `tabRows`,
  `gridRows`, `commentRows`, `recallRows`). A heading is kept with the first block that prints anything, a lyric line
  with its chords, a tab system whole with a gap after it, a grid run aligned into columns as the viewer aligns it and each line broken between bars; a section whose lines the
  options all hide leaves out its label, an unnamed verse is headed "Verse" as in the viewer while an unnamed paragraph is not headed (the viewer's chevron-only pill has nothing to do on a page), a chorus recall carries its label. Every text
  has a `PrintStyle`: lyrics in the app's text font, tablature and grids monospace, details and labels smaller and gray,
  annotations and comments italic, a boxed comment framed by rules. A chorus is indented behind a bar, drawn as a
  `PrintRule` on each of its rows so that it carries on across a column or page. Wrapping (`wrapPrintText`) breaks at a
  word where the next word fits and never inside a grapheme cluster. Only strings of up to eight characters are cached,
  and the layout yields after each song and every 50 placed blocks: on the web it shares the page's one thread. With `PrintSettings.showChordDiagrams` and the chords, a song's diagrams follow its heading as rows of cells
  sized by the text size (the chord's name over its diagram, a `PrintDiagram` in the page), kept with the heading the
  way a first block is; a name and its second name too wide for the column together are set on two lines, and one wider
  than the column on its own is broken across lines like any other text; their names are `PrintText`s that are not `isSelectable`, since read back by the importer they
  would be a line of chords above the song. Under the heading a row of what the song names of how it is played (`playingDetails`): the key, the
  `PrintSong.transposition` the viewer prints it in and a capo other than 0 with `showKey`, and the tempo (through
  `PrintLabels.tempoValue`, "96 BPM") and the time signature with `showTempo`, which also prints a change of either
  further down; `showMetadata` is the artist and a setlist's date and description alone. The values are set three
  spaces apart, which is what lets the importer read them back one by one. The screen lays out
  `PrintSettings.withinFeatures` rather than the options as chosen, and offers no option whose feature is off.
- **Renderer.** `PrintRenderer` draws a page for the preview and for the file alike, and measures for the layout with the
  same fonts, so the file is the preview at 216 dpi. An export draws every page into one reused bitmap and
  reads it back in bands of rows, checking for cancellation between them. `selectableText` takes each shaped character
  or cluster's selection rectangle from that same `TextLayoutResult`, preserving surrogate pairs and combining
  characters. Layout-only zero-width wrap opportunities are omitted and non-breaking padding copies as ordinary spaces. Diagrams are drawn by the screen's own `drawChordDiagram` (`components/ChordDiagram.kt`) in black, with the
  root in a mid gray in place of the accent, and their thinnest line one pixel of the page image (`minimumStroke`, `1 /
  scale`) rather than one point, which would be three of them.
- **Writer.** `PrintPdfWriter` turns each page into a 4-bit gray image (luminance rounded to sixteen levels, which print
  no differently from 256), compressed by `PrintDeflater`, a pure-Kotlin zlib encoder of one fixed-Huffman block, since
  no platform offers common code a compressor. The streams go straight into one growing buffer, and the file carries a
  `/Title` and a binary header line. Each page also carries invisible text (`3 Tr`) at the shaped rectangles, backed
  by tiny Type 3 fonts with empty glyph procedures, measured widths and ToUnicode maps. Consecutive shaped runs on one
  baseline, in one font and size and moving left to right, share one `TJ` text object with explicit advance adjustments, so dense columns do not look like vertical text to PDFium. Fonts preserve bold and
  monospace classification for import and split after 255 distinct clusters. A shaped run holding any right-to-left
  cluster (`rtlRuns`, the whole run, so a number or a Latin word inside Hebrew stays in it) is wrapped in a `/Span`
  with its logical text as `ActualText`, since its clusters move leftwards and a reader ordering text by position would
  read it backwards; the importer honours that. Content streams are compressed too;
  no display fonts or font programs are embedded, and no visible glyph can differ from the preview.

Only what is printed is included: the printed key, selected songs and visible options, not an attachment of the
original source or excluded metadata. Old image-only exports still require external OCR. There is no platform print
service: the file is saved or shared and printed from there. Browser selection/search should be checked in Chrome,
particularly accents, chords, columns and the absence of duplicate OCR text; engine checks alone are not UI checks.

The tests pin the layout (`PrintLayoutTest`: columns, keeping together, styles, wrapping, the measurement counts), the
file name, the writer's cross-reference offsets, Unicode maps, font splitting and gray packing, and the deflater
(`PrintDeflaterTest`, and a round trip through `java.util.zip.Inflater` in `desktopTest`, where `PrintRendererTest`
also draws real pages and checks selection rectangles). `CAMPFIRE_PRINT_QA_DIR` makes renderer tests save QA PDFs;
its `campfire.pdf` is also the document importer's positioned export/reimport fixture, documented beside that fixture.
