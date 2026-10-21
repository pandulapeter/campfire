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
- A run that ends in `Failure(NETWORK)` (only that reason) is tried again while the app is alive, after
  `NETWORK_RETRY_DELAYS` (capped, reset by any other ending and by a run somebody asks for); a change waiting already
  wins, Stop and Disconnect drop it, and leaving the front hands it to the keep-alive only once it is due.
- `startScheduledSynchronization` (the app leaving the front) starts the run on the caller's thread and puts
  `SyncProgress()` in the state before returning, so the shell can hand it to the platform keep-alive in the same
  callback.
- The app coming back to the front starts an automatic run (`synchronizeOnReturn`), except after `Interrupted`, which
  is reported rather than repeated, as at a launch, and while a deletion question is open, which waits for its answer
  (Sync now and a library change still ask again).
- `SyncRemoteWatcher` watches the provider's change feed while the app is in front, from `SyncRunner.watchPoint`: the
  cursor of a completed run's last listing with the index it left. A change whose revision the index holds, a deletion
  of a file it no longer holds (names matched ignoring case and Unicode form, as the run files them), or a
  `preferences.json` that decodes to the one the run left, is this device's own, and `shared-links.json` is no change
  at all (no run depends on it); anything else schedules a run and stops watching until that run hands over a new
  point. A run clears the point as it starts and only a completed one sets it, so a stopped run or an open deletion
  question is never restarted by the feed. A poll the service held for
  `HELD_POLL` and then lost is asked again at once; one that failed sooner is asked again after `NETWORK_RETRY_DELAYS`;
  any other failure schedules a run.
- Every network wait (the feed's, and a failed run's retry) ends early when `ConnectivityMonitor` reports the device
  back online (`awaitReconnection`), listened to only while such a wait is going.
- `SyncLibraryRefresher` re-reads only the files a run changed, never a rescan, after any ending.
- Tests advance `SyncEnvironment`'s scope with `runCurrent` / `advanceTimeBy`, never `advanceUntilIdle`.
