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

Implements `:data:repository:api` — all of it but `SyncRepository` and `DemoLibraryRepository`, which
`:data:sync:implementation` implements — on top of `:data:source:local:api` and, for the covers alone,
`:data:source:remote:api`. Koin wiring: `Module.kt` holds the `@Module @ComponentScan object DataRepositoryModule`, and every
repository is a `@Single`.

Nothing here prints: what fails without an exception is written to the injected `Logger` (`:data:model`), whose one
`@Single` is `DataRepositoryModule.logger()` for every module of the app, sync's included, and `base/recovering` is an
extension of it;
the tests hand a `RecordingLogger` to check that a failure was noticed and what it said.

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
`dropFromCache`, `keepOnlyInCache`). Only the preferences are persisted as a whole (`writeData`), through
`base/WholeDocumentRepository`, the subclass `UserPreferencesRepositoryImpl` extends and the list repositories do not,
so neither can replace its cached library with a list of its own making; the base gives it `currentState` and
`updateState` rather than its state flow, and `WholeDocumentRepositoryTest` covers the writes. What `writeData` publishes is `Idle` from the
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
- Sync lives in `:data:sync:implementation`, which reads the library through these repositories' API, holds
  `LibraryFileLock` and listens to `LibraryChanges` (both in `:data:source:local:api` now that two modules share them),
  and records the demo files (`DemoLibraryRepositoryImpl`); see its `CLAUDE.md`.
