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

The export formats, the options, and the PDF pipeline behind the export screen (`ui/screens/export`).

## Formats and options

- One export entry per song and setlist (and selection mode's "Export songs") opens the export screen; no separate
  share or file export entry. Formats: **PDF** (for printing) or the library's own files (for Campfire users):
  **ChordPro** for a song (the `.cho` as stored), **Zip** for a setlist (its `*.setlist.json` manifest plus songs) or
  for several selected songs (no manifest).
- A setlist's song ticks apply to both formats. A partial zip's manifest names only the ticked songs
  (`ExportSetlistUseCase`'s `songFileNames`, rest of the document kept), so the archive never names a song the user left
  out; a ticked song missing from the library is still named. All ticked → the stored manifest unchanged.
- `PrintSettings` (format included) are local preferences (`PrintSettingsDocument`), independent of the viewer's text
  size and folded sections.
- **The Features switches reach the export**: an option whose feature is off (chords, which take diagrams and key; the
  metronome, which takes tempo) is neither offered nor printed (`PrintSettings.withinFeatures`), but the choice is kept
  for when the switch comes back.
- A setlist export keeps original slot numbers and each entry's transposition; a song reached through a setlist uses that
  entry's key. Missing or unreadable songs keep a visibly marked slot.
- **Only what is printed goes into a PDF**: the printed key, the chosen songs and options; no original ChordPro or
  excluded metadata. No platform print service: the file is saved or shared.

## Pipeline

### Source

`CampfireViewModel.preparePrintSource` reads a `PrintSource` once, when the screen opens: each song rendered as the
viewer shows it (transposition, chord spelling); a missing/unreadable file is a `PrintSong` with no song, keeping its
slot. `PrintSong.chords` (`printChordsOf`, the reader's instrument) is collected whether or not diagrams are asked for,
so toggling them re-lays out rather than re-reads; none while chords or diagrams are off in the app (which also hides
the option). The setlist date uses the date picker's formatter in the app's language (`calendarLocale`).

### Layout

- `layoutPrintDocument` is **pure**: PDF points, measured by a function it is handed, so tests measure with arithmetic.
  Rows are the unit never split; a heading stays with the first segment that prints anything, a lyric line with its
  chords, a tab system whole, a grid run aligned into columns as the viewer does.
- Sections and runs are grouped by the screen's own `joinCutSections` and `groupIntoRuns` (`ui/songLayout/`):
  a chorus is one unit under one bar, a break the one cut the PDF keeps.
- Section labels: hidden when the options hide all its lines; an unnamed verse is "Verse", an unnamed paragraph has none.
- A chorus bar is a `PrintRule` per row so it continues across columns and pages. `wrapPrintText` never breaks inside a
  grapheme cluster.
- Only strings of ≤8 characters are cached; layout yields after each song and every 50 groups (on the web it shares the
  page's thread).
- Diagram names and the instrument/tuning line above diagrams (`PrintLabels.chordTuning`, printed where not guitar or
  keyboard, or not standard tuning) are **not `isSelectable`**: read back by the importer they'd be chords or a lyric.
- The playing row (`playingDetails`): key, transposition, and non-zero capo with `showKey` (on the keyboard the capo is
  counted into the transposition, as on screen); tempo and time signature with `showTempo`, which also prints later
  changes. **Values are three spaces apart**, which lets the importer read them back one by one. `showMetadata` is the
  artist and a setlist's date and description.

### Renderer

`PrintRenderer` draws the preview and the file and measures for the layout, with the same fonts: the file is the
preview at 216 dpi. Export reuses one bitmap, read back in bands, checking cancellation between them.
`selectableText` takes each cluster's rectangle from the same `TextLayoutResult` (surrogate pairs and combining marks
kept). Diagrams use the screen's `drawChordDiagram` in black (root mid gray), thinnest line one page pixel
(`minimumStroke`).

### Writer

- `PrintPdfWriter`: each page a 4-bit gray image (16 levels print like 256), compressed by
  `PrintDeflater` (pure-Kotlin fixed-Huffman zlib; no platform offers common code a compressor). Carries `/Title` and a
  binary header line.
- Invisible text (`3 Tr`) at the shaped rectangles, with tiny glyphless Type 3 fonts and ToUnicode maps, so exports are
  searchable, copyable, and reimportable without OCR. Runs on one baseline, font,
  and size share one `TJ` with explicit advances, or PDFium reads dense columns as vertical text. Fonts keep bold and
  monospace classification for import and split after 255 clusters.
- A run holding any RTL cluster (`rtlRuns`, the whole run) is wrapped in a `/Span` with `ActualText`, since readers
  ordering by position would read it backwards; the importer honours it.
- No font programs are embedded, so visible glyphs match the preview.
- Check selection and search by hand in Chrome (accents, chords, columns, no duplicate text).

## Tests

`CAMPFIRE_PRINT_QA_DIR` makes the renderer tests save QA PDFs; its `campfire.pdf` is also the document importer's
positioned export/reimport fixture (documented beside that fixture).
