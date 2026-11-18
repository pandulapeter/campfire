<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: testing the features that can lose a library or publish too much

**Status:** done but for the owner's part (2026-11-18). Sync's deletion logic, what a shared link publishes, and the
import of real other apps' backups are now tested past the usual. Left are three backups only the owner can make and
three rulings.

## What guards them now

- **Sync**: `SyncSimulation` (1,000 seeds in CI, 100,000 passed by hand) checks the deletion guard on every run,
  faulted and multi-pass ones included, against Dropbox's piecewise batch deletions, partial and stale listings, and
  writes over deleted files (all measured on the live account and written into `sync/run/CLAUDE.md`), with
  attachments, the editor's unsaved pictures, folder copies damaged from outside, skewed clocks, and explicit stops on
  any device. `SyncPlannerTableTest` enumerates the planner's 5,580 per-file states.
- **Shared links**: `SharedCopyParityTest` (200 seeds in CI, 10,000 by hand) holds the export's first copy and sync's
  rebuild to the same bytes and to exactly what was shared; the simulation holds a stopped share to leaving no copy.
- **Backups**: `BackupCorpusTest` and `ArchivePathParityTest`, by hand with the private corpus, hold every real backup
  under `backups/` to its own listing and to an export round trip.
- **The live check** (two desktop homes and the Android emulator on the owner's folder) behaved as documented on every
  line, and the folder was restored by hash.

They found and fixed: a stopped share's copy coming back (twice, by two routes), the guard missing deletions split over
two passes, a picture lost when a run stopped mid-conflict, a working share marked dead by a partial listing, the two
copy builders disagreeing (byte order marks, a setlist's other spellings), setlists missing songs spelt in another case
on Android, Linux, and the web, SongbookPro's minor keys and capo chords, and SongbookPro's ChordPro share not
offering Campfire.

## Left

### The owner's backups

Into `backups/<app>/` under the corpus folder, each named in that folder's `sources.txt` (app, version, platform, how
it was made), with accented and non-Latin titles, case twins, and two songs of one title among them:

- **SongbookPro on iOS**: a whole-library `.sbpbackup` (Android's is in).
- **OnSong**: its backup and its export of several songs; a format that is not a zip of `.onsong` files is a finding
  about the importer, noted in `sources.txt`.
- **OpenSong** (desktop): its `Songs` and `Sets` folders as on disk, zipped by the system's tool.

Then run, with `-Pcampfire.importCorpus=…` from a worktree, `--tests '*BackupCorpusTest*'` and
`'*ArchivePathParityTest*'`. A failure is fixed in the importer with a test built from a synthetic file, never the
owner's. When the streaming plan removes the old path, `ArchivePathParityTest` goes and `BackupCorpusTest` stays.

### Rulings for the owner

- **A device following another's piecewise deletion is not promised a question** (`sync/run/CLAUDE.md`): each piece is
  judged alone, so a follower may take a large deletion unasked; nothing saved is lost, since edits still beat
  deletions. Live, the followers took the first pieces unasked and were asked about the rest, but only because
  Dropbox refused their writes as busy until the batch finished. Promising it needs recent remote deletions kept in
  the sync index (a stored-format change) and a guard over a window of runs: its own plan, if wanted.
- **A folder picture or attachment damaged from outside, with no intact copy anywhere, fails every run on every
  device**, even when no song names it (`sync/run/CLAUDE.md`, Pictures). The likely fix leaves one that nothing names,
  holds, or indexed out of the plan quietly, or deletes it from the folder.
- **OpenSong and OpenSongApp sets** (`.osbs`, `.osts`, the set inside an `.ossb`) are reported as skipped, never
  imported as setlists; `.osbs`, `.ossb`, `.osts`, and `.justchords` are not offered for "Open with".

## Not in this plan

- Every other feature stays on proportionate testing; synced preferences are covered by `SyncedPreferencesTest` and
  the simulation.
- A Google Drive provider must first close the wrong-listing limit (its plan's Risks).
- The corpus and every real backup stay out of git.
