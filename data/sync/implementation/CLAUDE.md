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

Implements `SyncRepository` and `DemoLibraryRepository`. Everything is `internal`. `SyncEngine`'s demo lookup must stay
a `DataSyncModule` function, since the Koin plugin would hand an annotated constructor its `{ null }` default.
`DemoLibraryRepositoryImpl` lives here because it records demo files under `SyncKey.path`, the key the engine looks
them up by.

The module-wide rules are here; each package (`run`, `scheduling`, `connection`, `index`, `preferences`) has its own
`CLAUDE.md` for the rest.

## Locks

- `SyncConnectionManager`'s restore lock: never held across a run; forgetting the stored connection runs under it.
- `SyncRunner`'s run lock: held by a run, and by `disconnect` around the index deletion only.
- `LibraryFileLock`: held by the engine around local file calls only, never a request. Not reentrant: the repositories
  take their own locks first and this one inside; nothing holding it calls anything that takes it.
- `SyncLibraryRefresher.changedFilesMutex`: innermost.

## Invariants

- **Content decides what changed, never a clock**: the platforms disagree about modification times and the web has
  none. **An edit always beats a deletion.** A file changed on both sides is never merged. `SyncPlanner` stays a pure,
  tested function of (local hashes, remote listing, index).

## Renames

A rename reaches sync as a deletion plus a new file (`SyncPlanner` knows no moves). By "an edit beats a deletion", a
device that edited the old name since the last run puts it back, leaving both.

Tests: `./gradlew :data:sync:implementation:desktopTest`. `TwoDeviceSyncTest` runs two `Device`s in turn against one
shared fake cloud folder, through the real engine and preferences merge.
