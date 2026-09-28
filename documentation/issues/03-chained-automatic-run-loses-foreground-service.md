# Keep the Android sync service (and the iOS background task) across a run that is chained right after another

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** android, ios
**Files:** app/android/src/main/java/com/pandulapeter/campfire/sync/CampfireSyncService.kt,
app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosSyncNotifier.kt, app/android/CLAUDE.md, app/ios/CLAUDE.md

## Problem
Since cc4d6f55 "a request made during a run is carried out after it" (root CLAUDE.md, Sync): the scheduler in
`SyncRepositoryImpl.init` (`SyncRepositoryImpl.kt:161-168`) waits with `syncJob.load()?.join()` and starts the next run
the moment the current one ends. With the app in the background that next run starts with no keep-alive:

- Android: `CampfireSyncService` watches the state and stops itself the moment it sees no progress —
  `stopIfNothingIsRunning()` (`CampfireSyncService.kt:139-145`) → `stop()` → `stopForeground` + `stopSelf`. The run
  that follows a few milliseconds later sets `progress = SyncProgress()` (`SyncRepositoryImpl.kt:466`), but the service
  is already out of the foreground (`isInForeground = false`, so its collector does nothing) and the only other starter
  is the activity's composition, which is stopped. Android 12+ would refuse a `startForegroundService` from the
  background anyway. The second run runs in a cached process and is frozen or killed.
- iOS: `IosSyncNotifier.onProgressChanged(null)` (`IosSyncNotifier.kt:130-137`) calls `endBackgroundTask()` at once;
  whether the next run's non-null progress reaches the main-queue collector before iOS suspends the app (or whether the
  two emissions are conflated so the null is never seen) is a race.

Concrete scenario: open the app (the launch run starts; on a few hundred songs it takes several seconds), toggle a tag
while it is going (the automatic run is queued behind it), go home. The launch run finishes under the service; the
queued run — the one carrying the user's edit — starts unprotected, is frozen mid request, and the next launch
reports "interrupted" and, per `SyncUseCaseImpls.kt:97`, does not start a run, so the edit stays local until the user
presses Sync now.

## Fix
Give both shells a short grace before letting go when a run ends, and cancel it when a run starts again:

1. `CampfireSyncService`: in the state collector, when progress is null and the service is in the foreground, do not
   `stop()` at once; launch (in `scope`) a `stopJob` that `delay`s `RUN_HANDOVER_GRACE` (e.g. 2 s) and then calls
   `stopIfNothingIsRunning()`. When progress is non-null again, cancel `stopJob`. Keep the immediate check in
   `onStartCommand` (line 105) as a delayed one too, since it has the same purpose. `ACTION_DISMISS` and `ACTION_STOP`
   stay immediate (the first comes from the composition in front, which restarts the service itself if a run follows;
   the second is the user). Cancel `stopJob` in `stop()`.
2. `IosSyncNotifier.onProgressChanged(null)`: remove the notification at once as today, but end the background task
   from a delayed job (`scope.launch { delay(RUN_HANDOVER_GRACE); if (progress == null) endBackgroundTask() }`),
   cancelled when progress becomes non-null again. The expiration handler still ends it immediately.
3. While the Android notification lingers through the grace it still shows the previous run's counts; reset
   `completed`/`total` to 0 when progress goes from null to non-null, so the chained run's notification starts at
   "preparing" rather than at the last run's numbers. (Merged from a duplicate finding of the sync reviewer.)
4. Document the grace in `app/android/CLAUDE.md` (service paragraph) and `app/ios/CLAUDE.md` (IosSyncNotifier
   paragraph): a run queued behind another starts within milliseconds of it, so a couple of seconds covers it.

## Verification
No unit test harness for the shells. Manual, Android 14+ device with a Dropbox folder of 200+ songs:
1. Launch; while the launch run is in progress, toggle a tag on a song; press Home.
2. Watch the notification: it should stay up through the end of the first run into the second (count restarting
   from "preparing"), and disappear only after the second one ends. `adb shell dumpsys activity services
   com.pandulapeter.campfire` shows the service in the foreground throughout.
3. Relaunch: Settings shows a successful last sync, not "interrupted"; the tag is in the Dropbox file.
iOS: same steps; the second run completes (Settings shows success after returning).

## Conflicts
plan 02 touches the path that starts the service; the sync lane may change `SyncRepositoryImpl`'s scheduler. If the
sync lane instead makes the repository keep `progress` non-null across the hand-off, this plan becomes unnecessary —
coordinate.

## Open decision
- A: grace in the two shells (this plan) — small, local, platform code only.
- B: the repository keeps the state "running" across a queued hand-off (no `progress = null` between the two runs when
  the next one is already due). Deterministic, but every early exit of the next run must then clear it, and a lost
  hand-off would leave a stuck progress on screen.
Recommended: A.
