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

Pure-Kotlin PDF and Word text extractors, zip reader/writer, and image header reader, on no third-party library (only
`kotlinx.coroutines`) and no module but `:data:model`, `:libs:chordpro`, and `:libs:chordsheet` (a document is extracted
into its `ChordSheet`): no `FileStorage`, no Koin, no network. Public is only what `:data:source:local:implementation`
uses, the `Deflater` `:presentation` compresses PDFs and PNGs with, and the `ImageFormats` it tells a picture to shrink
or convert by.
Documents are read locally, never rendered, uploaded, or kept.

## `document/`

- Images and font programs are never decoded. `DocxTextExtractor` opens only `word/document.xml` and `word/styles.xml`;
  the XML parser refuses DTDs. Deleted text and external parts are left out.
- **Every limit bounds untrusted input; keep them all**, including the per-document work budget (re-reading a shared
  form or contents stream is paid for), the font-table-entry budget (shared `/W` and `/Differences` arrays and CMaps
  paid for each time), and the syntax budget every object parse of a document shares, the recovery scan's included. Bridging spaces are charged to the text budget, so a tiny monospace font cannot pad a page to hundreds of
  megabytes. A budget running out is `PdfLimitException`, not a malformed object.
- Damage is local: a malformed page, font, or form costs only itself; the document fails only when more than half its
  glyphs or pages do. Budgets, encryption, a broken page tree, and over 2,000 pages fail it whole (a songbook silently
  losing its tail is worse than one reported unreadable).
