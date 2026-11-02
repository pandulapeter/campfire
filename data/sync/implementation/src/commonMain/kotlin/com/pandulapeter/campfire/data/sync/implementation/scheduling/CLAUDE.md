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
- Every app change to a library file (`LibraryChanges`) or a synced preference schedules a run `AUTOMATIC_RUN_DELAY`
  after the latest request; a request during a run is carried out after it. A run's own writes go around the
  repositories, so a run never schedules the next one.
- A run ending in `Failure(NETWORK)` (only that reason) is retried while the app is alive after `NETWORK_RETRY_DELAYS`
  (capped, reset by any other ending and by a requested run); a waiting change wins; Stop, Disconnect, and a reconnect
  drop it (`stopForConnectionChange`); leaving the front hands it to the keep-alive only once it is due.
- `startScheduledSynchronization` (leaving the front) starts the run on the caller's thread and puts `SyncProgress()` in
  the state before returning, so the shell can hand it to the platform keep-alive in the same callback.
- Returning to the front starts an automatic run (`synchronizeOnReturn`), except after `Interrupted`, which is reported
  rather than repeated, as at a launch, and while a deletion question is open (Sync now and a library change still ask
  again).
- `SyncRemoteWatcher` watches the change feed while in front, from `SyncRunner.watchPoint`: the cursor of a completed
  run's last listing with the index it left. These are this device's own and start nothing: a change whose revision the
  index holds, a deletion of a file it no longer holds (names folded as the run files them), a `preferences.json`
  decoding to the one the run left. `shared-links.json` starts no run but is read into the mirror, so screens follow a
  share made or stopped elsewhere. Anything else starts a run at once (no `AUTOMATIC_RUN_DELAY`, which is for this
  device's edits) and stops watching until that run hands over a new point. A run clears the point as it starts and only
  a completed one sets it, so a stopped run or an open deletion question is never restarted by the feed.
- A poll held for `HELD_POLL` and then lost is asked again at once; one failing sooner waits `NETWORK_RETRY_DELAYS`, the
  first a couple of seconds, since one failure on a working link reports no reconnection to end the wait; any other
  failure schedules a run.
- Every network wait (the feed's, a failed run's retry) ends early when `ConnectivityMonitor` reports the device back
  online (`awaitReconnection`), listened to only during such a wait.
- `SyncLibraryRefresher` re-reads only the files a run changed, never a rescan, after any ending and while it goes: a
  live refresh asked for inside its pause waits for the pause rather than being dropped, so another device's change to
  the song on screen shows once downloaded.
- Tests advance `SyncEnvironment`'s scope with `runCurrent` / `advanceTimeBy`, never `advanceUntilIdle`.
