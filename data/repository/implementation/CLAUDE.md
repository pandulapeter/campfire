<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :data:repository:implementation

Implements `:data:repository:api` on top of `:data:source:local:api` and — for sync alone —
`:data:source:remote:api`. Koin wiring: `Module.kt` holds the `@Module @ComponentScan object DataRepositoryModule`, and every
repository is a `@Single`.

`base/BaseLocalDataRepository<T>` holds all the caching logic — new repositories should extend it rather than
reimplement state handling. It owns a `MutableStateFlow<DataState<T>>` that starts as `Loading(null)` (nothing has been
read yet is not an error), reads the local source on the first `loadDataIfNeeded()`, and guards that read with a
`Mutex` so that the parallel loads in `LoadScreenDataUseCase` wait for each other instead of racing. A re-read keeps the
data already on screen in the `Loading` state, so a refresh never blanks the list, and a failed read becomes
`Failure(previous data)` rather than an empty list. A failure stays one through `updateData`, and the next
`loadDataIfNeeded()` reads again even though a change since has put data in the cache — otherwise a song created after
a failed scan would stand in for the whole library. A write that lands through `updateData` while a read is running
makes that read go again once it has published, since its directory listing may predate the file.

A load that arrives in pieces can publish them with `publishPartialData`, which is what the song scan does with each
batch of files it has parsed — a library of thousands then fills the list as it is read instead of showing nothing
until the last file. Partial data is only ever published while there is nothing on screen: during a re-read the
previous library is up, and replacing it with a partial one would make the list shrink and fill again under the user.
A read that fails or is cancelled falls back on the data from *before* it started rather than on whatever it had
published, or half a library would sit there looking like the whole of it and nothing would ever read the rest.
`commonTest` covers those rules, since none of them can be seen once a load has finished.

Writing is deliberately **not** part of that shape. Songs and setlists are one file each, so a change writes that file
and updates the one cached entry (`updateData`, which takes a transform of the current list and applies it atomically,
so a save landing during a rescan cannot overwrite what the rescan found). The cached lists are in no particular order — a scan leaves them by file
name, a write moves its item to the end — and ordering them is the domain layer's business. Only the preferences are persisted as a
whole (`writeData`). What `writeData` publishes is `Idle` from the first moment, never a `Loading` the write then
resolves: the data being written is already what every reader should show, and it stays that way even when the write
fails, while a `Loading` would tell whoever reads this state for "nothing has been read yet" exactly that — for as
long as the storage takes to answer. A cancelled read is not a failed one: it is rethrown and leaves the cached data as it was. A
`rescan()` — the refresh action, and the last step of every import — is the only thing that walks the directory again.

- `SetlistRepositoryImpl` makes every write to a setlist file — `updateSetlist`, `renameSetlist`, `saveSetlist`,
  `deleteSetlist` — under one lock, held from reading the setlist out of its own cache to having the write back in
  that cache, so a second change reads what the first one wrote, and a move or a deletion cannot cross a change that
  is halfway through. The cache is the one place that is current straight after a write; anything observing
  `setlists` catches up a few hops later. Creating and importing a setlist take it as well, from the storage finding
  a name free to the file being there under it.
- `SongRepositoryImpl` has the same kind of lock for the three writers that pick a free name before they write
  (`createSong`, `importSong`, `renameSong`): finding the name and writing under it are two trips to the storage, and a
  second asker in between is given the same name. Every change to either cached list replaces by file name and never
  appends, since the file name is what the lists key their rows by.
- `SongContentRepositoryImpl` is not a `BaseLocalDataRepository`: it is a keyed in-memory cache of song *texts*, so
  paging through a setlist re-reads nothing. Bulk readers (the library export) pass `shouldCache = false` so that
  walking the whole library does not leave all of it in memory. The editor invalidates one entry after a save. The
  lock only guards the map, never a read: a read that started before an invalidation is told so by a generation
  counter and does not put the text it read back into the cache. Every invalidation is also emitted on
  `invalidations`, which is how the ViewModel's own copies of the open texts learn that a sync run or a rescan has
  replaced the files under them.
- `SongRepositoryImpl.saveSong` takes an optional `expectedText`, the text an edit (a tag, a language) was built on:
  the file is only written while it still holds exactly that, and otherwise the save writes nothing, drops the cached
  text and returns false, so the caller rebuilds the edit on the file as it is now. The editor's explicit save passes
  none — the user has been asked, and the draft is the answer. `commonTest` covers the guard with a map-backed local
  source.
- `ArchiveRepositoryImpl` is a pass-through to the zip code in the local source implementation; it exists so the domain
  layer can reach it without depending on a local source.
