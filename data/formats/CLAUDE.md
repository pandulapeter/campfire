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

The pure, dependency-free codecs the import and the export read and write documents with: the PDF and Word text
extractors and the zip reader and writer, in common Kotlin on all five targets, with no `FileStorage`, no Koin and no
network. Depends on `:data:model` (`ExtractedDocument`, `ImportLimits`) and `:chordpro` (`ChordSheetConverter.isChordLine`,
for the Word reader); consumed only by `:data:source:local:implementation`'s `DocumentLocalSourceImpl` and
`ArchiveLocalSourceImpl`. Public is only what those two use — `PdfTextExtractor`, `DocxTextExtractor`, `ZipReader`,
`ZipWriter`, `ZipEntry`, `ZipContent`, `UnreadZipEntry`, `DosTimestamp`; everything else is `internal`, the tests being
in the same module. `ZipReader`, an object with no constructor, is the one place here that prints, the name of an entry
it could not read.

- **`document/`** — bounded, text-only PDF and Word readers in common Kotlin, with no platform parser or network.
  The output is `ExtractedDocument`
  with pages, lines and positioned/style-bearing spans, never the original bytes in storage. `DocxTextExtractor`
  opens only `word/document.xml` and `word/styles.xml` through `ZipReader`; the bounded XML parser refuses DTDs,
  supports entities and CDATA, and the walker reads paragraphs, tabs, styles, page breaks and chord-grid/layout
  tables while leaving deleted text and external parts out. Proportional positions are estimates. The PDF reader
  supports classic/stream xrefs, object streams, incremental saves and recovery scans, Flate/ASCIIHex/ASCII85/LZW
  filters and predictors, inherited page resources, horizontal text operators and form XObjects. Fonts use
  ToUnicode maps or WinAnsi/MacRoman/Standard encodings and Adobe glyph names; images and font programs are never
  decoded. `PdfTextExtractor` walks the page tree, `PdfContentInterpreter` runs a page's content against the
  document's one `PdfContentBudget`, `PdfLineLayout` sets the glyphs into lines and columns and `PdfRunningLines` finds
  the repeated headers, footers and page numbers. Positioned glyphs form lines and columns, with paragraph gaps and repeated edge furniture removed. A gutter
  is a whitespace band 0.8 em wide that at most a fifth of the lines cross (a title over the columns may), found at
  every half point rather than sampled, since Campfire's own export leaves only 18 points at its 20-point text; each
  side is searched again, which is what reads three and four columns, and a band nothing crosses splits off a short
  last column whose lines start at one edge.
  Object, recursion, stream, page, operator, glyph, output and XML event (600,000, some 21,000 Word paragraphs, which
  also bounds the element tree at half that) limits bound untrusted input, and one work budget of 96 MiB of
  interpreted content and of stream input per document, so re-reading a form or a shared contents stream is paid for, and
  one of 2^20 font table entries (widths and ToUnicode mappings, which stay in memory with the cached fonts), so thousands
  of font dictionaries naming one shared `/W` array or CMap are paid for too; a CID font whose `/W` ranges expand to more
  than 131,072 widths (twice the codes it can have, so only overlapping ranges get there) is a malformed font. A
  budget running out is a `PdfLimitException`, told apart from a malformed object. A malformed page, font or form costs only
  itself, and the document is unreadable only when more than half of its glyphs or pages are; budgets, encryption, a
  broken page tree and more than 2,000 pages still fail it whole, the last because a songbook silently losing its tail
  is worse than one reported as unreadable. A `/Rotate` that is no multiple of 90 is read as the nearest one.
  An `ActualText` over glyphs on one baseline replaces them (an empty one removes them), which is how Campfire's export
  carries a right-to-left run in its logical order; one over several baselines (a hyphenated word) is left alone, and
  it is charged to the text budget. A PDF from elsewhere with right-to-left text in visual order and no `ActualText` is
  still read in visual order. Page, operator and work yields keep
  the web responsive. Stream ends are found once per file (`PdfStreamEnds`, every `endstream` offset in one pass) rather
  than by searching the rest of the file for each object, and at most 1 KiB of whitespace is walked past a declared
  `/Length`, so a recovery scan over thousands of streams with no usable end stays linear. The spaces that bridge a gap
  between glyphs (as many as the gap holds in a monospace font) are charged to the same text budget as the glyphs,
  so a tiny monospace font cannot pad a page into hundreds of megabytes. `commonTest` exercises syntax, filters, encodings, geometry and rejection paths,
  through `ReadableDocuments`, a mirror of what the local source does around the extractors; the end-to-end goldens
  stay with that source in `:data:source:local:implementation`.
- **`zip/`** — a dependency-free zip implementation: `ZipReader` (STORED + DEFLATE; a ZIP64 archive rejected, a ZIP64,
  encrypted or otherwise compressed entry left out),
  `ZipWriter` (STORED only — the module has no DEFLATE encoder, and a stored archive's size is what an import unpacks,
  which is what lets the export warn by the archive's size that it is past `ImportLimits.MAX_IMPORT_SIZE`; song text
  would compress about 2×), with every entry dated by its supplied DOS timestamp and more than 65,534 entries refused rather than written as ZIP64, `Inflater` (raw DEFLATE, RFC 1951,
  following `puff.c`) and `Crc32`. It exists because no multiplatform zip library covers wasmJs. Sizes an archive
  declares are trusted only as far as a first guess: an entry is asked about by name before it is inflated — hidden
  files and anything an import would not look inside are never read, a song over `ImportLimits.MAX_TEXT_FILE_SIZE` is
  not either, and the caller says how much the whole import may still unpack to (`ImportLimits.MAX_IMPORT_SIZE`,
  24 MiB, nested archives included) — charged before an entry is read, so a damaged entry costs what it declared, and
  an inflating entry is stopped at the size it declared rather than at the 24 MiB any entry may reach; the entries'
  compressed sizes may not add up to more than the archive holds, which is what a central directory pointing many
  entries at the same bytes (a zip bomb) runs into. An entry that cannot be read is left out and reported by name; only an archive
  that cannot be walked at all is a `ZipException`. The buffer still starts at no more than 1 MiB whatever the central
  directory claims. Entry names are UTF-8 where they say so or are valid UTF-8 (macOS does not say so), and code page
  437 otherwise, as the format specifies. A backslash separates paths as well as a slash, since Windows PowerShell 5.1
  writes one.

Tested with `commonTest` (syntax, filters, encodings, geometry and rejections of the readers, zip round trips, reader
rejections, the inflater's limit) and `desktopTest` (the inflater and the zip reader against archives the JVM produced),
run with `./gradlew :data:formats:desktopTest`.
