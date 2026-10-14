<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :data:sync:implementation

Implements `SyncRepository` and `DemoLibraryRepository`. Everything is `internal`. `SyncEngine`'s demo lookup must stay
a `DataSyncModule` function, since the Koin plugin would hand an annotated constructor its `{ null }` default.
`DemoLibraryRepositoryImpl` lives here because it records demo files under `SyncKey.path`, the key the engine looks
them up by.

## Locks

- `SyncConnectionManager`'s restore lock: never held across a run; forgetting the stored connection runs under it.
- `SyncRunner`'s run lock: held by a run, and by `disconnect` around the index deletion only.
- `LibraryFileLock`: held by the engine around local file calls only, never a request. Not reentrant: the repositories
  take their own locks first and this one inside; nothing holding it calls anything that takes it.
- `SyncLibraryRefresher.changedFilesMutex`: innermost.

## Invariants

- **Content decides what changed, never a clock**: the platforms disagree about modification times and the web has
  none. **An edit always beats a deletion.** A file changed on both sides is never merged. `SyncPlanner` stays a pure,
  tested function of (local hashes, remote listing, index).
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

## The index

- Told about a file only once it has actually moved, so an interrupted run's half-done work just looks unsynced.
- Filed under `SyncAccount.indexKey` (the service's account id), never a user-changeable name. An index an earlier
  version filed under the e-mail address is adopted: a run without an index brings back every file deleted since.
- Periodic snapshots never throw; on the way out of a stopped run the last is written after waiting for (not
  cancelling) the periodic writer, since a cancelled write on the web carries on and could land last. Only the writes
  opening and completing a run throw, ending it as `STORAGE`.
- An index that exists and cannot be read ends the run as `STORAGE` before anything is written. Only one that reads but
  does not decode is taken for none. **Never rename its fields** (`SyncIndexDocumentTest` reads devices' real files),
  since lenient decoding would read a renamed field as an empty index.
- An `isRunInProgress` marker found at `restore` is reported as interrupted and stops the launch run.

## Scheduling and lifetime

- **A run belongs to the app**: `SyncRunScheduler` owns an application-lifetime scope; the phones keep the process alive
  through the shell-provided `SyncNotifier`.
- Every app change to a library file (`LibraryChanges`) and to a synced preference schedules a run
  `AUTOMATIC_RUN_DELAY` after the latest request; a request during a run is carried out after it. A run's own writes go
  around the repositories, so a run never schedules the next one.
- `startScheduledSynchronization` (the app leaving the front) starts the run on the caller's thread and puts
  `SyncProgress()` in the state before returning, so the shell can hand it to the platform keep-alive in the same
  callback.
- `SyncLibraryRefresher` re-reads only the files a run changed, never a rescan, after any ending.
- Tests advance `SyncEnvironment`'s scope with `runCurrent` / `advanceTimeBy`, never `advanceUntilIdle`.

## Connection

- `restore` is called once per view model; finding `Connected` it reads nothing (re-reading would make a running run
  look interrupted). It shows the stored account before asking the service, so a slow network never shows
  disconnected. Refused credentials are a `ConnectionFailed` (authorization), nothing deleted and no launch run;
  credentials unreadable right now are a connection failure with the file left alone.
- A previous installation's credentials that could not be forgotten are noted and retried by every `restore` first;
  until then it answers disconnected. A start-up carrying the answer to a waiting authorization forgets nothing, even if
  it looks like a first launch.
- `connect` never throws but cancellation; every exit not ending connected forgets the stored credentials, so an
  abandoned authorization is never restored next launch.
- A run refused by the service (after renewal fails) sets `ConnectionFailed`, not a failed outcome: Disconnect would
  delete the index, Connect keeps it.
- Disconnecting cancels and awaits a running run, then deletes the index under the run lock, completing regardless of
  caller cancellation. A run writes its outcome only into a state still `Connected`.

## Synced preferences

`preferences.json` sits at the top of the cloud folder, never listed by the engine: the per-song overrides
(transposition, tempo, capo, `beatLevels`), chord shapes (`chords`), and tag colors (`tags`); `chords` and `tags` are
written only once non-empty, so older documents keep their bytes.

- Settled by `SyncedPreferencesSync` only after a run the engine completed: a three-way merge, value by value, of this
  device's, the folder's, and the base the index keeps. A change beats a removal; two changes keep this device's.
- Merged as a JSON tree, so unknown fields and unreadable values of known fields pass through untouched. The index names
  the fields the base's writer owned; others are removed from the base so a newly owned field merges as add-only. A new
  field joins `SyncedPreferencesDocument.OWNED_FIELDS`.
- A folder document that is missing or unreadable is merged as the base, never as a removal of everything; one with a
  newer `version` is neither applied nor overwritten.
- A song no longer in the library loses its entry, except a file the run failed on or one the folder gained since the
  last sync. Chord and tag entries are never dropped with a song. Entries match songs ignoring case and Unicode form.
- A value changed here during the merge is kept. A failure sets `SyncSummary.havePreferencesFailed` and leaves the base
  unchanged.

## Renames

A rename reaches sync as a deletion plus a new file (`SyncPlanner` knows no moves). By "an edit beats a deletion", a
device that edited the old name since the last run puts it back, leaving both.

Tests: `./gradlew :data:sync:implementation:desktopTest`.
