<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# data/sync/scheduling

- **A run belongs to the app**: `SyncRunScheduler` owns an application-lifetime scope; the phones keep the process alive
  through the shell-provided `SyncNotifier`.
- Every app change to a library file (`LibraryChanges`) and to a synced preference schedules a run
  `AUTOMATIC_RUN_DELAY` after the latest request; a request during a run is carried out after it. A run's own writes go
  around the repositories, so a run never schedules the next one.
- `startScheduledSynchronization` (the app leaving the front) starts the run on the caller's thread and puts
  `SyncProgress()` in the state before returning, so the shell can hand it to the platform keep-alive in the same
  callback.
- `SyncLibraryRefresher` re-reads only the files a run changed, never a rescan, after any ending.
- Tests advance `SyncEnvironment`'s scope with `runCurrent` / `advanceTimeBy`, never `advanceUntilIdle`.