- A song drawn as pictures under a header of text (a site's export) is unreadable too (`PdfPictureRows`): where images
  are placed, never what they hold, is counted in rows that no baseline crosses, leaving out pictures under 3 pt (rules)
  or over a quarter of the page (a cover, a background, a scan under its text); at least 8 such rows, and more than twice
  the document's lines of text, fail it. A logo, a cover, or a few rows of chord diagram pictures stay far below that.
- Must stay linear: `PdfStreamEnds` finds every `endstream` once per file; at most 1 KiB of whitespace is walked past a
  declared `/Length`.
- Columns: a gutter is a 0.8 em whitespace band crossed by at most a fifth of the lines, with a fifth of the characters
  on each side, counting no line that crosses it (a lyric beside the gap in its chord row is no second column) and
  leaving a crossing first or last line of the page out as well, **found at every half point, not sampled** (since a
  dense layout leaves less than a word's gap). A band no line crosses that opens a column beats one a heading crosses;
  among equally crossed bands, the fewest crossing lines below the top of the ink right of the band win (a key printed
  over the middle of the page is cut, not a column's longest chord row), then the fewest characters crossing (a page
  number under the gutter, not a lyric filled to the edge), then the widest. Each side is searched again (three and four
  columns). A line of the column read second above all of the first, reaching into the gutter more than the band past
  its column's start (a title centred over both), is read before both. A page whose letters mostly read from the right
  is read from its right column. Each line names its column (`ChordSheet.Line.column`), a line crossing the gutter none.
- Running lines (`PdfRunningLines`) are judged on margins laid out across the whole width, never by the columns: a short
  last page can read as columns and cut its header apart. A dropped margin line takes its whole baseline. A number in a
  running line is the same on every page or counts the pages, so a heading opening two pages (`Verse 2`, `Verse 6`)
  stays, as does one counting no pages over the same body line on each (a chorus printed again); a page count stands
  alone (`Page 1 of 2`), never a bare `1/2`, which a time signature also reads as. A page's lowest line counting this
  page (`2 / 3`) is dropped anywhere in the lower half (a wide margin lifts it), as is a lowest line outside the margins
  in the lower half that is the same at the same place on every page (an album and its year).
- A span is italic by its font's name or italic flag; a line carries its baseline.
- Chord diagrams (`PdfChordDiagrams`) are left out before the lines are set: a run of rows in type under 0.85 of the
  song's text holding both chord names and a row of at least two marks (fret and finger numbers, `x`, `o`, a lone
  symbol). Names alone stay (a small chord row over its lyric). A one-mark row joins a run (a barre's position) but does
  not make one, so a page number `3` under a small chord-only outro does not take it along; `&`, `+`, `-`, `/`, `|`, and
  dashes are not marks, so a count row (`1 & 2 &`) or a footer (`- 3 -`) ends a run. A small chord row over a count row
  of bare digits (`1 2 3 4`) is still taken for a diagram: nothing in the glyphs tells it from one.
- A blank line is a gap over 2.2 em, or one 1.4 times the document's usual step (`PdfParagraphs`: a quantile above the
  median, since a chord row sits closer to its lyric than to the lyric before it) between runs of three lines or more on
  each side, so a tightly set songbook's stanzas part while a header's credits and a diagram's name over its frets stay.
- An `ActualText` over glyphs on one baseline replaces them (how Campfire's export carries right-to-left runs in logical
  order); the glyphs stay as its `pieces` where they spell its text in order, so the converter still knows where each
  character is drawn. One spanning several baselines (a hyphenated word) is ignored.
- Other producers draw right-to-left text glyph by glyph from the left, so `PdfBidi` reorders such a line (a subset of
  UAX #9, `:libs:chordpro`'s `BidiOrder`, runs by `isStrongRtl`) into one span with `pieces`; a line with an
  `ActualText` is left alone. A line's direction is the side of its column it aligns to, else its column's letters,
  chord rows left out. A mirrored bracket is read as drawn (ReportLab) or as meant (Chrome), whichever pairs the line's
  brackets; a mark goes with the letter under it.
- Page, operator, and work yields keep the web build responsive.

## `zip/`

Exists because no multiplatform zip library covers wasmJs. Reads STORED and DEFLATE (`Inflater` follows `puff.c`);
encrypted or otherwise compressed entries are left out. Only the streaming reader reads ZIP64; the in-memory one refuses
it, as the reference the other is held to.

- **`ZipWriter` deflates each entry where that makes it smaller, else stores it** (decided by trying, never by name),
  with `Deflater`'s fixed Huffman codes. Every Campfire version reads both. The output depends on the entries alone,
  since a shared copy is rebuilt from the library's files and must come out as the same bytes. An archive is smaller
  than what it unpacks to, so whether an import would take an export is judged by its entries' sizes
  (`ExportedFile.unpackedSize`).
- **ZIP64 is written only past what the 32-bit format holds**, so an archive within it is byte for byte what it was
  before (`ZipWriterStreamingTest`'s and `ZipRoundTripTest`'s digests): an entry whose local header starts at or past
  `0xFFFFFFFF` gets its offset in a central `0x0001` field and version 45 (sizes never need one, an entry being one
  `ByteArray`); 65,535 entries or more, a central directory starting at or past `0xFFFFFFFF`, or an archive that would end
  past it add the ZIP64 record and locator, the end record's overflowing fields at their markers. An archive merely
  ending past 4 GiB needs them too, or the reader takes it for a wrapped one. Only a name over 65,535 bytes is refused.
  The tests lower the limits through `write`'s internal overload, never production's.
- **The writer streams into a `ZipSink`** (a file for an export, a buffer for the in-memory `write`, which is the same
  bytes): each entry is read (name and bytes, or null to leave it out), deflated, and written before the next is read, its header after its CRC and
  sizes are known (no data descriptors), so it holds one entry and the central directory's names and offsets, never the
  archive, nor the directory itself, which goes out 64 KiB at a time.
  An entry over 1 MiB is first tried on its first 64 KiB: one that does not shrink by 3% is stored undeflated, one that
  does is deflated into a buffer of its own size, given up on once no smaller (`LargeFileMemoryTest`, in a 128 MB JVM,
  is the proof); entries up to 1 MiB come out bit for bit as before. Data reaches the sink in slices of at most 1 MiB.
- **Declared sizes are only a first guess.** Entries are judged by name and declared size before inflating
  (`EntryPolicy`), or under an uninformative name by a 512-byte peek (`Sniff`); a declined peek costs no budget. The
  caller's import budget is charged before an entry is read; inflation stops at the declared size. Each entry's
  compressed bytes are charged once, and may not add up to more than the archive holds (zip bombs pointing many entries
  at the same bytes). The buffer starts at no more than 1 MiB whatever the directory claims.
- **A declared size the data cannot be is a damaged entry** (`ZipDirectory.canProduceDeclaredSize`): a stored entry is
  its compressed bytes, and no deflate stream inflates past `MAX_DEFLATE_RATIO` (1032, two bits per 258-byte match;
  zlib's best over zeros stays under it, `DeflateRatioJvmTest`). The walk charges such an entry (`onCharged`) and leaves
  it out unreadable without asking for a sink, where the in-memory reader reads it and finds it short, so nothing is set
  aside for a lie.
- **Two readers that read every archive alike**: `ZipReader.read(ByteArray)` in memory, and `ZipReader.open(ZipSource)`
  with `ZipDirectory.walk`, which never holds an archive or an entry whole unless its `EntrySink` does. The walk is the
  in-memory one decision for decision (same policies asked in the same order, same peeks, charges, and reasons), which
  `readAlike` and the fuzz test hold it to; keep them alike until the in-memory one is removed. Its own additions:
  `MAX_CENTRAL_DIRECTORY_SIZE` and `MAX_ARCHIVE_ENTRIES` refused before anything is allocated, the directory kept as
  primitive arrays and name bytes, each local header read once, and every sum of declared numbers through `checkedSum`.
  A `ZipDirectory` says what it cost of both caps (`centralDirectorySize`, `recordCount`), so a caller can carry what
  is left of them across nested archives.
  `ByteArrayEntrySink` refuses an entry over 50 MiB for memory's sake, stored ones too.
- **ZIP64 follows what real writers write** (`RealWriterArchives`' KDoc says what each wrote). The ZIP64 record's
  values win over stale 32-bit ones; only the end record's own disk fields are checked (Explorer and OneDrive write wrong
  ones in the others). An end record needing ZIP64 without a record is damaged; one needing none reads as it always did
  however broken its ZIP64 record, so no archive the in-memory reader reads reads differently. A central header's marker
  without its `0x0001` value leaves the entry out, as every marked entry was; local headers' ZIP64 fields and data
  descriptors are never read. A ZIP64 entry's data ends before the central directory. A 64-bit value with its top bit
  set is refused, and per-entry caps and the budget apply to 64-bit sizes unchanged.
- **A file past 4 GiB with no ZIP64 locator is `WrappedArchiveException`**, a public `ZipException` a caller tells apart
  from a damaged archive: its tool wrapped every size and offset around, so it is refused, never recovered.
- `ZipDirectory.walk` reads an entry of any 64-bit size into its sink; `ZipReader.read(source)`, which holds entries in
  memory, leaves out one of 2 GiB or more, as the in-memory reader does.
- **The streaming inflater** (`StreamingInflater.kt`) is `puff.c`'s structure again over a `ZipSource` range, with a
  64 KiB input buffer and a 64 KiB output window that slides to keep 32 KiB of history; only its block loops suspend,
  topping up between symbols. `Inflater.inflate(ByteArray)` stays as it is (the PDF reader's, and the reference the
  fuzz test holds the streaming one to: the same bytes or a `ZipException` from both). Output goes to an `EntrySink`
  through `EntrySink.receive`, which holds it to the declared size, takes its CRC-32 and SHA-256, yields every 256 KiB,
  and ends the sink exactly once: `close` with the SHA-256, or `abandon` on any failure or cancellation.
- An unreadable entry is left out and reported by name (the module's one print, in each reader); only an unwalkable
  archive is a `ZipException`.
- Entry names are UTF-8 where flagged or valid (macOS does not flag), else code page 437. A backslash is a separator too
  (Windows PowerShell 5.1).

## `image/`

Layout reserves a picture's room before decoding it, so `ImageInfo.of` reads only the header, never past a file's first
64 KiB (a JPEG frame header behind more metadata is not read). The size is as displayed: JPEG EXIF orientation 5–8
swaps the sides. A side over 32,768 is refused, so a lying header cannot reserve a huge box, and so is a picture over 25
megapixels (`MAX_PIXELS`, `isOverPixelLimit` telling it apart): Skia (desktop, iOS, web) decodes a whole picture before
scaling it, and a megabyte of flat PNG can claim a gigabyte decoded. Every size the app draws by comes from here.

## Tests

End-to-end document goldens live in `:data:source:local:implementation`. An extraction change is judged by the same
import corpus as a converter change (`chordpro/convert/CLAUDE.md`).

The `*FuzzTest`s damage valid documents and archives from fixed seeds (`zip/Mutator`): a reader may refuse one only with
an `Exception` (a `ZipException` for the zip reader), never an `Error`, nor read past its budget; raise the counts to
explore, never in a commit. A hang a hostile layout causes is tested against a sixty-fourth of its own input
(`assertTakesTimeInProportion`, the inputs built before the clock starts), never a fixed time. Archives past 4 GiB are
read through `SparseZipSource` (headers at their offsets, zeroes between), so CI never writes gigabytes.
