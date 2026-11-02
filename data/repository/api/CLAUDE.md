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

- **Only `rescan()` lists the library folder again.** `refresh(fileNames)` re-reads named files (what a sync run
  changed); `adoptImported` puts an import's writes in as one change; every other write updates its one cached entry. A
  setlist write caches the entry `saveSetlist` returns, with its file size (Settings sums the library size from it).
- **An existing setlist is changed through `updateSetlist`** (read latest, transform, write, serialized), never by
  saving a copy read earlier: two quick changes would undo each other. `renameSetlist` is the same for the header fields
  (may move the file). `saveSetlist` is for a setlist the caller owns whole (just created or copied).
- `SongContentRepository` is a recent-texts lookup, deliberately not a `DataState` flow.
- `UserPreferencesRepository.hasStoredUserPreferences` (a never-used installation, `IsFirstRunUseCase`) is deliberately
  uncached: the first save would make a cached `false` wrong.
- `CoverArtRepository.getCoverArt` answers bytes or nothing, never a failure. The catalogue searches emit progress as
  each catalogue answers, a failing one recorded in `failed`, not thrown; `loadSongCredits` never throws. Nothing from
  the details lookup or `ImportDownloadRepository` is kept.
- `SyncRepository` caches no list (a run feeds the song and setlist repositories through `refresh`) and is the only
  thing above the data layer that knows a service is involved. `forgetStoredConnection` is local-only, retried by
  `restore` until it works.
- `SharedLinksRepository` serves the shared links from this device's last read; only its actions ask the service, never
  under the run lock, and none throws but cancellation.
- `DemoLibraryRepository` is not part of `SyncRepository`: it runs on every planting, sync connected or not.
- `ArchiveRepository` and `DocumentRepository` are stateless boundaries; `DocumentRepository` never retains bytes or
  swallows cancellation.
