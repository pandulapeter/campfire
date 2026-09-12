<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :data:repository:api

Repository interfaces only. Consumed by `:domain:implementation`; implemented by `:data:repository:implementation`.

- `SongRepository` — an observable `songs: Flow<DataState<List<Song>>>` plus `loadSongsIfNeeded()`, `rescan()` (re-read
  the folder, which is what a refresh and the end of an import do), `saveSong`, `createSong`, `importFileName`, `importSong`, `deleteSong`.
- `SetlistRepository` — the same shape over `*.setlist.json`, plus `parseSetlist` / `loadSetlistDocument` for the export
  and import paths.
- `SongContentRepository` — the *text* of the songs that have been opened, cached in memory so that paging through a
  setlist does not re-read the same files. Deliberately not a `DataState` flow: it is a lookup, not a screen's state.
- `UserPreferencesRepository` — one document, read once and written whole. `hasStoredUserPreferences` is the one
  thing here that is not about what is in it: the demo library asks it to tell a fresh installation from a device
  Campfire has been used on, and it is deliberately uncached, since the very first save makes it false.
- `SyncRepository` — the state machine around sync: `syncState: Flow<SyncState>`, the providers the build has, and
  `restore` / `connect` / `disconnect` / `synchronize` / `cancelSynchronization`. Unlike the others it caches no list — the library keeps
  living in `SongRepository` and `SetlistRepository`, which is why a run that changed files has to be followed by a
  `rescan()`, done by `SynchronizeLibraryUseCase`. It is also the only thing above the data layer that knows a
  service is involved: the screens see a `SyncState` and never learn which provider produced it.
- `ArchiveRepository` — zip pack/unpack. It has no state to cache and exists only so that the use cases can reach the
  zip code without the domain layer having to see the local sources.

A `rescan()` is the only thing that re-reads the library folder. Everything else keeps the cached list in step by
updating the one entry it changed, so writing a song does not cost a directory scan.
