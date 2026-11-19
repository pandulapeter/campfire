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

The sync engine, and Campfire's side of it. The engine is to become a library another app (a diary, a notes app)
syncs its own documents with (`documentation/plans/sync-library-extraction.md`), so its packages (`run/`, `index/`,
`scheduling/`, `connection/`, `merge/`) know nothing of songs, setlists, pictures, preferences, shares, or the
repositories: they declare the seams below, and Campfire's side implements them. `.github/scripts/check_sync_seams.py`
fails a main source file there that imports Campfire's side (after every edit, before every commit, and in CI); a test
may wire Campfire in. The provider side is `:data:source:remote:implementation`'s.

## Seams (engine → Campfire's side)

- **Collections** (`SyncCollection` in `:data:model`): an id (the folder and the index key prefix, so a stored name),
  what names it holds, a size limit, and a role. `DOCUMENT`s are counted by the deletion guard and kept both in a
  conflict; `CONTENT_FILE`s are named by their content, hashed by size, never a conflict, kept while a document names
  them, and restored from the folder's history. → `LibraryCollections` (`songs`, `setlists`, `files`).
- **References** (`run/SyncReferences`, by collection): which content files a document names; a collection in the map
  is a referencing one, whose unreadable or oversized document holds back every content file deletion.
  **Conflict policies** (`run/ConflictPolicy`, by collection): parts a conflict may ignore. → `LibrarySyncRules` (songs
  through `libraryFilesNamedBy`, setlists' day through `SetlistComparison`).
- **Library observer** (`scheduling/SyncLibraryObserver`): what re-reading the files means for the app; the throttle
  stays `SyncLibraryRefresher`'s. → `LibrarySyncObserver` (the song, setlist, and image repositories).
- **Run steps** (`scheduling/SyncRunStep` in `SyncRunSteps`): what the app keeps in the folder beside the library, run
  in order after the files inside the run lock, failures reported by id in `SyncSummary.failedSteps`, forgotten with
  the index. → `shared/SharedCopyStep`; ids in `LibrarySyncSteps`.
- **Folder document** (`merge/`, not a step: the index keeps its base): one JSON document three-way merged by
  `MergedFolderDocument` through a `MergedFolderDocumentCodec`. → `preferences/SyncedPreferencesCodec`.
- **Constructor values**: `imageWritesGate` (`ImageRepository.withImagesKept`), the planted demo hash (a lambda over
  `UserPreferencesRepository`, which Koin cannot inject), the large file threshold. `DataSyncModule` builds all of it.

Still Campfire's in the engine, for the extraction: `LibraryFiles`' name folding and content-name rule, `Logger`, and
the index's `syncedPreferences*` keys (stored names). `DemoLibraryRepositoryImpl` lives here because it records demo
files under `SyncKey.path`, the key the engine looks them up by.

## Locks

- `SyncConnectionManager`'s restore lock: never held across a run; forgetting the stored connection runs under it.
- `SyncRunner`'s run lock: held only by a run (its `SyncRunSteps` included, in order, after the files), by `disconnect`
  around deleting the index and forgetting the steps (the shared links' mirror and journal), and by a finished
  authorization around resetting them for another account and showing it. Sharing actions never take it.
- `SharedLinksJournal.whileApplying`: held by the run's shared-copy step (inside the run lock), by
  `SharedLinksJournalApplier` from loading the journal until what was applied is removed, and by Stop sharing and Stop
  sharing everything, so a step never uploads a copy stopped after it read the list; the journal's file lock inside it.
  The applier never takes the run lock, so it, and a share applying the journal first, may wait for a run.
- `LibraryFileLock`: held by the engine around local file calls only, never a request. Not reentrant, and refuses
  reentry from its holder with an exception rather than deadlock: the repositories take their own locks first and this
  one inside; nothing holding it calls anything that takes it. A local write or deletion and its `onLocalFileChanged`
  finish together once started (`NonCancellable`).
- `ImageRepository.withImagesKept`: taken by the engine around its local picture deletions, outside `LibraryFileLock`, in
  the order the app's writes of a picture and its song take them, and alone to read the draft's names for a damaged copy.
- `SyncLibraryRefresher.changedFilesMutex`: innermost.

## Invariants

- **Content decides what changed, never a clock**: the platforms disagree about modification times and the web has none.
  **An edit always beats a deletion**, so a device that edited a renamed song's old name since the last run puts it
  back, leaving both (`SyncPlanner` knows no moves). **A pass never deletes one version on both sides**: a local
  deletion of the bytes the same plan deletes remotely under another name (a rename back onto a name the index knows
  holding them, deleted elsewhere) uploads the file instead. A file changed on both sides is never merged.
  `SyncPlanner` stays a pure, tested function of (local hashes, remote listing, index).
- **A conflict keeps both versions; the index decides which keeps the name.** A file the last run saw keeps this
  device's edit under its name and the other version as the ` (2)` copy. One the index has never seen (a device
  connecting, perhaps with an old copy of the library) keeps the cloud folder's version, so other devices get one new
  file per conflict instead of a rewritten song.

## Tests

A behaviour spanning devices (renames, conflicts, deletions, synced preferences) gets a case in `TwoDeviceSyncTest`.
`SyncSimulationTest` drives two or three devices through seeded random edits, runs, and failures (the app killed before
it writes the index included) and checks the promises above; each device's fake file system compares names as one
platform's does (`FileSystemNaming`), shared links must follow renames and stop only on a user's stop or deletion, a
stop holding while another device's run uploads the copy (`SimulatedSharing`), and songs show pictures and attach files each device prunes as the app does, none of a saved
song's ever lost, while an editor's unsaved text holds some, a writer outside Campfire overwrites folder copies, and
every device's clock is off in its own way; no device may hold a file whose bytes are not what its name says. A
failure prints its seed and steps (share ids aside, which are random), and a change to sync is worth a run by hand with
far more seeds (and a longer `runTest` timeout). The upgrade-5.2.0 test resources, here and in
`:data:source:local:implementation`, are what the 5.2.0 release's own code wrote; never regenerate them with today's
code, which would agree with itself whatever it reads. Further fixtures follow the root `CLAUDE.md`'s Stored formats
rule.
