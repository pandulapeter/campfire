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

Implements `:data:repository:api` on top of `:data:source:local:api` and — for sync and the covers alone —
`:data:source:remote:api`. Koin wiring: `Module.kt` holds the `@Module @ComponentScan object DataRepositoryModule`, and every
repository is a `@Single`.

`DocumentRepositoryImpl` is stateless, a pass-through to `DocumentLocalSource`, like `ArchiveRepositoryImpl`:
extraction is a one-shot import operation, not another cached library. Conversion belongs to the domain/chordpro
layers, and no original document enters the repository's stored state.

`base/BaseLocalDataRepository<T>` holds all the caching logic — new repositories should extend it rather than
reimplement state handling. It owns a `MutableStateFlow<DataState<T>>` that starts as `Loading(null)` (nothing has been
read yet is not an error), reads the local source on the first `loadDataIfNeeded()`, and guards that read with a
`Mutex` so that the parallel loads in `LoadScreenDataUseCase` wait for each other instead of racing. A re-read keeps the
data already on screen in the `Loading` state, so a refresh never blanks the list, and a failed read becomes
`Failure(previous data)` rather than an empty list. A failure stays one through `updateData`, and so does a `Loading`
— a change made while the first scan is going, or before it, is not the finished library, and an `Idle` is what the
cover cache's prune and the launch's deep link wait for. The next
`loadDataIfNeeded()` reads again even though a change since has put data in the cache — otherwise a song created after
a failed scan would stand in for the whole library. A write that lands through `updateData` while a read is running
makes that read go again once it has published, since its directory listing may predate the file — once: the changes
that land during that second read are applied onto its result instead, since a sync run refreshes the files it wrote
about once a second and a scan slower than that would otherwise never finish. Every `updateData` transform replaces or
drops the entries of the files it names with what is on disk, so applying one again is harmless.

A load that arrives in pieces can publish them with `publishPartialData`, which is what the song scan does with the
batches it has parsed — a library of thousands then fills the list as it is read instead of showing nothing
until the last file. Partial data is only ever published while there is nothing on screen: during a re-read the
previous library is up, and replacing it with a partial one would make the list shrink and fill again under the user.
Every batch has the changes `updateData` recorded since the read started applied onto it, or a song changed while the
list fills would revert on screen until the read ends.
A read that fails or is cancelled falls back on the data from *before* it started rather than on whatever it had
published, or half a library would sit there looking like the whole of it and nothing would ever read the rest.
For a first read that is cancelled that is `Loading(null)`, the state the repository starts in, and
`loadDataIfNeeded()` reads again whenever it finds a `Loading` state — under the lock it runs in, one can only be what
an unfinished read left behind.
`commonTest` covers those rules, since none of them can be seen once a load has finished.

Writing is deliberately **not** part of that shape. Songs and setlists are one file each, so a change writes that file
and updates the one cached entry (`updateData`, which takes a transform of the current list and applies it atomically,
so a save landing during a rescan cannot overwrite what the rescan found). The cached lists are in no particular order
— a scan leaves them by file name, a write moves its item to the end — and ordering them is the domain layer's
business. The two lists share those transforms through `base/LibraryListRepository` (`putInCache`, `replaceInCache`,
`dropFromCache`, `keepOnlyInCache`). Only the preferences are persisted as a whole (`writeData`). What `writeData` publishes is `Idle` from the
first moment, never a `Loading` the write then resolves: the data being written is already what every reader should
show, and it stays that way even when the write fails, while a `Loading` would tell whoever reads this state for
"nothing has been read yet" exactly that — for as long as the storage takes to answer. The publish also comes before
the storage is waited for, since every caller builds its change on the state it finds; the writes then go through a
lock of their own one at a time, each writing what is published by then rather than what it was called with, so the
last change is the last thing on disk and a burst of changes ends in one write. A write that succeeded publishes
nothing more, and one that failed turns the state into a `Failure` only while its data is still the data on show.
A change to one preference goes through `transformAndWriteData` (`updateUserPreferences`) rather than a whole
document the caller built: the transform is applied to the state atomically, since a caller's copy of the state — the
ViewModel's is a few `stateIn` hops downstream — may not have the previous change in it yet, and saving a document
built on it would undo that change. A transform that changes nothing publishes and writes nothing. `saveUserPreferences`
is left for writing the document as it is. `commonTest` covers this too. A cancelled read is not a failed one: it is rethrown and leaves the cache with what it
held before, plus any change that landed while it ran. A `rescan()` — the refresh action — is the only thing that walks the
directory again; an import ends with `adoptImported`, which puts the songs and setlists it wrote into the list in one
change, dropping the cached texts of the songs among them. `refresh(fileNames)` reads the named files alone, for a
sync run that changed them: each is put in the list in place of its old entry or drops out, as a rescan would drop it,
and a repository that has not been read yet rescans instead, since there is no list to put them into. It reads them
and updates the list under `LibraryFileLock`, which every write holds from its write to its cache update, so a save or
a deletion of one of those files lands before its reads or after its update and is never undone by it.

