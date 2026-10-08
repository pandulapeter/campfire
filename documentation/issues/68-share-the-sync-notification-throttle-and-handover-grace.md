# Share the sync notification's update throttle and run-handover grace between Android and iOS as one tested commonMain class

**Kind:** testability  ·  **Severity:** medium  ·  **Effort:** M  ·  **Risk:** medium  ·  **Platforms:** Android, iOS
**Files:** new `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SyncNotificationScheduler.kt`
(next to `BackgroundSync.kt`, which holds `SyncNotifier`); new
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SyncNotificationSchedulerTest.kt`;
`app/android/src/main/java/com/pandulapeter/campfire/sync/CampfireSyncService.kt` (may have moved to `src/main/kotlin`);
`app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosSyncNotifier.kt`; `app/android/CLAUDE.md`; `app/ios/CLAUDE.md`;
`presentation/CLAUDE.md` (or `ui/platform/CLAUDE.md` after plan 60), the `BackgroundSync.kt` bullet
**Depends on:** none

## Problem

Two shells implement the same two timing mechanisms by hand, with only the interval differing, and neither can be
tested (one is an Android `Service`, the other a Kotlin/Native class against UIKit):

`CampfireSyncService`:
```kotlin
    private fun scheduleStop() {
        if (!isInForeground || stopJob?.isActive == true) return
        stopJob = scope.launch {
            delay(RUN_HANDOVER_GRACE_MILLIS)
            if (latestSyncState.progress == null) {
                stopJob = null
                stop()
            }
        }
    }

    private fun scheduleNotificationUpdate(isCountingStarted: Boolean) {
        if (isCountingStarted) {
            notificationUpdateJob?.cancel()
            notificationUpdateJob = null
            updateNotification()
        } else if (notificationUpdateJob?.isActive != true) {
            notificationUpdateJob = scope.launch {
                delay(NOTIFICATION_UPDATE_INTERVAL_MILLIS)
                updateNotification()
            }
        }
    }
    …
        private const val NOTIFICATION_UPDATE_INTERVAL_MILLIS = 500L
        private const val RUN_HANDOVER_GRACE_MILLIS = 2_000L
