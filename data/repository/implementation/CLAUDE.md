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
`:data:source:remote:api`. Koin wiring in `Module.kt` (`dataRepositoryModule`), all repositories as `single`.

`base/BaseLocalDataRepository<T>` holds all the caching logic — new repositories should extend it rather than
reimplement state handling. It owns a `MutableStateFlow<DataState<T>>` that starts as `Loading(null)` (nothing has been
read yet is not an error), reads the local source on the first `loadDataIfNeeded()`, and guards that read with a
`Mutex` so that the parallel loads in `LoadScreenDataUseCase` wait for each other instead of racing. A re-read keeps the
data already on screen in the `Loading` state, so a refresh never blanks the list, and a failed read becomes
`Failure(previous data)` rather than an empty list.

A load that arrives in pieces can publish them with `publishPartialData`, which is what the song scan does with each
batch of files it has parsed — a library of thousands then fills the list as it is read instead of showing nothing
until the last file. Partial data is only ever published while there is nothing on screen: during a re-read the
previous library is up, and replacing it with a partial one would make the list shrink and fill again under the user.
A read that fails or is cancelled falls back on the data from *before* it started rather than on whatever it had
published, or half a library would sit there looking like the whole of it and nothing would ever read the rest.
`commonTest` covers those rules, since none of them can be seen once a load has finished.

Writing is deliberately **not** part of that shape. Songs and setlists are one file each, so a change writes that file
and updates the one cached entry (`updateData`, which takes a transform of the current list and applies it atomically,
so a save landing during a rescan cannot overwrite what the rescan found); only the preferences are persisted as a
whole (`writeData`). What `writeData` publishes is `Idle` from the first moment, never a `Loading` the write then
resolves: the data being written is already what every reader should show, and it stays that way even when the write
fails, while a `Loading` would tell whoever reads this state for "nothing has been read yet" exactly that — for as
long as the storage takes to answer. A cancelled read is not a failed one: it is rethrown and leaves the cached data as it was. A
`rescan()` — the refresh action, and the last step of every import — is the only thing that walks the directory again.

- `SongContentRepositoryImpl` is not a `BaseLocalDataRepository`: it is a keyed in-memory cache of song *texts*, so
  paging through a setlist re-reads nothing. Bulk readers (the library export) pass `shouldCache = false` so that
  walking the whole library does not leave all of it in memory. The editor invalidates one entry after a save. The
  lock only guards the map, never a read: a read that started before an invalidation is told so by a generation
  counter and does not put the text it read back into the cache.
- `ArchiveRepositoryImpl` is a pass-through to the zip code in the local source implementation; it exists so the domain
  layer can reach it without depending on a local source.
- `sync/` is where local and remote meet, which is why it is in a repository rather than in either source.
  `SyncPlanner` is a **pure function** of (local hashes, remote listing, the index of what the last run saw) and is
  the one part of sync worth testing — `commonTest` covers every way a file can differ between two devices,
  including the ones that would otherwise only show up as a song someone lost. `SyncEngine` carries the plan out and
  is written so that an interrupted run leaves the library usable: the index (`SyncIndexDocument`, the on-disk shape
  of `sync-index.json`) is only told about a file once that file has actually moved, so anything half done simply
  looks unsynced next time. A failure on one file does not end
  a run; only the two failures that make every further call pointless (the credentials refused, the service
  unreachable) do — and a `CancellationException` is caught *first* and rethrown, since a stopped run is not a few
  hundred files that failed. Operations run `CONCURRENT_TRANSFERS` at a time within each ordering group rather than
  one after another: every one of them is a request, and serialising them made a first sync as slow as the round
  trip times added up. `SyncRepositoryImpl` owns an application-lifetime scope, so a run outlives the screen and
  (on Android) the activity that started it, and it is what tells the song and setlist repositories to rescan
  afterwards — the use case cannot, now that it returns before the run does. Disconnecting cancels a run that is
  still going and waits for it, and a run only ever writes its outcome into a state that is still `Connected`: a
  run that outlived the account it ran against must not bring that account back on screen. `restore` never throws
  for a service that refuses the stored credentials — the app starts disconnected and says so.
