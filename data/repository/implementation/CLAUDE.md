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

Implements `:data:repository:api` except `SyncRepository` and `DemoLibraryRepository` (`:data:sync:implementation`),
over `:data:source:local:api` and, for covers and the song details lookup only, `:data:source:remote:api`. Every
repository is a `@Single`.

Nothing prints: failures without an exception go to the injected `Logger`, whose one `@Single`
(`DataRepositoryModule.logger()`) serves every module, sync included. Tests use `RecordingLogger`.

## `base/BaseLocalDataRepository` — read rules

New cached repositories extend it rather than reimplement state handling. `commonTest` covers these rules, none of
which is visible once a load has finished:

- Starts as `Loading(null)`; the first `loadDataIfNeeded()` reads under a `Mutex`, so parallel loads wait, not race.
- A re-read keeps the data on screen (`Loading(data)`); a failed read is `Failure(previous)`, never an empty list.
- `Failure` and `Loading` survive `updateData`: a change made during or before the first scan is not the library.
  The next `loadDataIfNeeded()` reads again even though a change put data in the cache, and also whenever it finds
  `Loading` (under the lock that can only be an unfinished read). `Idle` is what the cover prune and the launch's deep
  link wait for.
- A write landing via `updateData` during a read makes that read go again once (its listing may predate the file);
  changes during the second read are applied onto its result instead, or a sync run refreshing every second would
  keep a slow scan from ever finishing. `updateData` transforms replace or drop the named files' entries from disk, so
  reapplying one is harmless.
- `publishPartialData` (the song scan's batches) publishes only while nothing is on screen, never during a re-read
  (the list would shrink and refill). Each batch gets the changes recorded since the read started.
- A failed or cancelled read falls back on the data from *before* it started, not what it published. Cancellation is
  rethrown, keeping the old data plus changes that landed meanwhile.

## Writes

- Songs and setlists: a write changes one file and its cached entry through `updateData` (an atomic transform, so a
  save during a rescan is not overwritten). Shared transforms in `base/LibraryListRepository`; every change replaces by
  file name, never appends (rows key by file name). Cached lists are unordered — ordering is the domain's job.
- Preferences only are written whole, via `base/WholeDocumentRepository` (the list repositories cannot extend it, so
  neither can replace its library with a list of its own). `writeData` publishes `Idle` at once (never a `Loading`,
  which readers take as "nothing read yet"), before waiting on storage; writes are serialized and each writes what is
  published by then, so a burst ends in one write and the last change is last on disk. A failed write becomes
  `Failure` only while its data is still on show.
- One preference changes through `transformAndWriteData` (`updateUserPreferences`), applied atomically to the state:
  the caller's copy (the ViewModel's is several `stateIn` hops behind) may miss the previous change. A no-op
  transform writes nothing.
- `refresh(fileNames)` reads the named files and updates the list under `LibraryFileLock`, which every write holds from
  file write to cache update, so a concurrent save or delete is never undone by it. Unread repository → rescan.
- File write plus cache update is one `NonCancellable` step (a screen closing mid-write still leaves the cache right);
  locks are waited for cancellably.

### `SetlistRepositoryImpl`

- Every write (`updateSetlist`, `renameSetlist`, `saveSetlist`, `deleteSetlist`, create, import) holds one lock, plus
  `LibraryFileLock`, from reading the setlist **from its file** to the cache update. The file, not the cache: sync
  writes setlists behind the repository's back, and a change built on the stale cache would put the old version back
  and upload it over another device's edit. A gone file drops the setlist and changes nothing; an undecodable one is
  changed as cached.
- **Every setlist has a date**: an import dates a dateless setlist with today (the demo's included) unless it replaces a
  library setlist, whose day it keeps. No read writes: an undated file is listed with today's day in memory
  (`ParsedSetlist.isDated` false). `writeDays` saves those days after `loadSetlistsIfNeeded` / `rescan` publish, from a
  fresh read under both locks, announcing nothing, so the next sync carries them; never inside the read itself (`latest`
  takes the read lock while holding the other two).
- `loadSetlistFileNamesNaming` reads every file afresh, outside the locks, for the reference walks.

### `SongRepositoryImpl`

- `createSong`, `importSong`, and `renameSong` share a lock: finding a free name and writing are two storage trips.
  Every write holds `LibraryFileLock` (so sync cannot write between `saveSong`'s guard and the write).
- `saveSong(expectedText)` writes only while the file still holds exactly that text; otherwise it writes nothing, drops
  the cached text, and returns false so the caller rebuilds the edit. The editor's explicit save passes none.
- `editSongs` reads and writes each file from disk under `LibraryFileLock`, carries on past failures (naming them), then
  re-reads the changed files like `refresh` and announces once — a few hundred `saveSong`s would be as many re-sorts.

## Others

- `SongContentRepositoryImpl`: in-memory LRU of song texts (32 entries, at most a million characters; a larger text is
  not kept). Exports and the import's duplicate check pass `useCache = false` (see disk now, don't fill memory). The
  lock guards only the map; a generation counter stops a read begun before an invalidation from caching its text.
  `invalidations` is a `StateFlow`, not events, so a busy collector cannot lose one; the ViewModel re-reads all open
  texts on each value.
- `EditorDraftRepositoryImpl`: no cache; writes serialized so a pause's write and the post-save deletion keep order.
- `DocumentRepositoryImpl`, `ArchiveRepositoryImpl`, `ImportDownloadRepositoryImpl`, `SongDetailsRepositoryImpl`: keep
  nothing. `loadSongCredits` turns a failure into `SongCreditsState.Failed` (logged by class name only, like every
  catalogue failure) and null into `Unavailable`.
- Sync (`:data:sync:implementation`) reads the library through this module's API and shares `LibraryFileLock` and
  `LibraryChanges` from `:data:source:local:api`.

## The cover copies

- `covers/<sha256 of the address>` is the offline cache on all platforms, outside `library/`, out of device backups.
  `CoverArtRepositoryImpl` watches `SongRepository.songs` and, after each finished read (`Idle` only, never scan
  batches, which would prune covers of songs not read yet) with a changed address set, deletes every copy no song
  names (search thumbnails included).
- Downloads run in the repository's own scope (a row scrolled away must not cancel another row's wait); one download
  per address; at most `MAX_CONCURRENT_DOWNLOADS` (4) at once, and one with no callers left by its turn is skipped (a
  fling would otherwise queue a request per row, the HTTP timeout counting the queue). A non-cover answer is not asked
  again this session; an unreachable address not for a minute or until the next cover search starts.
- `coverArtCacheSize` re-lists after writes and prunes off a conflated `StateFlow` counter.
- Searches run every catalogue at once via `searchCatalogues` (`CatalogueSearch.kt`, a `channelFlow`), appending each
  one's results as it answers so the grid only grows at its end.
- Settings → Library deletes the copies after a confirmation (`clearCoverArtCache`).
