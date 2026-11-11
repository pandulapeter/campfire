<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: compressed archives

**Status:** not started. Written against the code on 2026-11-11 (95af1c4f5). `zip/` is
`data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/zip/`.

`ZipWriter.write` stores every entry as it is (method 0), so a library export, a setlist zip, and every shared link
are as large as the files inside them. ChordPro text deflates to a third of its size or less, so compressing makes
exports and shared links smaller to upload, download, mail, and keep. Independent of the
[streaming zip writer](streaming-zip-writer.md), and best landed before it, so that plan starts from the final entry
format.

## Decisions

- **Reuse the PDF's deflater.** `ui/print/PrintDeflater.kt` (`:presentation`) already writes deflate with the fixed
  Huffman codes, wrapped as zlib for FlateDecode. It moves to `zip/Deflater.kt` in `:data:formats`, writing the raw
  deflate stream a zip entry holds; the print writer adds the zlib header and Adler-32 around it itself. No new
  dependency: no platform offers a compressor to common code. Fixed codes cost about a fifth against dynamic ones,
  which is not worth their code here either.
- **Each entry takes the smaller form.** An entry is deflated (method 8) only when that makes it smaller; one that
  does not shrink (a JPEG, once the [images plan](song-images.md) lands, or a tiny file) is stored. The writer
  decides by trying, not by extension.
- **Every version already reads it.** `zip/Inflater.kt` has inflated other apps' archives since before v5.3.1, so an
  older Campfire opens a compressed export or shared link. Nothing else in the archive changes: names, order, and the
  undated timestamp of shared copies stay as they are.
- **Deterministic.** The same files give the same bytes, which shared copies rely on (they are rebuilt from the
  library's files). Existing shared copies are not rebuilt for this: `sharedCopyFingerprint` is made of the songs'
  hashes, not the zip's, so each copy is compressed the next time its songs change.
- **Off the main thread, cancellable.** `pack` already runs on `Dispatchers.Default`; the deflater yields every
  256 KB as it does for print, so a cancelled export stops.

## Steps

1. **Move the deflater.** `zip/Deflater.kt` (raw deflate) with its tests moved from `PrintDeflaterTest` and
   `PrintDeflaterRoundTripTest` (the JDK `Inflater` round trip stays a desktop test). `PrintPdfWriter` wraps it in
   zlib; the PDF goldens must not change by a byte.
2. **The writer compresses.** `ZipWriter.write` deflates each entry, keeps the smaller form, and writes method,
   compressed size, and uncompressed size per entry; the CRC stays the uncompressed data's. `pack` becomes `suspend`
   down to the writer for the deflater's yields. Tests: a text entry deflated and read back by `ZipReader`, an
   incompressible entry stored, an empty entry, the same input giving the same bytes, and the JDK's `ZipInputStream`
   reading the result on the desktop.
3. **Documentation.** `data/formats/CLAUDE.md` (the zip writer compresses; the deflater lives here and print uses
   it), `ui/print/CLAUDE.md` and the export package's (the PDF deflater's new home, archives being compressed).

## Size

One file moved, one small change to `ZipWriter` and `PrintPdfWriter`, and their tests. No platform code, nothing to
check by hand beyond opening one export in the system's archive tool.
