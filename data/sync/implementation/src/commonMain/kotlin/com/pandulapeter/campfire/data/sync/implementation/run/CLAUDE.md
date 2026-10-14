<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# data/sync/run

- `LibraryFileKind.matches` filters the remote listing and the index as well as the local listing: a file listed on one
  side only reads as a deletion. Anything else in the cloud folder is left alone.
- Names match ignoring case and Unicode form: a remote name takes the local spelling, and an index entry neither listing
  has moves to the listed spelling that folds to it.
- **What cannot be read is left out of the plan on both sides and named among the failures, never read as a
  deletion**: a download above `MAXIMUM_FILE_SIZE`, a local file above it (its index entry too), a remote name this
  device cannot hold (split off before name folding, named once), and a local file whose read fails (its index entry
  kept). If no file at all can be read, the run ends as a storage failure.
- **Conflicts**: the incoming version is written next to the local one *before* the local one is uploaded, as ` (2)` or
  the first number free on both sides, and taken back if the service then says the remote file is still there, so the
  losing version is never held only in memory. Two exceptions take the folder's version with no copy, written only over
  the bytes compared:
  - a setlist whose versions differ only in the day, or whose only local change is the day a read gave an undated file
    (`SetlistComparison`, so the engine never sees the setlist format);
  - a demo file this device planted that still hashes to `UserPreferences.demoLibraryContentHashes` and has no index
    entry. With an index entry it was changed back on purpose and keeps its copy.
- **A download is decided twice**: before the request and just before the write, so a save made meanwhile becomes a
  conflict. The index records the revision the download fetched. Every local change happens under `LibraryFileLock`,
  which the repositories hold from their check to their write, so a save is never overwritten or deleted silently.
- **Deletion guard** (`DeletionGuard`): a plan stops with `Result.DeletionsNeedConfirmation` if its deletions on one
  side exceed half the index (at least `MIN_DELETIONS_TO_ASK`) or are all of it, or if it would delete remotely while
  the local listing is empty and the index is not (a missing library folder lists as empty everywhere).
  - `KEEP_AND_UPLOAD` drops the index entries of files here and not there (so they read as new) and those songs from the
    last synced `preferences.json`, so their overrides travel back; `KEEP_AND_DOWNLOAD` is the mirror image.
  - An answer waives its own direction only, for one run: the policy is a parameter, never state. Settings' library
    deletion is the one run started with an answer already given.
  - Remote deletions go to `SyncProvider.delete` in one call, so the folder is half deleted (what another device's guard
    sees) as briefly as possible.
- **Failures**: one file failing does not end a run; only `SyncRunEndingException`s do. `CancellationException` is
  caught *first* and rethrown. A failed or still contested file keeps `lastSyncedAt` where it was. A run ending in a
  non-`Exception` throwable rescans the whole library if any operation had finished, since it may have come
  mid-write.
