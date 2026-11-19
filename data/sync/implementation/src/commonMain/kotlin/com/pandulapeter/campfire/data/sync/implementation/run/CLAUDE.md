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

- `SyncCollection.matches` filters the remote listing and the index as well as the local listing: a file listed on one
  side only reads as a deletion. Anything else in the cloud folder is left alone.
- Names match ignoring case and Unicode form: a remote name takes the local spelling, and an index entry neither listing
  has moves to the listed spelling that folds to it.
- **What cannot be read is left out of the plan on both sides and named among the failures, never read as a deletion**:
  a download above its collection's limit (`SyncCollection.maxSize`: 8 MiB for a song or setlist, 50 MiB for a library
  file, whose picture names are attachment names too), a local file above it (its index entry too), a remote name this device cannot
  hold (split off before name folding, named once), and a local file whose read fails (its index entry kept). If no file
  at all can be read, the run ends as a storage failure.
- **Conflicts**: the incoming version is written next to the local one *before* the local one is uploaded, as ` (2)` or
  the first number free on both sides, and taken back if the service then says the remote file is still there, so the
  losing version is never held only in memory. A numbered copy an earlier attempt left here (unlisted, unindexed) already
  holding the incoming bytes is reused, never taken back, so retries after network failures leave one copy; a numbered
  file another device knows is never the copy, since that device may change or delete it. Two exceptions take the folder's version with no copy, written
  only over the bytes compared:
  - a document of a collection with a `ConflictPolicy` whose versions differ only in its volatile parts, or whose only
    local change is those parts filled in: Campfire's setlists, whose day a read gives an undated file
    (`LibrarySyncRules` over `SetlistComparison`, so the engine never sees the setlist format);
  - a demo file this device planted that still hashes to `UserPreferences.demoLibraryContentHashes` and has no index
    entry. With an index entry it was changed back on purpose and keeps its copy.
- **A download is decided twice**: before the request and just before the write, so a save made meanwhile becomes a
  conflict. The second time, and `takeRemote`'s check, look under any spelling of the name (`spellingHeldOf`) and write
  under the one found: a file made meanwhile as `hallelujah.cho` is that conflict, not a twin of `Hallelujah.cho` on a
  file system that tells them apart. The index records the revision the download fetched. Every local change happens under `LibraryFileLock`,
  which the repositories hold from their check to their write, so a save is never overwritten or deleted silently.
- **Deletion guard** (`DeletionGuard`): a plan stops with `Result.DeletionsNeedConfirmation` if its deletions on one
  side exceed half the index (at least `MIN_DELETIONS_TO_ASK`) or are all of it, or if it would delete remotely while
  the local listing is empty and the index is not (a missing library folder lists as empty everywhere).
  - `KEEP_AND_UPLOAD` drops the index entries of files here and not there (so they read as new) and those songs from the
    last synced `preferences.json`, so their overrides travel back; `KEEP_AND_DOWNLOAD` is the mirror image.
  - It judges the run, not the pass (`RunDeletions`): a second pass happens exactly when another device writes during
    the run, perhaps deleting too, so each pass counts what the earlier ones planned to delete out of every song and
    setlist any pass's index knew. Such a question may come after the first pass deleted its part.
  - An answer waives its own direction only, for one run: the policy is a parameter, never state. Settings' library
    deletion is the one run started with an answer already given.
  - Remote deletions go to `SyncProvider.delete` in one call, so the folder is half deleted (what another device's guard
    sees) as briefly as possible.
  - **The deleting device asks; a device syncing meanwhile may follow the first pieces unasked** (owner's ruling,
    2026-11-18): Dropbox carries a batch out at a few files a second, so such a device sees pieces, each judged alone
    against an index the earlier ones shrank. Live, the followers took 12 and 14 songs, then were asked about the rest
    (their writes refused as busy until the batch ended). Not worth a guard over several runs: a person already
    confirmed the deletion, and nothing saved is lost (an edit still beats a deletion).
- **What the service does, as the tests' `FakeSyncProvider` answers it** (measured on Dropbox, 2026-11-18): an upload
  expecting the revision of a file deleted since writes it again (its folder too), and only one written or re-added
  since is a `Conflict`, so an edit uploaded over another device's deletion simply wins; a listing taken right after
  another client's upload, rename, or deletion shows it; a batch deletion is not atomic (~4 files/s), and a listing
  taken meanwhile is a consistent snapshot of any fraction of it.
- **A listing that is wrong** (partial, or older than the folder), which Dropbox's never was (measured): a file it
  leaves out reads as a deletion in the folder and is deleted here, within the guard, and the next run brings it back
  from the folder, nothing lost. **Known limit**: where the app is killed in that run and the index the throttled
  writer last saved predates the deletion, the next run reads it as this device's own and deletes the folder's copy,
  the version with it. A provider whose listings may be wrong (the planned Google Drive one) needs that closed first,
  by saving the index without those entries before deleting, or by asking the folder about each file before deleting
  it here. `SyncSimulation` never kills a run whose listing it falsified.
  - A listing older than what this device last read or wrote is outside the contract (`SyncFolder.list`): one still
    naming a file deleted since makes a local file of the same bytes look in step, so it is never uploaded, and the
    next run deletes it here. The simulation's stale listings go back no further than the device's last run.
- **Failures**: only `SyncRunEndingException`s end a run. `CancellationException` is caught *first* and rethrown. A
  failed or still contested file is named beside `lastSyncedAt`, which any run that got to the end moves. A run ending
  in a non-`Exception` throwable rescans the whole library if any operation had finished, since it may have come
  mid-write.
- **Pictures** (a `CONTENT_FILE` collection, named by content, attachments included: every "a song names" below is
  what a referencing collection's `SyncReferences` answers, Campfire's songs through `libraryFilesNamedBy` in
  `LibrarySyncRules`; `run/` reads no document format): never a conflict and never renamed.
  - Not read by the listing: the size stands for the hash (`localImageHash`), so a same-size damage goes unnoticed. Every
    transfer checks the bytes against the name (`ImageTransfers`): a damaged copy is never uploaded or written, and is
    replaced from the other side where that side's copy is intact; two intact copies of one name need only the index. A
    damaged folder copy is not fetched again while listed unchanged. One no device holds intact stays (another device's
    song may name it), a failure only where a song here or the editor's draft names it.
  - **A reference beats a deletion** (`SyncPlanner.withImagesKept`): a picture a song here names is uploaded or
    downloaded again instead of deleted. A pass that brings a song down, deletes one on either side, or follows an
    unreadable song deletes no picture on either side; the next run does (an older version's edit may beat a song's
    deletion in the folder, and it never puts a picture back), which the run that held them back asks for
    (`SyncPlan.holdsDeletionsBack`, see `scheduling/`). The deletion guard counts songs and setlists only.
  - **A file nothing holds comes back from the folder's history** (`LostFileRestorer`, `SyncFolder.restoreDeleted`):
    such an edit may name a picture already gone everywhere. A completed run asks back what songs here name and no side
    or index holds (20 a run, each name once per process); the follow-up downloads it. Dropbox keeps ~30 days; older
    ones stay lost.
  - A pass's local picture deletions run as one batch inside `ImageRepository.withImagesKept` and `LibraryFileLock`,
    after reading what every song names *now*, plus what the editor's unsaved text names (`LibraryImageWriter.heldNames`):
    either sends the picture back up instead.
  - Every file is held whole while it moves; one over 8 MiB on either side (`SyncPass.largeFiles`) moves alone, beside
    the other transfers, since six attachments at once are more than a phone's heap.
