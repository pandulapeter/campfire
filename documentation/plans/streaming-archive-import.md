<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: streaming archive import, the release check and the cleanup

**Status:** built and on `main` (up to 6df8e17d5, 2026-11-18), not yet released. Every archive (a library export,
another app's backup, a shared link's zip, ZIP64 included, up to 16 GB and `MAX_ARCHIVE_ENTRIES` entries) is copied
into `imports/` as it arrives and unpacked from there, never held in memory. The switch that turned this on is
`ImportIntake.STAGES_ARCHIVES` (`:data:model`), mirrored for the iOS share extension by `ShareLimits.stagesArchives`
(`ShareInboxWriter.swift`). How it works is in the `CLAUDE.md` files it touched: `data/formats` (`zip/`),
`data/source/local/implementation` (`imports/`, staging, the ledger), `domain/implementation/.../importing`,
`ui/screens/importReport`, `ui/progress`, `ui/platform`, and `app/ios`; the history is in git.

**It is critical for the next release: safety and reliability first, with no room for error.** No release is built
from `main` until step 1 has passed on every platform. There is no fallback to the old in-memory path: the streaming
path is the only one production runs.

## 1. Release check by hand, on every platform

Not done until every line passes on every platform, with the same archives each time:

- a library export of the owner's library with pictures and attachments;
- a shared link opened from a message;
- a SongbookPro backup, and an OnSong or OpenSong archive;
- a generated archive over 1 GB, and one over 4 GB (ZIP64) written by `ZipWriter`;
- one zipped past 4 GB by the system's own tool (macOS Archive Utility, Windows Explorer) where the platform can: it
  either imports (the tool wrote ZIP64) or is refused with the wrapped-archive sentence (it did not), never the
  damaged-archive sentence and never a wrong import;
- a library export past 4 GB, exported and imported back;
- a small archive with ZIP64 forced (`zip -fz`);
- a corrupt one: truncated, and one with a damaged entry;
- Cancel during receiving and during unpacking, and an import on a nearly full device.

After each: the review matches the same archive on the last release (where it could read it; a ZIP64 one matches its
content zipped without ZIP64), the library holds what it should, `imports/` is empty after the import and after the
next launch, and the app stays responsive.

**Leaving the app** during a download, an unpack, and an Import write, on every platform. The Import write cannot be
cancelled; a kill during it leaves what was written so far, and importing the same archive again completes it without
duplicates.

- **Android**: the small-heap emulator at 360 × 640 dp (Medium_Phone AVD, 720 × 1280 at density 320) and a device;
  picked, opened with, shared from Files, Gmail, and Drive, dropped from another app in split screen; the process
  killed during receiving and relaunched; low disk by filling the emulator's storage. The import's notification shows
  progress and its Cancel works, also with a sync running beside it; Android 15's `dataSync` time limit ends an import
  cleanly (`onTimeout`).
- **iOS**: the simulator and a device; picked, opened in place from iCloud Drive (not downloaded yet), AirDrop, the
  share extension, a drop on iPad. On iOS 26 or later, on a device: the system's progress for the import and its stop,
  which cancels the import. Before iOS 26: when background time runs out the import pauses and resumes on return (the
  simulator never suspends the app, so only a device shows this).
- **Desktop**: macOS, Windows, and Linux; picked, dropped, opened with while running and cold; Cmd+Q and closing the
  window during an import ask first, and stopping cancels it cleanly; on Windows the source file renamed and deleted
  during the copy and during the review (it must not be locked).
- **Web**: Chrome, Safari 26, and a Safari before 26 (no `createWritable()`); picked and dropped; a hidden tab finishes
  its import; closing the tab during one warns, and a tab closed during unpacking and reopened leaves nothing behind;
  `navigator.storage.estimate()` near its quota.

Also before the release: run `ArchivePathParityTest` against the private import corpus once more (it is the last run
the old path gets, step 2 removes it), and, for the owner:

- **The Play Console**: add the import to the `dataSync` foreground service declaration: "Campfire also uses this
  foreground service while it imports songs the user chose (downloading a shared link or copying and unpacking a picked
  archive), so that the import finishes when the user leaves the app; the notification shows its progress and has a
  Cancel button."
- **The website** (its own repository): replace any mention of the old 88 MB import limit.
- **Decide on large exports**: exporting a library past 65,535 files that also holds recordings near 45 MB runs out of
  a 128 MiB heap (about 77 MB of per-file bookkeeping beside an attachment read whole); either alone is fine. Such a
  library could not be exported at all before.

## 2. Remove the old path, before the release ships

Once step 1 has passed on every platform, and before the release is built (not the release after):

1. **`DocxTextExtractor`** (`data/formats/.../document`) moves first, in a commit of its own, from
   `ZipReader.read(ByteArray)` to the streaming reader over a `ByteArrayZipSource`, with the Word tests and their fuzz
   test passing unchanged. A Word document is still read whole, within `MAX_DOCUMENT_FILE_SIZE`.
2. **The old reader and unpack go**: `ZipReader.read(ByteArray, …)`, its callers in `ArchiveLocalSourceImpl` and
   `ArchiveSongImages`, and whatever only they used.
3. **The switch goes**: `ImportIntake.STAGES_ARCHIVES` and `stagesArchives` with every branch on them (`git grep
   stagesArchives`), and `ShareLimits.stagesArchives` in the share extension. `ArchiveHeldInMemory`, which stages an
   archive that still arrives as bytes, stays. The `CLAUDE.md` lines naming the switch are updated.
4. **The tests**: `ArchivePathParityTest` (with `ArchivePathParity` and `ParityArchives`) becomes the new path's half of
   the parameterised tests; every test that only fed the old path is deleted.

The full test suite, `largeArchiveImportTest`, and CI pass after the removal, and the release is built from that
commit. Then delete this plan and its roadmap entry.
