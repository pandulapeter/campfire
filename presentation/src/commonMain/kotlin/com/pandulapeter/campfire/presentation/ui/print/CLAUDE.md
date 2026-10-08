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

### Source

**Source.** `CampfireViewModel.preparePrintSource` reads a `PrintSource` once, when the screen opens: every song as the
viewer reads it (rendered, in the transposition and chord spelling the song details screen shows), a missing or
unreadable file as a `PrintSong` with no song, which keeps its slot. The screen leaves out the songs that are unticked
and adds the setlist's date, formatted with the date picker's formatter in the app's language (`calendarLocale`). Each
song also carries its chords (`PrintSong.chords`, `printChordsOf`: `songChordsOf` and `selectShape` turned into
`ChordDiagramGeometry`, the song details screen's Chords section on the reader's instrument), collected whether or not
they are asked for, so that the Chord diagrams box lays out again rather than reading again, and none at all while the
chords or the diagrams are switched off in the app, which is also what hides the box.

### Layout

**Layout.** `layoutPrintDocument` is pure: a `PrintDocument` of `PrintText`s and `PrintRule`s in PDF points, measured by
a function it is handed, so the tests measure with arithmetic. A private `PrintLayouter` holds the cursor and `place`s
rows, the unit it never splits; one function per kind of block or line builds them (`lyricsRows`, `tabRows`, `gridRows`,
`commentRows`, `recallRows`). A heading is kept with the first block that prints anything, a lyric line with its chords,
a tab system whole with a gap after it, a grid run aligned into columns as the viewer aligns it and each line broken
between bars; a section whose lines the options all hide leaves out its label, an unnamed verse is headed "Verse" as in
the viewer while an unnamed paragraph is not headed (the viewer's chevron-only pill has nothing to do on a page), a
chorus recall carries its label.

Every text has a `PrintStyle`: lyrics in the app's text font, tablature and grids monospace, details and labels smaller
and gray, annotations and comments italic, a boxed comment framed by rules. A chorus is indented behind a bar, drawn as
a `PrintRule` on each of its rows so that it carries on across a column or page. Wrapping (`wrapPrintText`) breaks at a
word where the next word fits and never inside a grapheme cluster. Only strings of up to eight characters are cached,
and the layout yields after each song and every 50 placed blocks: on the web it shares the page's one thread. With
`PrintSettings.showChordDiagrams` and the chords, a song's diagrams follow its heading as rows of cells sized by the
text size (the chord's name over its diagram, a `PrintDiagram` in the page), kept with the heading the way a first block
is; a name and its second name too wide for the column together are set on two lines, and one wider than the column on
its own is broken across lines like any other text; their names are `PrintText`s that are not `isSelectable`, since read
back by the importer they would be a line of chords above the song.

Under the heading a row of what the song names of how it is played (`playingDetails`): the key, the
`PrintSong.transposition` the viewer prints it in and a capo other than 0 with `showKey`, and the tempo (through
`PrintLabels.tempoValue`, "96 BPM") and the time signature with `showTempo`, which also prints a change of either
further down; `showMetadata` is the artist and a setlist's date and description alone. The values are set three spaces
apart, which is what lets the importer read them back one by one. The screen lays out `PrintSettings.withinFeatures`
rather than the options as chosen, and offers no option whose feature is off.

### Renderer

**Renderer.** `PrintRenderer` draws a page for the preview and for the file alike, and measures for the layout with the
same fonts, so the file is the preview at 216 dpi. An export draws every page into one reused bitmap and reads it back
in bands of rows, checking for cancellation between them. `selectableText` takes each shaped character or cluster's
selection rectangle from that same `TextLayoutResult`, preserving surrogate pairs and combining characters. Layout-only
zero-width wrap opportunities are omitted and non-breaking padding copies as ordinary spaces. Diagrams are drawn by the
screen's own `drawChordDiagram` (`components/ChordDiagram.kt`) in black, with the root in a mid gray in place of the
accent, and their thinnest line one pixel of the page image (`minimumStroke`, `1 / scale`) rather than one point, which
would be three of them.

### Writer

**Writer.** `PrintPdfWriter` turns each page into a 4-bit gray image (luminance rounded to sixteen levels, which print
no differently from 256), compressed by `PrintDeflater`, a pure-Kotlin zlib encoder of one fixed-Huffman block, since no
platform offers common code a compressor. The streams go straight into one growing buffer, and the file carries a
`/Title` and a binary header line. Each page also carries invisible text (`3 Tr`) at the shaped rectangles, backed by
tiny Type 3 fonts with empty glyph procedures, measured widths and ToUnicode maps. Consecutive shaped runs on one
baseline, in one font and size and moving left to right, share one `TJ` text object with explicit advance adjustments,
so dense columns do not look like vertical text to PDFium. Fonts preserve bold and monospace classification for import
and split after 255 distinct clusters. A shaped run holding any right-to-left cluster (`rtlRuns`, the whole run, so a
number or a Latin word inside Hebrew stays in it) is wrapped in a `/Span` with its logical text as `ActualText`, since
its clusters move leftwards and a reader ordering text by position would read it backwards; the importer honours that.
Content streams are compressed too; no display fonts or font programs are embedded, and no visible glyph can differ from
the preview.

Only what is printed is included: the printed key, selected songs and visible options, not an attachment of the original
source or excluded metadata. Old image-only exports still require external OCR. There is no platform print service: the
file is saved or shared and printed from there. Browser selection/search should be checked in Chrome, particularly
accents, chords, columns and the absence of duplicate OCR text; engine checks alone are not UI checks.

The tests pin the layout (`PrintLayoutTest`: columns, keeping together, styles, wrapping, the measurement counts), the
file name, the writer's cross-reference offsets, Unicode maps, font splitting and gray packing, and the deflater
(`PrintDeflaterTest`, and a round trip through `java.util.zip.Inflater` in `desktopTest`, where `PrintRendererTest` also
draws real pages and checks selection rectangles). `CAMPFIRE_PRINT_QA_DIR` makes renderer tests save QA PDFs; its
`campfire.pdf` is also the document importer's positioned export/reimport fixture, documented beside that fixture.

## Printing

Song and setlist action menus have one export entry each (**Export song**, **Export setlist**), which opens the export
screen titled the same (`presentation/ui/screens/export/ExportScreen.kt`), full screen over the app with Save as its
floating action button (on a phone the options end above it and the preview is their first item, scrolling away with
them; from 520dp of width it stands beside them) and, on Android and iOS, Share in the app bar: there is no separate
share or file export entry. Its first option is the format, with a line saying what each is for — a **PDF**, for
printing, or the library's own files, for sharing with other Campfire users: **ChordPro** for a song, the `.cho` file as
the library holds it, and **Zip** for a setlist, a setlist manifest (its `*.setlist.json`) next to its songs as ChordPro
files.

A setlist's songs are ticked off for either format alike, and a zip of only some of them carries a manifest naming only
those (`ExportSetlistUseCase`'s `songFileNames`, everything else in the document kept), so that the archive never names
a song the user left out — a ticked song whose file is missing from the library is still named, and an import shows it
as a missing song, as the setlist itself did; with all of them ticked the manifest is the stored file unchanged. The
library's own files take no other option and are previewed as the text or the files they write. The rest of this section
is the PDF. `PrintSettings` (the format among them) are local user preferences, mapped through `PrintSettingsDocument`,
saved once the options have settled and whatever way the screen closes, independent of the viewer's text size and folded
sections. A setlist can export its running order or the selected song sheets, retaining the original slot numbers and
the transposition of each entry; a lone song reached through a setlist uses that entry's key too.

Under each song's heading a row says how it is played — the key it is printed in, the transposition that took it there
and the capo, then the tempo in BPM and the time signature — each half an option of its own (**Key, transposition and
capo**, **Tempo and time signature**) beside the chords, the chord diagrams, the comments and the artist. **The Features
tab reaches the export screen**: an option whose feature is switched off (the chords, which take the chord diagrams and
the key with them, or the metronome, which takes the tempo) is not offered and not printed, whatever was chosen before
(`PrintSettings.withinFeatures`), the choice itself kept for the switch to be turned back on. Missing or unreadable
songs retain a visibly marked place. The source is a snapshot read when the screen opens.
`presentation/ui/print/PrintLayout.kt` lays out PDF points using the same font measurements as the preview — lyrics in
the app's text font, tablature and grids in its monospace one — keeping lyric/chord pairs and guitar systems together,
and flowing long songs across columns and pages.

`PrintRenderer` draws both the preview and the page images (216 dpi, sixteen grays, which print no differently from 256)
embedded by the common `PrintPdfWriter`, which titles the file after the song or setlist, compressed with Flate by
`PrintDeflater`, a small pure-Kotlin zlib encoder. These PDFs retain those page images and add invisible selectable text
positioned from the same Compose shaping, with small glyphless Type 3 fonts and explicit ToUnicode maps. Only the
printed content is included, in its printed key and with the chosen options; no original ChordPro or excluded metadata
is embedded. New exports can be searched, copied and reimported without OCR, a right-to-left run in its logical order
through `ActualText`; older image-only exports remain unreadable to the importer. Saving uses the existing `FilePicker`
on every platform, and Android and iOS offer Share in the app bar; the save button counts the pages as they are drawn,
and is Cancel until the picker is up. The file is named from the song's header or the setlist's title, the way
`ExportFileNames.kt` names a song (see `domain/implementation/CLAUDE.md`). New controls and text written into PDFs are
localized in both languages.
