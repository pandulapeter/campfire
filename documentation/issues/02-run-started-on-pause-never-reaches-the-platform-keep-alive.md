# Hand the platform the automatic run that leaving the app starts, synchronously, while the app is still in front

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** android, ios
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt,
data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt,
data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SyncRepository.kt (KDoc only),
presentation/CLAUDE.md, data/repository/implementation/CLAUDE.md

## Problem
cc4d6f55 made leaving the app start the waiting automatic run at once, on the stated grounds that "a phone only keeps
alive a run it was told about while the app was still in front" (root CLAUDE.md, Sync;
`CampfireViewModel.onAppPaused` KDoc, `CampfireViewModel.kt:1568-1573`). But nothing makes the telling happen while the
app is still in front — the start is asynchronous and the telling goes through a frame of the composition:

1. `CampfireApp.kt:228` — `LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onAppPaused() }` →
   `startScheduledSynchronization()` →
   `SyncRepositoryImpl.startScheduledSynchronization()` (`SyncRepositoryImpl.kt:417-419`) only moves
   `scheduledRunDueAt` to now.
2. The collector in `init` (`SyncRepositoryImpl.kt:161-168`, on `Dispatchers.Default`) wakes up, `delay`s, `join`s,
   calls `startRun`, which `launch`es `runSynchronization`; that takes the mutex, asks `providers.firstOrNull {
   it.isConnected() }` and only then sets `progress = SyncProgress()` (`SyncRepositoryImpl.kt:466`).
3. `SyncNotificationEffect` (`CampfireApp.kt:1303-1341`) sees it through `collectAsStateWithLifecycle` and calls
   `syncNotifier.onSyncNotificationChanged(...)` from a `LaunchedEffect` — i.e. after a hop back to the main thread
   and a recomposition frame.

On **Android** the notifier is the only thing that starts `CampfireSyncService`
(`CampfireMainActivity.kt:136-172`). Between ON_PAUSE and ON_STOP there is often no frame at all: pressing the power
button pauses and stops the activity back to back and the display stops sending vsync, and at ON_STOP
`collectAsStateWithLifecycle` stops collecting and the window recomposer pauses. The effect then never runs while the
app is in front (and from the background Android 12+ would refuse `startForegroundService` anyway). The run carries on
in a process that has no foreground service, is demoted to cached, and is frozen (Android 14's cached-app freezer)
or killed within seconds — mid request. Scenario: edit a tag / save a song, lock the phone within 10 s. Result: the
run hangs or is killed; at the next launch it is reported as interrupted, and because
`SynchronizeLibraryUseCaseImpl` (`SyncUseCaseImpls.kt:97`) skips the launch run when `wasInterrupted`, the change is
not uploaded until the user presses Sync now or edits again.

On **iOS** the background task itself is taken from the state (`IosSyncNotifier.onProgressChanged`), so the run is
kept alive, but the words for the notification (`IosSyncNotifier.words`) and the notification permission request
only ever come from the composition (`IosSyncNotifier.kt:120-128`). In a process whose composition never showed a run
(for example a launch whose previous run was interrupted, so no launch run started), an automatic run started on
leaving shows no notification at all.

## Fix
Make the two steps synchronous in the ON_PAUSE callback itself, which runs on the main thread while the activity /
scene is still in front:

1. `SyncRepositoryImpl.startScheduledSynchronization()`: when a run is waiting (`scheduledRunDueAt.value != null`) and
   no run is going (`syncJob.load()?.isActive != true`), set `scheduledRunDueAt.value = null`, call
   `startRun(SyncDeletionPolicy.ASK)` directly, and set the state's progress to `SyncProgress()` **before returning**
   (e.g. `updateConnected { it.copy(progress = SyncProgress(), lastOutcome = null) }` right after a successful
   `startRun`). `runSynchronization` already sets the same value again at line 466 and clears it on every way out
   (`finally` at 572-576); check its two early returns before the `try` (credentials unreadable, no provider
   connected, lines 450-464) also leave no progress behind — the first already sets `progress = null`, the second
   replaces the state with `ConnectionFailed`, and the `as? Connected ?: return@withLock` one only returns when there
   is no `Connected` state (and so no progress) at all. When a
   run *is* going, leave the collector to chain it as today (see plan 03 for keeping that one alive).
   Update the KDoc of `SyncRepository.startScheduledSynchronization` to say the state shows the run as started by the
   time it returns.
2. In `CampfireApp.kt`, replace the bare ON_PAUSE effect with one that, right after `viewModel.onAppPaused()`, reads
   `viewModel.syncState.value` and, when it has progress, calls `syncNotifier.onSyncNotificationChanged(...)` with the
   same `SyncNotification` `SyncNotificationEffect` builds. Hoist the words (the five `stringResource`s at
   `CampfireApp.kt:1313-1317`) so both places use one `remember`ed builder, and set `hasShownNotification`-equivalent
   state so the later "nothing to show" is still sent when the run ends in front. The Android activity already
   ignores a repeat with the same words while the service is running (`CampfireMainActivity.kt:152`).
3. No test harness covers the composition; add a `SyncRepositoryImplTest` case: with a scheduled run waiting and none
   going, `startScheduledSynchronization()` leaves `syncState` with non-null progress synchronously, and nothing is
   started when nothing was waiting.
4. Update `presentation/CLAUDE.md` (where `SyncNotifier` / `onAppPaused` are described) and
   `data/repository/implementation/CLAUDE.md` (the automatic run paragraph).

## Verification
- `./gradlew :data:repository:implementation:desktopTest`
- Android (API 34+ emulator/device, sync connected): open a song, toggle a tag, press the power button within 10 s.
  Expected: the sync notification appears (visible on the lock screen), the run finishes; next launch does not say
  "interrupted". Before the fix: no notification, and the run is frozen/killed.
- iOS: force-quit, launch with no run going, toggle a tag, go home within 10 s: a background task holds the run and,
  once in the background, the notification is posted.

## Conflicts
`SyncRepositoryImpl.kt` is the sync lane's file; `CampfireApp.kt` is shared by every presentation lane. plan 03
touches the same notifier / service path.
