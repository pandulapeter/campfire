<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: exports written to a file, not held in memory

**Status:** not started. Written against the code on 2026-11-11 (95af1c4f5). Lands after
[compressed archives](zip-compression.md) and before the export step of [images in songs](song-images.md), which
needs it.

Every export is a `ByteArray` from end to end: the files read whole into a map, `ZipWriter` copying them all into
one buffer, `ExportedFile.bytes`, then a platform copy (Android's pending export and share copy, the web's
`Int8Array`). An export holds three to four times its size in memory at its peak, which text libraries never notice
and a library with photos would, the web build first. The same holds for PDF exports, whose pages are images.

## Decisions

- **An export is a file.** `ExportedFile` keeps its name and type and gains a source: bytes, as today, for the
  small text and PNG cases, or a temporary file the export was written into. Archives and PDFs write to the file.
- **The writer takes a sink.** `ZipWriter` writes each entry to a sink as it goes: read one library file, deflate
  it, write header and data, let it go. The CRC and sizes are known before the header is written (the entry is in
  hand), so no data descriptors are needed. It keeps only the offsets, CRCs, and sizes for the central directory, so
  its memory is one entry, not the archive. The in-memory `write` stays, as the sink into a buffer, for the tests and
  the small cases.
- **Temporary files have one home and one lifetime.** A new `StorageDirectory.EXPORTS` (`exports/` beside `covers/`,
  never backed up, synced, or exported) holds them; each is deleted once saved or shared, and the whole folder is
  emptied on launch, so a crash leaves nothing behind for long.
- **Each platform hands over the file, not its bytes:**
  - Android: the save writes the file into the chosen document by streaming; the pending export kept for process
    death becomes the file itself, and a share serves it through the existing provider with no second copy (the
    provider's cache path then points at `exports/`).
  - iOS: the share sheet and the document picker are given the file's URL.
  - Desktop: the file is moved (or copied, across volumes) to the chosen path.
  - Web: the archive is written into OPFS, and `getFile()` gives a disk-backed `File` that both the download and Web
    Share take, never copied into the wasm heap.
- **Shared links keep the single upload.** Dropbox takes up to 150 MB in one request; a shared copy over 150 MB is
  refused with a sentence rather than given upload sessions, which no song or setlist with photos nears. The upload
  reads the file in chunks where the platform's HTTP client streams a body, else whole (it is one shared copy, not
  the library).
- **ZIP64 stays out.** 65,535 entries and 4 GiB per archive are far beyond any library; the writer keeps refusing
  past them, as it does today.

## Steps

1. **Sink and streaming writer** (`:data:formats`, pure): `ZipSink` (write bytes, position) with a buffer
   implementation; `ZipWriter` streaming entries from a sequence of (name, suspend reader) into it. Tests: the
   archive byte-identical to the in-memory one, one entry in memory at a time (a reader that fails if asked twice).
2. **File sinks** (`FileStorage`): append-writing a file in `EXPORTS`, on every platform including OPFS
   (`createSyncAccessHandle` in the worker, or a writable stream); emptying `EXPORTS` on launch. The storage
   contract tests, the iOS simulator run included.
3. **The model and the use cases:** `ExportedFile` with its source; `ArchiveRepository.pack` into a file; the song,
   setlist, and library export use cases, and the PDF export writing its pages into a file as it draws them.
4. **The platforms' `FilePicker`** save, share, and mail paths, each taking the file; checked by hand once per
   platform (save, share, cancel, and Android's process death during the save dialog).
5. **Shared links:** `SharedCopySync` packs into a file and uploads it; the 150 MB refusal.
6. **Documentation:** `exports/` in the root `CLAUDE.md` library layout, the storage, export, print, and platform
   docs.

## Size and risk

Steps 1 and 2 are small and pure or contract-tested. The weight is in steps 3 and 4: `ExportedFile` is used in about
sixteen files, and the save and share paths are platform code that only a hand check covers. The risk is a regression
in exporting, which today works on every platform; each platform's paths are switched and checked one at a time.
