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

`SyncEngine` and `SharedCopySync` are built by `DataSyncModule` functions (a lambda over `UserPreferencesRepository`
Koin cannot inject; the build number). `DemoLibraryRepositoryImpl` lives here because it records demo files under
`SyncKey.path`, the key the engine looks them up by.

## Locks

- `SyncConnectionManager`'s restore lock: never held across a run; forgetting the stored connection runs under it.
- `SyncRunner`'s run lock: held only by a run, by `disconnect` around deleting the index (and the shared links' mirror
  and journal), and by a finished authorization around resetting them for another account and showing it. Sharing
  actions never take it.
- `SharedLinksJournal.whileApplying`: held by the run's shared-copy step (inside the run lock), by
  `SharedLinksJournalApplier` from loading the journal until what was applied is removed, and by Stop sharing and Stop
  sharing everything, so a step never uploads a copy stopped after it read the list; the journal's file lock inside it.
  The applier never takes the run lock, so it, and a share applying the journal first, may wait for a run.
- `LibraryFileLock`: held by the engine around local file calls only, never a request. Not reentrant, and refuses
  reentry from its holder with an exception rather than deadlock: the repositories take their own locks first and this
  one inside; nothing holding it calls anything that takes it. A local write or deletion and its `onLocalFileChanged`
  finish together once started (`NonCancellable`).
- `SyncLibraryRefresher.changedFilesMutex`: innermost.

## Invariants

- **Content decides what changed, never a clock**: the platforms disagree about modification times and the web has none.
  **An edit always beats a deletion**, so a device that edited a renamed song's old name since the last run puts it
  back, leaving both (`SyncPlanner` knows no moves). A file changed on both sides is never merged. `SyncPlanner` stays a
  pure, tested function of (local hashes, remote listing, index).
- **A conflict keeps both versions; the index decides which keeps the name.** A file the last run saw keeps this
  device's edit under its name and the other version as the ` (2)` copy. One the index has never seen (a device
  connecting, perhaps with an old copy of the library) keeps the cloud folder's version, so other devices get one new
  file per conflict instead of a rewritten song.

## Tests

A behaviour spanning devices (renames, conflicts, deletions, synced preferences) gets a case in `TwoDeviceSyncTest`. The
upgrade-5.2.0 test resources, here and in `:data:source:local:implementation`, are what the 5.2.0 release's own code
wrote; never regenerate them with today's code, which would agree with itself whatever it reads. Further fixtures
follow the root `CLAUDE.md`'s Stored formats rule.