- `SetlistRepositoryImpl` makes every write to a setlist file — `updateSetlist`, `renameSetlist`, `saveSetlist`,
  `deleteSetlist` — under one lock, held from reading the setlist out of its **file** to having the write back in the
  cache (and `LibraryFileLock` inside it for the same span), so a second change reads what the first one wrote, and a
  move or a deletion cannot cross a change that is halfway through. The file and not the cache, because sync writes setlist files behind this repository's back and the
  cache only catches up at the run's next refresh: a change built on it in between would put the version from before the run
  back, and the next run would upload that over the other device's edit. A file that is gone drops the setlist from
  the cache and changes nothing; one that cannot be decoded is changed as the cache has it. The cache is the one place that is current straight after a write; anything observing
  `setlists` catches up a few hops later. Creating and importing a setlist take it as well, from the storage finding
  a name free to the file being there under it. The write and the cache update under that lock run as one
  `NonCancellable` step, so a change whose screen goes away while it is being written is still in the cache the next
  change reads; the lock itself is waited for cancellably. `loadSetlistFileNamesNaming` reads every setlist file afresh
  for the reference walks, outside the locks — `updateSetlist` reads each one again under them. No read writes
  anything: the local source lists an undated file with today's day in memory (`ParsedSetlist.isDated` false), and
  `writeDays`, after `loadSetlistsIfNeeded` and `rescan` have published, saves each such file through `latest` under
  both locks — from a fresh read, so a download or an edit that landed since the listing is what gets the day, not
  the listing's older copy. It announces nothing. It never runs inside the read itself, since `latest` takes the read
  lock while it holds the other two.
- `SongRepositoryImpl` has the same kind of lock for the three writers that pick a free name before they write
  (`createSong`, `importSong`, `renameSong`): finding the name and writing under it are two trips to the storage, and a
  second asker in between is given the same name. Every write, `saveSong`'s guard included, also holds
  `LibraryFileLock`, which is what keeps a sync run from writing the file between the guard and the write. Its writes
  (`createSong`, `renameSong`, `deleteSong`, and `saveSong` once its guard has passed) change the file and the cached
  list as one `NonCancellable` step as well, the locks themselves still being waited for cancellably. Every change to either cached list replaces by file name and never
  appends, since the file name is what the lists key their rows by.
- `EditorDraftRepositoryImpl` is not a `BaseLocalDataRepository` either, and holds no cache: the draft is read once
  per start and written on every pause, under a lock so that a pause's write and the deletion that follows a save
  land in the order they were asked for.
- `CoverArtRepositoryImpl` is not a `BaseLocalDataRepository` either: a cover is read from the device's copy (named by
  the SHA-256 of its address) and otherwise downloaded and written, in a scope of the repository's own rather than the
  caller's, so that a row scrolled out of view, which cancels its request, does not throw away a download another row
  is waiting for. Callers asking for one address at once share one download. At most `MAX_CONCURRENT_DOWNLOADS` (4)
  reach the network at a time, and one whose every caller has gone before it gets its turn is not made and records no
  failure: a fling through a library whose covers are not on the device yet would otherwise queue a request per row in
  the HTTP client, whose timeout counts the queueing, and the rows it stops on would come last. An address that answered with something
  that is not a cover is not asked again for the session, and one that could not be reached not for a minute, so a
  dead address costs one request rather than one per scroll. It watches `SongRepository.songs` and, whenever a
  library read has finished (`Idle`, never the `Loading` batches of a scan, which would prune the covers of the songs
  not read yet) with a different set of addresses, deletes every copy no song names — a search result's thumbnail,
  which is fetched the same way, included. The search runs every `CoverArtSearchRemoteSource` side by side in a
  `channelFlow`, so cancelling the collection stops them all, and appends each one's candidates in the order they
  answer — the grid grows at its end rather than shifting under the user. `coverArtCacheSize` lists the copies again
  after every write and prune, off a counter in a `StateFlow`, whose conflation keeps a burst of downloads to one
  listing at a time. `commonTest` covers the sharing, the bound and the downloads it skips, the failures, the
  pruning, the cache size and the search's order of emissions.