```

`IosSyncNotifier`:
```kotlin
    private fun scheduleBackgroundTaskEnd() {
        if (backgroundTask == UIBackgroundTaskInvalid || backgroundTaskEndJob?.isActive == true) return
        backgroundTaskEndJob = scope.launch {
            delay(RUN_HANDOVER_GRACE)
            if (progress == null) endBackgroundTask()
        }
    }

    private fun scheduleNotificationUpdate(isCountingStarted: Boolean) {   // identical body to Android's
    …
        val NOTIFICATION_UPDATE_INTERVAL = 1.seconds
        val RUN_HANDOVER_GRACE = 2.seconds
```

Both cancel the grace job when a run (re)starts (`stopJob?.cancel()` / `backgroundTaskEndJob?.cancel()`), both cancel
the pending update when the notification is taken down. The behaviour these encode — "a burst of progress posts at most
once per interval, from one delayed job that reads the latest counts when it fires", "the step from preparing to
counting posts at once", "a run chained within the grace keeps the service / background task" — was the subject of
several past fixes (the chained-run and late-intent cases in the service's comments) and has no test.

The two shells' *decisions* differ on purpose and must stay where they are: Android's `isChainedRun` (resets counts,
posts at once when a run starts behind another while the service is in the foreground) versus iOS'
`wasPreparing && !newProgress.isPreparing`; Android only stops after `startForeground` (`isInForeground`), iOS only
ends a task it began (`backgroundTask != UIBackgroundTaskInvalid`).

## Fix

1. Add `SyncNotificationScheduler` to `ui/platform` in `:presentation` `commonMain` (JVM-free; `kotlin.time.Duration`,
   `kotlinx.coroutines`):
   ```kotlin
   class SyncNotificationScheduler(
       private val scope: CoroutineScope,
       private val updateInterval: Duration,
       private val handoverGrace: Duration = 2.seconds,
       private val postUpdate: () -> Unit,
       private val release: () -> Unit,
       private val isRunGoing: () -> Boolean,
   ) {
       fun requestUpdate(immediately: Boolean)   // the current scheduleNotificationUpdate body
       fun cancelUpdate()
       fun scheduleRelease()                     // starts the grace unless one is pending; at its end calls release() if !isRunGoing()
       fun cancelRelease()
   }
   ```
   Move the KDoc that explains *why* (Android's five-updates-a-second shedding, the chained run starting within
   milliseconds, Android 12's background start refusal, iOS suspending before progress arrives) onto these functions,
   keeping each platform-specific reason as a sentence naming its platform. Public (the shells live in other modules),
   no Koin involvement — each shell constructs its own.
2. Android: `CampfireSyncService` creates `SyncNotificationScheduler(scope, 500.milliseconds, 2.seconds,
   postUpdate = ::updateNotification, release = ::stop, isRunGoing = { latestSyncState.progress != null })`;
   `scheduleStop()` becomes `if (isInForeground) scheduler.scheduleRelease()`, `stopJob?.cancel()` becomes
   `scheduler.cancelRelease()`, `scheduleNotificationUpdate(x)` becomes `scheduler.requestUpdate(immediately = x)`,
   `stop()` calls `cancelRelease()` + `cancelUpdate()`. Keep `isChainedRun`, the count reset and the
   `isInForeground` guards in the service. Note `stop()` currently clears `stopJob = null` before calling `stop()`
   from inside the job — make `release` safe to call while the scheduler's own job is running (the job clears its
   reference before invoking `release`).
3. iOS: `IosSyncNotifier` creates it with `1.seconds` / `2.seconds`, `postUpdate = ::updateNotification`,
   `release = ::endBackgroundTask`, `isRunGoing = { progress != null }`; `scheduleBackgroundTaskEnd()` becomes
   `if (backgroundTask != UIBackgroundTaskInvalid) scheduler.scheduleRelease()`; `cancelNotificationUpdate()` →
   `scheduler.cancelUpdate()`. Keep `wasPreparing` and the foreground/background observers.
4. Docs: `app/android/CLAUDE.md` ("rendered from the sync state at most twice a second … `RUN_HANDOVER_GRACE_MILLIS`")
   and `app/ios/CLAUDE.md` name the scheduler; the `BackgroundSync.kt` bullet in `:presentation`'s doc gains one line.

Step 1 (class + tests) lands alone; steps 2 and 3 are one commit each.

## Tests

`SyncNotificationSchedulerTest` (commonTest, `kotlinx-coroutines-test` is already a `commonTest` dependency of
`:presentation`), using `runTest` and the test scheduler's virtual time, with counters for `postUpdate`/`release`:
- ten `requestUpdate(false)` within one interval → exactly one post, at `updateInterval`, not before;
- a further request after that post → a second post one interval later;
- `requestUpdate(true)` while one is pending → posts at once and cancels the pending one (total one post at t=0, none
  at t=interval);
- `cancelUpdate()` before the interval → no post;
- `scheduleRelease()` with `isRunGoing = false` → `release` once at exactly `handoverGrace`;
- `scheduleRelease()` twice → still one release;
- `scheduleRelease()`, then `cancelRelease()` within the grace → no release;
- `scheduleRelease()` with a run starting before the grace ends (`isRunGoing` flips true) → no release;
- `release` that calls `cancelRelease()` re-entrantly does not throw.
Run with `./gradlew :presentation:desktopTest`.

## Manual check

Android (emulator, Dropbox connected, a library of 100+ songs so a run lasts): start Sync now, press Home — the
notification appears and counts up smoothly; edit a song, leave the app within 10 s so a scheduled run and a chained
run follow each other — the notification restarts from "preparing" and does not disappear between the runs; after the
last run it goes away about two seconds later. iOS (device or simulator): same sequence; the notification counts while
backgrounded and is removed on return; a chained run keeps going in the background.
