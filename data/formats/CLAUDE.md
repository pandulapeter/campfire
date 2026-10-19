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

Pure PDF and Word text extractors and zip reader/writer in common Kotlin on every target, on no third-party library
(only `kotlinx.coroutines`) and no module but `:data:model` and `:chordpro`: no `FileStorage`, no Koin, no network.
Used only by `:data:source:local:implementation`'s `DocumentLocalSourceImpl`, `ArchiveLocalSourceImpl`, and the readers
of other apps' files in its `backup/` (`parseXml` for OpenSong); public is only what those use, everything else
`internal`. `ZipReader` is the one place that prints (the name of an entry it could not read).

Documents are read locally, never rendered, uploaded, or kept; `ExtractedDocument` is all that leaves this module.

## `document/`

- Images and font programs are never decoded. Proportional positions in Word are estimates. `DocxTextExtractor` opens
  only `word/document.xml` and `word/styles.xml`; the XML parser refuses DTDs. Deleted text and external parts are left
  out.
- **Every limit exists to bound untrusted input; keep them all.** Object, recursion, stream, page, operator, glyph,
  output, and XML event limits, plus one per-document work budget (interpreted content and stream input, so re-reading a
  shared form or contents stream is paid for) and one font-table-entry budget (shared `/W` arrays and CMaps are paid for
  each time). Bridging spaces between glyphs are charged to the text budget, so a tiny monospace font cannot pad a page
  to hundreds of megabytes. A budget running out is `PdfLimitException`, distinct from a malformed object.
- Damage is local: a malformed page, font, or form costs only itself, and the document fails only when more than half
  its glyphs or pages do. Budgets, encryption, a broken page tree, and over 2,000 pages fail it whole (a songbook
  silently losing its tail is worse than one reported unreadable).
- Must stay linear: `PdfStreamEnds` finds every `endstream` once per file, and at most 1 KiB of whitespace is walked
  past a declared `/Length`.
- Columns: a gutter is a 0.8 em whitespace band crossed by at most a fifth of the lines, **found at every half point,
  not sampled**, since Campfire's own export leaves only 18 points between 20-point text. Each side is searched again
  (three and four columns).
- An `ActualText` over glyphs on one baseline replaces them; this is how Campfire's export carries right-to-left runs in
  logical order. One spanning several baselines (a hyphenated word) is ignored.
- Page, operator, and work yields keep the web build responsive.

## `zip/`

Exists because no multiplatform zip library covers wasmJs. `ZipReader` reads STORED and DEFLATE (ZIP64 archives
rejected; ZIP64, encrypted, or otherwise compressed entries left out); `Inflater` follows `puff.c`.

- **`ZipWriter` writes STORED only, on purpose**: a stored archive's size is what an import unpacks, which lets the
  export warn when it is past `ImportLimits.MAX_IMPORT_SIZE`. More than 65,534 entries are refused rather than written
  as ZIP64.
- **Declared sizes are only a first guess.** Entries are asked about by name and declared size before inflating
  (`EntryPolicy`: hidden files are skipped, oversized songs never read), or, under a name that says nothing, by a
  512-byte peek (`Sniff`, the inflater's prefix mode) that decides whether to read the rest; a peek turned down costs
  no budget. The caller's remaining import budget is charged before an entry is read, and inflation stops at the
  declared size. Each entry's compressed bytes are charged once, peek and read together, and may not add up to more
  than the archive holds, which stops zip bombs whose central directory points many entries at the same bytes. The buffer starts at no more than 1 MiB
  whatever the directory claims.
- An unreadable entry is left out and reported by name; only an unwalkable archive is a `ZipException`.
- Entry names are UTF-8 where flagged or valid (macOS does not flag), else code page 437. A backslash is a path
  separator too (Windows PowerShell 5.1 writes one).

## Tests

`./gradlew :data:formats:desktopTest`. `commonTest` calls the extractors directly; `desktopTest` checks against archives
the JVM produced. What the local source does around the extractors (size limit, extension, blank text, failure) and the
end-to-end document goldens are tested in `:data:source:local:implementation`.