- `SongContentRepositoryImpl` is not a `BaseLocalDataRepository`: it is a keyed in-memory cache of song *texts*, so
  paging through a setlist re-reads nothing. It keeps the 32 most recently used, and at most a million characters of
  them — a long session would otherwise hold every song it opened, and a few songbooks pasted into one file each
  would hold the rest; a text larger than all of that is handed out and not kept. The exports and the import's check
  for a song already there pass `useCache = false`, which reads the file itself and neither answers from the cache
  nor fills it: they have to see what is on disk now, and walking the library through the cache would leave all of it
  in memory. The editor invalidates one entry after a save. The lock only guards the map, never a read: a read that
  started before an invalidation is told so by a generation counter and does not put the text it read back into the
  cache. `invalidations` is that counter as a `StateFlow`, which is how the ViewModel's own copies of the open texts
  learn that a sync run or a rescan has replaced the files under them; it re-reads all of them on every new value.
  A state rather than one event per file, since a collector busy reading while a burst of them arrives would lose
  events that did not fit a buffer, and with them a copy that nobody then re-read.
- `SongRepositoryImpl.saveSong` takes an optional `expectedText`, the text an edit (a tag, a language) was built on:
  the file is only written while it still holds exactly that, and otherwise the save writes nothing, drops the cached
  text and returns false, so the caller rebuilds the edit on the file as it is now. The editor's explicit save passes
  none — the user has been asked, and the draft is the answer. `commonTest` covers the guard with a map-backed local
  source.
- `ArchiveRepositoryImpl` is a pass-through to the zip code in the local source implementation; it exists so the domain
  layer can reach it without depending on a local source.
