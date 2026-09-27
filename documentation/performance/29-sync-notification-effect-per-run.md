<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 29 — Hand the shells the sync notification per run rather than per file

| | |
|---|---|
| Lane | D |
| Impact | low |
| Confidence | high |
| Platforms | all for the composition work; Android for the shell part |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/BackgroundSync.kt, presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireAndroidApp.kt |
| Depends on / conflicts with | — (touches the end of `CampfireApp.kt` only) |
| Commit message | `Tell the platform about a sync run's notification once per run rather than once per file.` |

## Problem
My review first suggested that this floods Android's notification rate limit. **That is not the case, and this plan is narrower because of it:**
- `CampfireSyncService.scheduleNotificationUpdate` already posts at most once per `NOTIFICATION_UPDATE_INTERVAL_MILLIS`, from its own observation of the sync state.
- `CampfireMainActivity.onSyncNotificationChanged` (`:141-170`) returns early while the service runs with the same words.
- `IosSyncNotifier.onSyncNotificationChanged` only keeps the words.

What is left is avoidable work on the main thread for every file a run moves (several per second):
- `SyncNotificationEffect` (`CampfireApp.kt:1253-1290`) reads the whole `syncState`. For every progress change it:
  - formats a `body` with `withSyncCounts`;
  - builds a new `SyncNotification(…, progress = it)`;
  - restarts `LaunchedEffect(notification)`, which calls the shell.

  **No shell reads `body`** (grep over `app/android` and `app/ios`: only `channelName`, `title`, `preparingBody`, `progressBodyFormat`, `stopLabel` and, for the first `startForegroundService`, `progress`).
- `CampfireAndroidApp.kt:67-68` reads the whole `syncState` at the shell's root only to compute `syncState is SyncState.Connected`, so the root recomposes for every progress change. The children skip, so the cost is small.

## Fix
1. **In `SyncNotificationEffect`, key the effect on what the shells act on:** the resolved words and whether a run is going. Pass the latest progress through `rememberUpdatedState`, so the first post still carries current counts:
   ```kotlin
   val isRunning by remember(viewModel) { viewModel.syncState.map { (it as? SyncState.Connected)?.progress != null } }.collectAsStateWithLifecycle(false)
   val latestProgress by rememberUpdatedState((syncState as? SyncState.Connected)?.progress)
   LaunchedEffect(isRunning, channelName, title, stopLabel, preparing, progressBodyFormat) {
       val progress = latestProgress
       if (isRunning && progress != null) { hasShownNotification = true; syncNotifier.onSyncNotificationChanged(SyncNotification(…, progress = progress)) }
       else if (hasShownNotification) { hasShownNotification = false; syncNotifier.onSyncNotificationChanged(null) }
   }
   ```
   Move the `syncState` read into that effect as well, or into a `snapshotFlow`, so the composable itself no longer recomposes per file.
2. **Remove `SyncNotification.body`** (`BackgroundSync.kt`) and its per-tick formatting, since nothing reads it. If something outside this repository's shells needs it, keep the field and compute it only inside the effect.
3. **In `CampfireAndroidApp`, collect only the boolean:**
   ```kotlin
   val isSyncConnected by remember(viewModel) { viewModel.syncState.map { it is SyncState.Connected }.distinctUntilChanged() }.collectAsStateWithLifecycle(false)
   ```
   Initialize it from `viewModel.syncState.value is SyncState.Connected`, not from `false`, so a composition that starts mid-run does not ask for the permission a frame late.

What must NOT change: "nothing to show" is still only reported after something was shown (the `hasShownNotification` rule and its KDoc), and the Android service still starts with the counts of the moment.

## Verification
- `./gradlew :presentation:desktopTest :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64`.
- On an Android device with a real Dropbox test account (see the sync testing notes), start a first sync of a large library, background the app, and check:
  - the notification appears with counts that keep moving (the service's own updates);
  - "Stop" works;
  - the notification is removed when the run ends.
- Open the app on a run already going: no stop.
- With a temporary counter, `SyncNotificationEffect`'s effect runs about twice per run instead of once per file.