- `sync/` is where local and remote meet, which is why it is in a repository rather than in either source.
  `SyncPlanner` is a **pure function** of (local hashes, remote listing, the index of what the last run saw) and is
  the one part of sync worth testing — `commonTest` covers every way a file can differ between two devices,
  including the ones that would otherwise only show up as a song someone lost. `SyncEngine` carries the plan out and
  applies `LibraryFileKind.matches` to both the remote listing and the index it loads, the same rule the local listing
  applies, because a file listed on one side only reads as a deletion. A download above `MAXIMUM_REMOTE_FILE_SIZE` is
  a per-file failure rather than filtered out of the listing for the same reason. A conflict's incoming version is
  written next to the local one *before* the local one goes up, and taken back if the service then says the remote
  file is still there, so the version that loses is never held only in memory. The engine is written so that an
  interrupted run leaves the library usable: the index (`SyncIndexDocument`, the on-disk shape of `sync-index.json`)
  is only told about a file once that file has actually moved, so anything half done simply looks unsynced next time.
  It is filed under the account's id as the service gives it (`SyncAccount.indexKey`), never under a name or an
  address the user can change; an index an earlier version filed under the e-mail address is adopted on the next run
  (`adoptedBy`) rather than ignored, since a run without an index brings back every file deleted since.
  It is told as the run goes rather than when a pass completes: every finished operation
  hands `SyncRepositoryImpl` a way to take a snapshot, which it does at most every `INDEX_WRITE_INTERVAL` —
  building one costs as much as the index is long — and once more on the way out of a stopped or failed run (the
  periodic writer waited for first, not cancelled: on the web a cancelled write carries on in the browser, so it could
  land after the final write). Those writes and the periodic one never throw (`saveIndexQuietly`): only the write
  that opens a run and the one that completes it do, which is how a device that cannot write ends a run as
  `SyncFailureReason.STORAGE`. The repository's scope carries a `CoroutineExceptionHandler` that logs, since nothing
  launched there has anyone to throw to. An interrupted run therefore keeps what it transferred, and only a completed
  one with no failed files moves `lastSyncedAt`.
  The library counts are kept moving during a run by a live rescan that waits five times what the previous one took
  (`liveRescanPauseAfter`), so that re-reading a large library never becomes most of what a run does.
  A run the app never came back from is found by the index's `isRunInProgress` marker at `restore`, reported as
  interrupted next time, and that run is left for the user to start: `RestoreResult.wasInterrupted` keeps
  `RestoreSyncUseCase` from starting one on launch, which would replace the message before it could be read.
  `restore` is asked once per ViewModel — on Android once per activity — so a call that finds the state already
  `Connected` answers from it and reads nothing: read again from the disk, a run that is going would look like one
  that was interrupted, and its marker would be cleared under it.
  `commonTest` runs the engine against an in-memory `SyncProvider` and `LibraryFileLocalSource` for the behaviour the
  planner's tests cannot show, and `SyncRepositoryImplTest` runs the repository against the same fakes plus the ones
  in `FakeSyncCollaborators.kt`. A plan whose local deletions are more than half of the index (at least
  `MIN_DELETIONS_TO_ASK` of them) or the whole of it is not applied under `SyncDeletionPolicy.ASK`: the engine returns
  `Result.DeletionsNeedConfirmation` before anything moves, and the repository reports it as the run's outcome without
  a rescan. `DELETE_LOCALLY` applies such a plan as it is, and `KEEP_AND_UPLOAD` first drops the index entries of
  every file that is here and not there, which the planner then reads as new local files. The policy is a parameter
  of the one run it was given to, never state. A failure on one file does not end a run; only the three failures
  that make every further call pointless (the credentials refused, the service unreachable, the remote folder full)
  do — and a `CancellationException` is caught *first* and rethrown, since a stopped run is not a few
  hundred files that failed. A file that failed is named in `SyncSummary.failed`, and a run that has any does not
  move `lastSyncedAt`. Operations run `CONCURRENT_TRANSFERS` at a time within each ordering group rather than
  one after another: every one of them is a request, and serialising them made a first sync as slow as the round
  trip times added up. `SyncRepositoryImpl` owns an application-lifetime scope, so a run outlives the screen and
  (on Android) the activity that started it, and it is what tells the song and setlist repositories to rescan
  afterwards — after a completed run that changed something, and after a stopped or failed one in which any
  operation had finished (`finishRunCutShort`, always under `NonCancellable`), since files that moved before the run
  ended are on disk whichever way it ended. The use case cannot, now that it returns before the run does.
  Disconnecting cancels a run that is still going and waits for it, and deletes the index under the run lock, so that
  a run stopped a moment earlier has finished writing it, and a run only ever writes its outcome into a state that is
  still `Connected`: a run that outlived the account it ran against must not bring that account back on screen.
  `cancelConnection` is the way out of `Connecting` that does not need the `connect()` that got there to be running
  still — on the web it never is, and a page restored from the back/forward cache is otherwise connecting for good.
  `connect` never throws anything but a cancellation — every way out of it leaves `Connecting`, and a clean-up the
  storage refuses is logged rather than allowed to replace the outcome. `restore` never throws
  for a service that refuses the stored credentials — the app starts disconnected and says so — and a redirect that
  no authorization is waiting for is ignored, the stored account restored as usual. It shows the account from what is
  stored and asks the service behind that, so a slow network never makes a connected account look disconnected; a
  refusal that arrives later takes the connection down then.
