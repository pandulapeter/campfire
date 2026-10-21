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

Implements `SyncRepository`, `SharedLinksRepository` (`shared/`, its own `CLAUDE.md`), and `DemoLibraryRepository`.
Everything is `internal`. `SyncEngine` is built by a `DataSyncModule` function, since its demo lookup is a lambda over
`UserPreferencesRepository` that Koin cannot inject.
`DemoLibraryRepositoryImpl` lives here because it records demo files under `SyncKey.path`, the key the engine looks
them up by.

## Locks

- `SyncConnectionManager`'s restore lock: never held across a run; forgetting the stored connection runs under it.
- `SyncRunner`'s run lock: held by a run, and by `disconnect` around deleting the index (and the shared links' mirror and
  journal) only. Sharing actions never take it.
- `LibraryFileLock`: held by the engine around local file calls only, never a request. Not reentrant: the repositories
  take their own locks first and this one inside; nothing holding it calls anything that takes it. A local write or
  deletion and its `onLocalFileChanged` finish together once started (`NonCancellable`), as the repositories' writes do.
- `SyncLibraryRefresher.changedFilesMutex`: innermost.

## Invariants

- **Content decides what changed, never a clock**: the platforms disagree about modification times and the web has
  none. **An edit always beats a deletion.** A file changed on both sides is never merged. `SyncPlanner` stays a pure,
  tested function of (local hashes, remote listing, index).

## Renames

A rename reaches sync as a deletion plus a new file (`SyncPlanner` knows no moves). By "an edit beats a deletion", a
device that edited the old name since the last run puts it back, leaving both.

A behaviour that spans devices (renames, conflicts, deletions, synced preferences) gets a case in `TwoDeviceSyncTest`,
which runs two `Device`s against one fake cloud folder through the real engine.
