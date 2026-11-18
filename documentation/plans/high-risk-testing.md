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

**Status:** not started. Written against the code on 2026-11-17 (230acf02b). `sync/` is
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/`, `syncTest/` the
same path under `src/commonTest`, and `localTest/` is
`data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/`
`source/`.

The streaming archive import was tested past the usual: a differential test of the old and new paths, seeded fuzz
mutants, the private corpus, capped-heap and large-archive tests, spikes on real platforms, and hand checks on each
platform, and that found real defects (an Android-only crash, a tiny archive failing for space, an iOS import
interrupted in the background, a dying content provider killing the app). Three other areas earn the same rigour:

1. **Sync's deletion logic**, for any change to it: the one other place that can delete the library, on both sides,
   with no undo.
2. **What a shared link publishes**, for privacy: exactly what the user chose, current only while shared, stopped for
   good only by an explicit action.
3. **Real other apps' backups in the private import corpus**, which holds no archive today.

This plan first records what is tested already, measured against the code, then fills only the gaps. Every new test
asserts behaviour (what a device, the folder, or a link holds), never which calls were made.

## Where it stands

### Sync: already proven

- **The model-based test exists.** `SyncSimulation` (driven by `SyncSimulationTest`, 1,000 seeds × 60 steps, 5.7 s on
  the desktop target) runs two or three devices on one `FakeSyncProvider` through random saves, edits, renames,
  deletions, prunes, a lost library folder, disconnects, shares, and runs, with faults striking at a random request
  (offline, refused, stopped, killed before or after the index is written, a read or a write failing, a save during the
  run, another device's run meanwhile). Each device's file system compares names as one platform's does
  (`FileSystemNaming`: exact, Windows, iPhone, and Mac, with or without decomposed storage), odd seeds stand in a
  service with exact names, and names in the pools differ only by case or Unicode form. After every step it checks that
  no version a user saved and nobody replaced is gone from every device and the folder (an edit beats a deletion, a
  conflict keeps both), that no side holds one name in two spellings, and that no picture a saved song names is lost;
  settled, every device holds the folder's library, another round moves nothing, and no picture is left that no song
  names.
- **Unit and scenario tests** cover each rule with a named case: `SyncPlannerTest` (16 cases of the per-file decision,
  edit beats deletion both ways, "a pass never deletes one version on both sides"), `SyncPlannerImageTest` (5: a
  reference beats a deletion, held picture deletions), `SyncPlannerTableTest` (every per-file state enumerated, 5,580
  cases in 0.1 s, held to the deletion promises; step 5), `DeletionGuardTest` (11: the thresholds, an empty library folder,
  an answer waiving its own direction only), `SyncEngineDeletionGuardTest` (14: nothing moves before the question, each
  answer's effect, both directions), `TwoDeviceSyncTest` (25: renames as delete plus create, edit against deletion, case
  and Unicode spellings, shared links following renames and stopping on deletion), `SyncEngineNameFoldingTest` (17),
  `SyncEngineUnreadableFileTest` (12: what cannot be read is never a deletion), `ImageSyncTest` (30: damaged pictures
  and attachments never uploaded or written, the editor's draft holding pictures, the folder's history),
  `SyncEngineDamagedCopyTest`, `SyncEngineRestoreTest`, `SyncEngineConflictTest` (26), and `PassListingTest` (9).
- **Content decides, never a clock**: holds by construction. The planner is a pure function of local hashes, the remote
  listing, and the index; the only clock reads in `:data:sync:implementation` are `lastSyncedAt` (`SyncRunner`) and a
  share's `sharedAt` date, and no sync code reads a modification time (`LibraryFile.lastModified` is 0 on the web).
- **The Dropbox provider's own rules** are tested against a recorded service (`DropboxFilesTest`: a listing follows its
  cursor and a page naming more but no cursor fails; uploads add or update an exact revision; downloads are checked by
  size and hash; `DropboxDeletionTest`: batch jobs, busy folders, files the answer does not mention).

### Sync: not proven

- **The deletion guard is checked only on fault-free runs.** `SyncSimulation.checkGuard` runs only when no fault was
  set (`isPlain`), because it counts deletions as before-and-after differences, which another actor's changes would
  blur. Yet the guard is applied per pass (`SyncEngine` calls `DeletionGuard.check` with the planning index of each
  pass, and a second pass starts from the first pass's updated index), and a second pass happens exactly when another
  device writes during the run. Nothing tests the guard across two passes of one run.
- **Dropbox's `delete_batch` is not atomic** (measured on the live account, about 7 files a second), so a device
  syncing during another's large deletion sees it in pieces, each under half, and follows without asking. The guard's
  design promises a second question on the other device ("two questions about one folder are better than one answer
  that empties both"); `FakeSyncProvider.delete` removes a batch at once, so no test meets the pieces.
- **Every listing is a true snapshot in the fake.** No test gives the engine a listing that misses files the folder
  holds (a pagination bug, an eventually consistent service such as the planned Google Drive provider) or one older
  than the device's own last write. Such a listing reads as remote deletions under the guard's threshold.
- **The simulation's library is narrower than the app's.** It names pictures only by `{image: …}`, never attachments
  (`{meta: attachment …}`), never models the editor's unsaved text holding a picture, never damages a folder copy, and
  every device uses the system clock.
- **Dropbox's answers the fakes assume are unmeasured** for: an `update` upload carrying the revision of a file deleted
  since (a `Conflict` in `FakeSyncProvider`; Dropbox may write it), a listing taken right after another client's write
  or deletion, and whether a link made before a shared copy was deleted opens a copy uploaded again at the same path.

### Shared links: already proven

- `SharedLinksRepositoryImplTest` (49): sharing uploads, links, and lists; another device's share of the item answers
  instead and the copy uploaded here is deleted; every failure after the upload deletes the copy where the list surely
  does not name it; Stop sharing deletes the copy before the entry; Stop sharing everything deletes every copy the
  listing or the list names, unlisted ones included; stops and renames survive an unwritable list through the journal;
  a stop waits for the same device's run step; disconnecting forgets the mirror and the journal.
- `SharedCopySyncTest` (21): a copy is rebuilt only when an input changes; pictures and attachments go under `images/`
  once each, in order, within their budgets, and an attachment left out loses its lines; a copy deleted by hand is
  marked missing for good; one missing from a listing older than the registry is left alone.
- `CreateShareLinkUseCaseImplTest`, `ExportSetlistUseCaseImplTest`, and `ExportSongsUseCaseImplTest`: the first copy
  (the export) holds the setlist and every song it names, a missing one left out, the pictures they show once, and the
  attachments within the budget.
- `SimulatedSharing`, inside the simulation: a share stops only where a user deleted the item under a name it had, and
  settled, every live copy's song is the folder's file and carries exactly the pictures its song names.

### Shared links: not proven

- **No test asserts that a copy holds nothing else.** The tests check that what belongs is present (`"[images/…]" in
  copy`) and ordered; none compares a copy's whole entry list with what the user chose, so an extra song, picture, or
  file would pass.
- **The two builders are never compared.** The first copy comes from the export use cases (`:domain:implementation`),
  every later one from `SharedCopySync.build`; they are meant to give the same bytes for the same files, and each is
  tested only against its own examples.
- **Explicit stops never meet another device.** `SimulatedSharing` shares, renames, and deletes, but never presses Stop
  sharing or Stop sharing everything, so nothing checks that a stopped copy never comes back when another device's run
  read the list before the stop (it uploads with the revision it listed: a `Conflict` in the fake, unmeasured on
  Dropbox). Its setlists are opaque text, so a setlist's songs never enter a copy in the simulation.
- **A partial shared-folder listing** "as new as the registry" would mark a working share `copyMissing` for good;
  `SimulatedSharing.checkSettled` skips such entries, so a share wrongly marked dead passes.

### The import corpus

`ImportCorpusTest` scores documents against hand-typed `.cho` files and never fails on a score;
`ArchivePathParityTest` takes every archive-like file under `campfire.importCorpus` (`.zip`, `.sbpbackup`, `.sbp`,
`.osb`, and zips by their bytes) through both import paths, with fuzz mutants of each. The corpus holds no archive, so
the parity test packs its folders into 20 archives of its own; no real SongbookPro, OnSong, OpenSong, or OpenSongApp
backup, nor any app's JSON export, has ever gone through either path. Once the streaming plan's last step removes the old
path, the parity test can no longer compare anything for them.

## Safety

- **Nothing here changes what the app does.** The steps add tests, test fakes, and live checks; a defect they find is
  fixed in its own commit with the smallest focused test that reproduces it (a `TwoDeviceSyncTest` or unit case shrunk
  from the failing seed's steps), never parked with `@Ignore` or an exception list. A behaviour the owner rules
  acceptable instead gets one line in the package's `CLAUDE.md` and is excluded from the property by name, with why.
- **The live account is restored.** Every live check follows the recipe in the sync live testing note: snapshot the
  folder from a fresh desktop home first, restore it by hash afterwards, connect only test homes (never the real
  installation), revoke the test grants at the end, and keep every helper and driver in the scratchpad, reverted with
  `git checkout` before any commit.
- **The corpus never enters git.** The backups hold the owner's and other people's songs; they and every report the
  tests write stay under the corpus folder (`.runs/`), outside the repository.

## Steps

### 1. Measure what the fakes assume, on the live account (by hand)

The fakes in steps 2 to 6 must answer as Dropbox does, so this goes first, like the streaming plan's spikes. With two
desktop test homes and the API as a third device:

- an `update` upload with the revision of a file another client deleted since: a library file and a shared copy;
- whether a link made before a shared copy was deleted opens a copy uploaded again at the same path;
- `list_folder` immediately after another client's upload, deletion, and rename: whether it ever omits the change;
- a `delete_batch` of 300 files with a second home running sync throughout: what each of its runs listed and did, and
  whether it ever asked.

**Done** when each answer is written into `sync/run/CLAUDE.md` or `sync/shared/CLAUDE.md` (where the fake's rule is
stated) and `FakeSyncProvider` answers the same way, in one commit, with the simulation still passing. If Dropbox
writes an `update` over a deleted file, the shared copy upload is a defect before anything else (a stopped share's copy
could come back) and is fixed first.

### 2. The deletion guard on every run

In `SyncSimulation`, `checkGuard` runs after every run that got as far as deleting, faulted or raced ones included,
counting what the engine itself deleted rather than before-and-after differences: songs and setlists deleted locally
(the device library's `onDelete`) and remotely (`FakeSyncProvider.deleteCalls`), against the device's index when the
run started. `FakeSyncProvider.delete` gains a fault that removes part of a batch, lets another device run, then
removes the rest, as Dropbox was measured to.

- **Expected to find**: a run whose second pass meets another device's deletions can delete more than half of what it
  started with without asking, and a device following a non-atomic batch deletion never asks at all.
- **Handled**: the owner rules whether "a run" means the run or the pass, and whether a follower must be asked. The
  likely fix counts a run's earlier passes against its starting index; the follower case may need the guard to count
  deletions the index saw over recent runs, which is a design change and gets its own plan if so.
- **Done**: the check holds over the CI seeds and 100,000 seeds by hand.

### 3. Listings that are stale or partial

Two more faults in `FakeSyncProvider`, struck by the simulation like the others: a listing that leaves out a random
subset of the folder's files while claiming to be complete, and one answered from the folder as it was a few writes
ago. A shared-folder listing gets the same faults.

- **Properties**: the existing ones (no saved version lost, one library once settled) and one new: an entry is
  `copyMissing` only where its copy is truly gone from the folder.
- **Expected to find**: a partial listing under the guard deletes those files locally and the next run brings them
  back (acceptable if nothing saved is lost and the owner agrees, written into `sync/run/CLAUDE.md`); a partial
  shared-folder listing marks a working share dead for good (a defect: a share then stops by inference).
- **Done**: both faults pass the CI seeds and 100,000 by hand. Their rate is kept low enough that most seeds still
  settle into ordinary runs.

### 4. The simulation's library as wide as the app's

- Songs name attachments (`{meta: attachment …}`) beside pictures, through `libraryFilesNamedBy` as the app does.
- A device's editor holds unsaved text naming a picture no saved song names (`LibraryImageWriter.heldNames`), changed
  and discarded at random.
- A writer outside Campfire replaces a folder picture's bytes now and then.
- Each device's clock is fixed apart from the others: one a day behind, one ahead, one stepping backwards between runs.

**Properties added**: no device ever holds a library file whose bytes are not what its name says, and a picture the
draft names is never lost while the draft names it. Nothing is expected from the clocks (sync reads none); they are
there so that a future change that consults one meets skew at once. **Done**: the CI seeds pass, and 100,000 by hand.

### 5. The planner's whole table

`SyncPlannerTableTest` (`syncTest/run/`): every per-file state, enumerated rather than sampled: local and remote each
absent, unchanged, or changed against an index entry present or absent, for a song, a setlist, and a picture (named by
a local song or not), alone and paired with a second file of another spelling of the name. For each, the plan must not
delete a file on a side where it differs from the index, delete anything the index never saw, delete one version on
both sides, delete a picture a local song names, or leave a file absent on a side where the other side changed it.
About 500 cases, in milliseconds. The 16 cases of `SyncPlannerTest` that the table covers stay as the readable
examples. **Done**: passing, with any state the table shows to be unplanned fixed in `SyncPlanner` first.

**Done** (2026-11-18): 5,580 cases in 0.1 s, a rename's other name included; the table found no unsafe plan, and
breaking the planner's rules one at a time fails it.

### 6. Explicit stops in the simulation

`SimulatedSharing` gains Stop sharing and Stop sharing everything on any device at any step, disconnects while a stop
is still journaled, and setlists as real setlist documents naming songs of the pool.

- **Properties**: once a stop returns stopped, no copy under that share id exists at any later step and no upload is
  ever made under it; a share stops only by a user's stop or a deletion of its item (the journaled stop a disconnect
  forgets is the one accepted edge, which the Disconnect confirmation names); a later share of the item gets a new id;
  settled, every live copy's entries are exactly the item, the songs it names that the folder holds, and the pictures
  and attachments those name within their budgets, compared as whole sets.
- **Expected to find**: a device whose run read the list before another device's stop uploading the copy again, where
  step 1 shows Dropbox writes it.
- **Done**: the CI seeds pass, and 100,000 by hand.

### 7. The two copy builders agree, and a copy holds nothing else

`SharedCopyParityTest` (`localTest/`, beside `ArchivePathParityTest`, the one test source set that wires the domain
and sync implementations over the real local sources, `ImportPathApp`, with an in-memory provider in place of
Dropbox): seeded generated libraries (songs showing pictures and naming attachments under and over the budget, files a
song names that the library lacks, setlists naming missing songs or songs under another spelling, plus songs, files,
setlists, and per-device overrides that nothing shared names), each shared through `CreateShareLinkUseCase` and then
synced.

- **Asserted**: the run uploads nothing (the export's first copy and `SharedCopySync.build`'s are the same bytes); the
  copy's entry list equals the oracle's set exactly (the item, the setlist's songs the library holds, `images/` for
  every file they name within the budgets) with nothing else; every song in it is byte-equal to the library's except
  for the lines of attachments left out; editing a song that nothing shared names changes no copy.
- **Expected to find**: little in the entries; a byte difference between the builders (an upload on the first run
  after every share) is the likelier find, a cost rather than a leak, fixed all the same.
- **Done**: 200 seeds in CI, 10,000 by hand.

### 8. The corpus gathers real backups (the owner)

Into `backups/<app>/` under the corpus folder, with a `sources.txt` there naming each file's app, version, platform,
and how it was made:

- **SongbookPro**: a whole-library `.sbpbackup` from Android and one from iOS, one of them with several hundred songs,
  sets, and folders; a `.sbp` of a set and one of a single song.
- **OnSong**: whatever its backup and its export of several songs produce. If that is not a zip of `.onsong` files, the
  format it is gets noted in `sources.txt` as a finding about the importer, not a test failure.
- **OpenSongApp**: an `.osb` backup with sets and folders. **OpenSong** (desktop): its `Songs` and `Sets` folders as
  they are on disk, zipped by the system's tool.
- **JSON**: every app's JSON export the owner can make.
- Across them, at least one with accented and non-Latin titles, two songs whose titles differ only by case, and two of
  one title.

**Done** when the files are in place and the next step's tests read every one of them.

### 9. The corpus's backups through the import

- **Now**: `ArchivePathParityTest` already takes them (by extension or by their bytes), fuzz mutants included, and is
  run with the corpus again before the release.
- **After the old path goes** (the streaming plan's last step): `BackupCorpusTest` (`localTest/`, skipped while
  `campfire.importCorpus` is blank, never in CI) holds each backup to two properties that need no stored expectation:
  - **nothing is dropped silently**: for SongbookPro and OpenSongApp, whose backups list their songs and sets
    explicitly, every one listed is in the import plan as a song or setlist or named among its problems;
  - **a round trip keeps everything**: imported into an empty library, exported whole with `ExportLibraryUseCase`, and
    imported into a second empty library, the two libraries hold the same files by hash.
- **Expected to find**: songs dropped without a problem named (a format variant the reader does not know), and names
  that do not survive the round trip (case-only twins, two songs of one title).
- **Done**: every backup passes, or its failure is fixed in the importer with a focused test built from a synthetic
  file that reproduces it (never the owner's file).

### 10. The end-to-end check on the live account (by hand)

After steps 2 to 7 and their fixes, with two desktop test homes and the Android emulator on one folder:

- a rename on one device while another edits the old name; most of the library deleted on one device, confirmed, while
  the others run; a library folder deleted on disk; the same song saved under two case spellings on macOS and Android;
- a song and a setlist shared, each link opened with no account and its zip's entry list compared with what was
  chosen; a song the setlist names edited, and the link serving the new copy after a run; a share stopped on one device
  while another's run is building its copy; Stop sharing everything; a disconnect with a stop still journaled.

**Done** when every line behaves as the package `CLAUDE.md` files say, any surprise has become a fake's rule and a
test, and the folder is restored by hash.

## Cost

- **CI**: about 5 s more per `desktopTest` run: the wider simulation about 3 s over today's 5.7 s, the planner table
  well under 0.1 s, and `SharedCopyParityTest` about 1 to 2 s. CI runs the unit tests twice (the main job and Windows),
  so about 10 s in all. `BackupCorpusTest` and the corpus half of the parity test never run in CI.
- **By hand**: 100,000 simulation seeds take about 15 minutes; each live session about half a day with the owner's
  hands for the Dropbox consents.

## Not in this plan

- **Every other feature stays on proportionate testing**: the importers of documents, ABC, and chord sheets (scored by
  `ImportCorpusTest` as today), the UI, the metronome, the tuner, the editor, printing and export beyond what a shared
  copy holds, cover art, and details lookup. None of them can delete the library or publish anything.
- **Synced preferences**: covered by `SyncedPreferencesTest` (58) and the simulation; they lose a setting at worst.
- **The streaming archive import's own testing**, which its plan carries (steps 11, 16, and 17).
- **A Google Drive provider**: its plan (`google-drive-sync.md`) brings its own provider tests; steps 3 and 4 make the
  simulation ready for its listings.
- **Committing the corpus or any real backup**, in any form.