- `sync/` is where local and remote meet, which is why it is in a repository rather than in either source.
  `SyncRepositoryImpl` is a facade over the classes that do the work, each a `@Single` of the `sync` package:
  `SyncStateHolder` (the one `SyncState`), `SyncConnectionManager` (restore, connect, disconnect, forgetting),
  `SyncRunScheduler` (when a run starts, the debounce), `SyncRunner` (one run: the engine, `preferences.json`, the index
  writes, the outcome), `SyncIndexStore` (`sync-index.json` as a document) and `SyncLibraryRefresher` (the song and
  setlist repositories told what a run changed). `SyncEngine` is built by `DataRepositoryModule.syncEngine`. Who holds
  which lock:
  - `SyncConnectionManager`'s restore lock — never held across a run; forgetting the stored connection runs under it.
  - `SyncRunner`'s run lock (`withRunLock`) — held by a run, and by `disconnect` around the index deletion only.
  - `LibraryFileLock` — held by the engine around local file calls, and by the song and setlist repositories inside
    their own locks; never held across a request.
  - `SyncLibraryRefresher`'s `changedFilesMutex` — innermost: the engine reports a changed file both inside
    `LibraryFileLock` and outside it, so it is taken under `LibraryFileLock` and must never be held while anything
    takes `LibraryFileLock` — which is why the refresh lets go of it before it calls `refresh`.
  `SyncPlanner` is a **pure function** of (local hashes, remote listing, the index of what the last run saw) and is
  the one part of sync worth testing — `commonTest` covers every way a file can differ between two devices,
  including the ones that would otherwise only show up as a song someone lost. `SyncEngine` carries the plan out and
  applies `LibraryFileKind.matches` to both the remote listing and the index it loads, the same rule the local listing
  applies, because a file listed on one side only reads as a deletion. Names are matched by case where a service
  ignores it, and by Unicode form, which the file systems disagree about: a remote name that differs from a local one
  only by case or by form takes the local spelling
  (`foldRemoteNamesOntoLocal`), and an index entry whose name neither listing has moves to the one listed spelling
  that folds to it (`foldIndexNamesOntoListings`). Without the second, a song moved to another spelling of its own
  name was downloaded again after it was deleted. A download above `MAXIMUM_FILE_SIZE` (the largest
  file an import reads, `ImportLimits.MAX_TEXT_FILE_SIZE`) is a per-file failure rather than filtered out of the
  listing for the same reason. A local file larger than a run downloads is not read or uploaded either: it is left out
  of the plan on both sides — its index entry too, so that it is not taken for a deletion — and named among the run's
  failures. So is a remote file whose name this device's file system cannot hold (`LibraryFileLocalSource.canHoldFileName`:
  no for a name that is a path on every platform, and for `? : * " < > |` on Windows), split off before the remote
  names are folded onto the local ones so that it is never matched onto a local file either. So is a local file whose
  read fails: it is folded onto and left out like a too-large one, but its index entry is kept — the planner just does
  not see it — so the run that can read it again decides as usual; a library of which no file can be read ends the
  run instead. A conflict's incoming version is written next to the local one *before* the local
  one goes up, under a name free on both sides: the pass's remote listing is passed to the free-name search, since a
  file under the copy's name that has not come down yet would otherwise be taken for the copy changed here. The copy
  is taken back if the service then says the remote file is still there, so the version that loses is never held only
  in memory. A setlist is the exception: one whose two versions differ only in the day they name — which every device
  gives an undated setlist on its own, the day it first reads it — or where this device's only change is the day its
  read gave an undated file (the index version, re-encoded with no day, is the local one's), takes the cloud folder's
  version with no copy, written only over the bytes it was compared with (`ConflictResolver.resolveWith`, `takeRemote`,
  built by the engine with its own collaborators and never by Koin). The engine
  never sees the setlist format for it: `SetlistComparison` (`:data:source:local:api`) answers both questions. A day
  set on purpose on two devices offline loses to the folder's. A demo file this device planted is the other exception:
  one that still hashes to what `UserPreferences.demoLibraryContentHashes` recorded when it was planted, met with no
  index entry (the first time this device compares that name with this folder), takes the folder's version the same
  way, since it is another version's demo; the record is written by `DemoLibraryRepositoryImpl`, under the same
  `SyncKey.path` and `localContentHash` the engine looks it up by, and the engine asks for it through the lookup
  `DataRepositoryModule.syncEngine` builds it with — a `@Single` function rather than an annotated class, since the
  Koin compiler plugin would hand an annotated constructor its `{ null }` default instead — and one with an index entry that is back at its planted bytes was changed back on purpose and keeps its copy. A
  device that planted before the record existed has none and still makes copies. A download is decided about twice — before its request, so that a file already in step is
  not transferred, and again just before the write, so that a save made while the request was in flight is resolved
  as a conflict rather than written over; the index records the revision the download fetched rather than the one the
  listing named, so a file another device wrote again in between is not this device's next edit's conflict, and a
  conflict is uploaded over the revision it was compared with. That second check and the write, and every other change the engine makes to
  a local file (a local deletion and the check before it, a conflict copy, taking a copy back), happen under
  `LibraryFileLock`, a `@Single` the song and setlist repositories hold around their own writes from whatever they
  check to the write, so that a save lands either before the engine's check, which then sees it, or after its write,
  and is never overwritten or deleted with no conflict to show for it. It is only ever held around local file
  calls, never a request, and is not reentrant: the repositories take their own locks first and this one inside
  them, and nothing that holds it calls anything that takes it. The engine is written so that an
  interrupted run leaves the library usable: the index (`SyncIndexDocument`, the on-disk shape of `sync-index.json`)
  is only told about a file once that file has actually moved, so anything half done simply looks unsynced next time.
  It is filed under the account's id as the service gives it (`SyncAccount.indexKey`), never under a name or an
  address the user can change; an index an earlier version filed under the e-mail address is adopted on the next run
  (`adoptedBy`) rather than ignored, since a run without an index brings back every file deleted since.
  It is told as the run goes rather than when a pass completes: every finished operation
  hands `SyncRunner` a way to take a snapshot, which it does at most every `INDEX_WRITE_INTERVAL` —
  building one costs as much as the index is long — and once more on the way out of a stopped or failed run (the
  periodic writer waited for first, not cancelled: on the web a cancelled write carries on in the browser, so it could
  land after the final write). Those writes and the periodic one never throw (`SyncIndexStore.saveQuietly`): only the write
  that opens a run and the one that completes it do, which is how a device that cannot write ends a run as
  `SyncFailureReason.STORAGE`. Reading it is the same: an index that is there and cannot be read ends the run as
  `STORAGE` before anything is written, and start up and a new connection leave such a file alone. Only one that reads
  and does not decode is taken for none. The scope, the time source, the wall clock and the dispatcher documents are
  coded on come from `base/RepositoryEnvironment` (a `@Single` function of `DataRepositoryModule`, shared with
  `CoverArtRepositoryImpl`), which is what lets `SyncRepositoryImplTest`, `SyncRunSchedulerTest`,
  `SyncLibraryRefresherTest` and `CoverArtRepositoryImplTest` run every
  launched job, the ten-second debounce and the retry minute on the test scheduler's virtual time — the scope being
  background work there, so a test advances it with `runCurrent` or `advanceTimeBy`, never `advanceUntilIdle`. The
  repository's scope carries a `CoroutineExceptionHandler` that logs, since nothing
  launched there has anyone to throw to, and a run that ends in a throwable that is not an `Exception` (a synchronous
  `js(...)` failure on the web, a real `Error`) is finished and reported like a failed one rather than left to it. The failures that are only worth a line in the
  log — a clean-up, a quiet index write, a file that could not be read — go through `base/recovering`, which rethrows a
  cancellation, logs and falls back on any other `Exception` and catches nothing else; a block with an extra typed arm
  or work to do on cancellation keeps its own `try`. An interrupted run therefore keeps what it transferred, and only a completed
  one with no failed files moves `lastSyncedAt`.
  The engine reports every library file it changes (`onLocalFileChanged`), once the change is on disk, and the library
  counts are kept moving during a run by a live refresh of those files that waits five times what the previous one
  took (`liveRescanPauseAfter`), so that re-reading what a run wrote never becomes most of what it does.
  A run the app never came back from is found by the index's `isRunInProgress` marker at `restore`, reported as
  interrupted next time, and that run is left for the user to start: `RestoreResult.wasInterrupted` keeps
  `RestoreSyncUseCase` from starting one on launch, which would replace the message before it could be read. Only a
  run somebody asked for, or a launch started, is treated so: the marker also says whether the run was an automatic
  one (`isAutomaticRunInProgress`), and one of those — which being swiped away right after an edit routinely cuts
  off — is cleared without a word and followed by the ordinary launch run, which carries the same changes.
  **Automatic runs wait for the library to settle**: `scheduleSynchronization` — asked for by every change the app
  makes to a song or setlist file, which the two repositories announce through `LibraryChanges` —
  sets when the run is due, ten seconds after the latest request (`AUTOMATIC_RUN_DELAY`), and one `collectLatest` over
  that moment is the debounce: a request that lands while the previous one is still waiting moves the start rather than
  adding a run. A request made while a run is going is carried out after it, since the run may have read the file
  before the change. The files a run writes itself go around the repositories, so no run schedules the next one.
  `synchronize` — the button, a new connection and a launch — takes the place of a waiting run, `cancelSynchronization` drops it with the run it
  stops, `disconnect` drops it too, and `startScheduledSynchronization` starts it at once, which the app asks for as
  it leaves the front, the last moment a phone lets it start a run that survives the background. It starts the run
  on the caller's thread rather than leaving it to the debounce, and answers the progress: `startRun` puts
  `SyncProgress()` in the state before it returns — and a completion handler takes it down again for a run cancelled
  before its body could — so the app can hand the run to the platform's keep-alive inside the same callback. The run slot is an
  `AtomicReference` swapped with `compareAndSet`, since the buttons start runs from the main thread and the debounce
  from the repository's own scope.
  `restore` is asked once per ViewModel — on Android once per activity — so a call that finds the state already
  `Connected` answers from it and reads nothing: read again from the disk, a run that is going would look like one
  that was interrupted, and its marker would be cleared under it.
  `commonTest` runs the engine against an in-memory `SyncProvider` and `LibraryFileLocalSource` for the behaviour the
  planner's tests cannot show, and `SyncRepositoryImplTest` runs the repository against the same fakes plus the ones
  in `FakeSyncCollaborators.kt`. The pure parts of a run are tested on their own: `preparePass` (`PassListing.kt`,
  `PassListingTest`) works out what one pass may touch from the two listings and the index, and `DeletionGuard`
  (`DeletionGuardTest`) is the question below; the engine threads one `SyncRun` and one `SyncPass` (`SyncRun.kt`)
  through its steps instead of their values one by one. A plan whose deletions on one side are more than half of the index (at least
  `DeletionGuard.MIN_DELETIONS_TO_ASK` of them) or the whole of it is not applied under `SyncDeletionPolicy.ASK`, and neither is one
  that would delete anything remotely while the local listing is empty and the index is not — a library folder that
  went missing lists as empty on every platform, however small the library was. The engine returns
  `Result.DeletionsNeedConfirmation` with the `SyncDeletionDirection` before those deletions move, asking about this
  device first, and the repository reports it as the run's outcome, refreshing whatever an earlier pass of the same
  run had already moved. `DELETE_LOCALLY` and `DELETE_REMOTELY` apply such a plan as it is;
  `KEEP_AND_UPLOAD` first drops the index entries of every file that is here and not there, which the planner then
  reads as new local files — and drops those songs from the last synced `preferences.json` too, so that their
  overrides travel back with them rather than following a folder another device emptied — and `KEEP_AND_DOWNLOAD` those of every file that is there and not here, which it reads as
  new remote ones. An answer waives the guard of its own direction only, so a run told to delete here still stops if it
  would also empty the cloud folder. The policy is a parameter of the one run it was given to, never state. A failure on one file does not end a run; only the three failures
  that make every further call pointless (the credentials refused, the service unreachable, the remote folder full,
  the subclasses of the sealed `SyncRunEndingException`, whose `reason` is what the run is reported as) do — and a `CancellationException` is caught *first* and rethrown, since a stopped run is not a few
  hundred files that failed. A file that failed is named in `SyncSummary.failed`, and a run that has any does not
  move `lastSyncedAt`. So is a file the service still reported as contested in the last of the `MAXIMUM_PASSES`
  passes (another device writing it under every upload): the two sides still differ, and the run did not settle it. Operations run `CONCURRENT_TRANSFERS` at a time within each ordering group rather than
  one after another: every one of them is a request, and serialising them made a first sync as slow as the round
  trip times added up. The remote deletions are the exception: they go to `SyncProvider.delete` in one call, so that
  a large deletion spends as little time as possible half done, which is what another device's guard would see. On
  Dropbox that is still about seven files a second, so a device that syncs during a large approved deletion can
  still see less than half of it gone and follow that part without asking; its next run asks about the rest. `SyncRunScheduler` owns an application-lifetime scope, so a run outlives the screen and
  (on Android) the activity that started it, and `SyncLibraryRefresher` is what tells the song and setlist
  repositories to read the files it changed again (`refresh`, never a whole rescan) — after a completed run, and after a stopped or failed one
  (`finishRunCutShort`, always under `NonCancellable`), since files that moved before the run ended are on disk
  whichever way it ended. A live refresh that is stopped puts back what it had not read. Only a run that ends in a
  throwable that is not an `Exception`, which may have come from the middle of a write, rescans the whole library. The use case cannot, now that it returns before the run does. A run
  waits for a first read of the library that is still going before it reads any local file, so that a connected
  launch does not read every file twice at once; a repository that has been read, or has failed, is not waited for.
  Disconnecting cancels a run that is still going and waits for it; once it has begun to take the connection apart it
  is carried to the end whoever cancels its caller, and a run that finds the state `Connected` but no provider
  connected turns it into `ConnectionFailed` rather than doing nothing, so an account whose credentials are gone never
  stays on screen with a button that cannot work. A run the service refuses (`SyncAuthorizationException`, which the
  provider only throws once a renewal has been refused) ends the same way, rather than as a failed outcome under the
  account: its only way on would be Disconnect, which deletes the index, while Connect keeps the index for the same
  account. Backing out of that Connect — the consent page closed, the waiting given up — returns to the failed state
  rather than to disconnected while the provider still holds the refused credentials, since the next launch restores a
  connection from them; a failure that stored none backs out to disconnected. Disconnecting deletes the index under the run lock, so that a run stopped a
  moment earlier has finished writing it, and a run only ever writes its outcome into a state that is still
  `Connected`: a run that outlived the account it ran against must not bring that account back on screen.
  **`preferences.json` is settled after the files**, by `SyncedPreferencesSync`, and only after a run the engine
  completed: the songs it may name are then the ones the run left on both sides. `SyncedPreferencesDocument` keeps it
  as a JSON tree and merges it three ways, value by value, against the document the last run settled
  (`SyncIndexDocument.syncedPreferences`, carried through every snapshot the engine hands out and dropped with the rest
  of an index written for another account); this device's side is that base with this version's own fields replaced
  by the preferences (`localDocument`), so whatever a later version writes passes through untouched — a value of one of
  those fields that this version cannot read (a tempo of 400, a capo of 13) included, like an unknown field. A document that
  is missing from the folder, does not decode or holds no `songs` object (`isReadable`) is merged as the base, so it
  is replaced with this device's values rather than read as a removal of everything; one whose `version` is newer
  than this version's (`isNewerFormat`) is neither applied nor written over, and the step answers the base it was
  given (an empty one where there was none), so the run still counts as successful. A song no longer
  in the library — compared by case and Unicode form, the run's failed files counting as there, and a song the folder's
  document has gained since the last synced one counting as there too, since it may have reached the folder after the
  run listed it — is dropped from the merged document before it is applied. Entries are matched to songs by case and Unicode form too, and applied under
  this device's spelling of the file, which is the one its screens read; the document keeps one spelling per song,
  the first in sort order of those the folder's document (then the base) already holds, all three sides being put on
  it before the merge, so two devices that spell one file differently share its overrides. The preferences are changed before the upload, so a failed upload only leaves
  the next run a change to carry; a value changed here while the merge ran is kept (`SyncedPreferences.applyTo`). A
  conflict merges again against the document it lost to, once; a document that still cannot be settled, or any
  failure but the authorization's, is reported as `SyncSummary.havePreferencesFailed` (which, like a failed file, keeps
  the run from counting as the last successful one) and leaves the base where it was.
  The player's chord shapes (`UserPreferences.chordVoicings`) are a `chords` member beside `songs`, by instrument and
  then by the chord's id, merged the same way value by value; a shape is any string and an instrument this version does
  not know is kept, a non-string value passes through, nothing in it is ever dropped with a song, and the member is only
  written once there is something in it, so a document from before it stays the same bytes.
  `localChanges` is what schedules a run when the three maps or the chord shapes change, filtering out the values the
  step wrote itself.
  `SyncedPreferencesTest` covers the merge, the document and the step against the fakes.
  `cancelConnection` is the way out of `Connecting` that does not need the `connect()` that got there to be running
  still — on the web it never is, and a page restored from the back/forward cache is otherwise connecting for good.
  `connect` never throws anything but a cancellation — every way out of it leaves `Connecting`, and every way out of it
  that does not end connected forgets whatever the provider stored during the token exchange
  (`forgetStoredCredentials`, no request), so an authorization given up after the tokens arrived is not a connection
  the next launch restores and syncs. A clean-up the storage refuses is logged rather than allowed to replace the outcome. `restore` never throws
  for a service that refuses the stored credentials — the app starts disconnected and says so — nor for credentials
  the device cannot read right now, which it reports as a connection failure with a storage reason and leaves alone,
  and a redirect that
  no authorization is waiting for is ignored, the stored account restored as usual. It shows the account from what is
  stored and asks the service behind that, so a slow network never makes a connected account look disconnected; a
  refusal that arrives later takes the connection down then. A forgetting of a previous installation's credentials
  that failed is noted (`SyncIndexLocalSource.setForgettingCredentialsOwed`) and retried by every `restore` before
  anything else is read; while it keeps failing, `restore` answers disconnected — not a storage failure like
  unreadable credentials, since what cannot be removed belongs to an earlier installation, and connecting again, which
  writes over it and crosses the note off, is the right answer to it.
