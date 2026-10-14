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

Repository interfaces only; consumed by `:domain:implementation`, implemented by `:data:repository:implementation`
(and `SyncRepository` / `DemoLibraryRepository` by `:data:sync:implementation`).

- **Only `rescan()` lists the library folder again.** `refresh(fileNames)` re-reads named files (what a sync run
  changed); `adoptImported` puts an import's writes in as one change; every other write updates its one cached entry.
  A setlist write caches the entry `saveSetlist` returns, carrying the file size (Settings sums the library size from
  the models).
- **An existing setlist is changed through `updateSetlist`** (read latest, transform, write, serialized), never by
  saving a copy read earlier: that copy lags, and two quick changes would undo each other. `renameSetlist` is the same
  for title and description (may move the file) and takes the setlist's name. `saveSetlist` is for a setlist the caller
  owns whole (just created or copied).
- `SongContentRepository` is a recent-texts lookup, deliberately not a `DataState` flow.
- `UserPreferencesRepository.hasStoredUserPreferences` asks whether the preferences document exists, whose absence is
  what a never-used installation looks like (`IsFirstRunUseCase`). It is deliberately uncached: the first save would make
  a cached `false` wrong.
- `CoverArtRepository.getCoverArt` answers bytes or nothing, never a failure; searches (`searchCoverArt`,
  `searchSongDetails`) emit progress as each catalogue answers, a failing one recorded in `failed`, not thrown.
  `loadSongCredits` never throws. Nothing from the details lookup or `ImportDownloadRepository` is kept.
- `SyncRepository` caches no list (the library stays in the song and setlist repositories, which a run feeds through
  `refresh`) and is the only thing above the data layer that knows a service is involved. `forgetStoredConnection` is
  local-only, retried by `restore` until it works.
- `DemoLibraryRepository` is not part of `SyncRepository`: it runs on every planting, sync connected or not.
- `ArchiveRepository` and `DocumentRepository` are stateless boundaries so use cases reach the zip and extraction code
  without seeing local sources; `DocumentRepository` never retains bytes or swallows cancellation.
