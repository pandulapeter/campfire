# Start the Android sync service only once the app is leaving, not for every run in the foreground

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** android
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt, app/android/src/main/java/com/pandulapeter/campfire/CampfireMainActivity.kt, app/android/CLAUDE.md, presentation/CLAUDE.md

## Problem
`SyncNotificationEffect` (`CampfireApp.kt` ~1304-1340) hands a notification to the shell the moment any run starts,
and on Android `CampfireMainActivity.onSyncNotificationChanged` (`CampfireMainActivity.kt:141-175`) answers it with
`startForegroundService`, whose notification carries a **Stop** action. Android 12+ shows a foreground-service
notification immediately (no 10-second deferral) when it has action buttons, and earlier versions always did.

That was acceptable when runs happened at launch and on Sync now. Since cc4d6f55 a run starts ten seconds after every
change — a tag, a transposition step in a setlist, a reorder, a cover — so while the user is working in the app, a
"Syncing" icon appears in the status bar and an ongoing notification with a Stop button appears in the shade after
nearly every edit, for a run the user did not ask for and that finishes in a second or two. Offline, the same happens
for runs that fail each time.

The service is only needed to keep the process once the user has left; while the activity is in front the process
is foreground anyway.

## Fix
1. In `SyncNotificationEffect`, gate the hand-over on the lifecycle: read `LocalLifecycleOwner.current.lifecycle`'s
   `currentStateAsState()` and treat "show" as `isRunning && lifecycleState < Lifecycle.State.RESUMED` (i.e. the app is
   paused/leaving). Add the state to the `LaunchedEffect` keys. When the app comes back to RESUMED while a run is going,
   send `null` only if something was shown (`hasShownNotification`), so the service goes away while the user can see
   the run in the app. Keep iOS behaviour unchanged: `IosSyncNotifier` only takes the words from this call and follows
   the state itself, so make the gate Android-only (e.g. a `val startsSyncNotifierOnlyWhenLeaving: Boolean` on the
   platform, or do the gating inside `CampfireMainActivity` with the activity's own `lifecycle.currentState`, and
   hand the words over unconditionally). Doing it in `CampfireMainActivity` is the smaller change: remember the last
   notification; in `onPause` start the service if one is current; in `onResume` dismiss it if the run is still going
   (the service stops on its own at the end otherwise).
2. The service must still be started while the activity is at least paused-but-visible, which ON_PAUSE is (the same
   window `onAppPaused` already relies on to start the waiting run). A run started by `onAppPaused` publishes its
   progress milliseconds later; `onSyncNotificationChanged` arriving while the activity is paused must therefore start
   the service at once (not wait for another onPause).
3. Update `app/android/CLAUDE.md` (the `CampfireSyncService` paragraph: "The activity starts the service when a run
   starts…") and the `SyncNotifier` KDoc in `presentation/.../platform/BackgroundSync.kt` if the contract wording
   changes.

## Verification
Manual, Android emulator, connected: toggle a tag and stay in the app; after ~10 s the run happens with no status-bar
icon or notification. Toggle a tag and press Home within 10 s: the notification appears and the run completes in the
background (Dropbox shows the change). Start Sync now on a large library and press Home mid-run: notification appears
and stays until the run ends; reopen the app mid-run: notification goes away, Settings shows the progress.

## Conflicts
`plan 03` changes the service's stop behaviour in `CampfireSyncService.kt`; same lane recommended.

## Open decision (only if the fix needs the user's choice)
- **A (recommended):** service only while the app is not in front, as above.
- **B:** keep the service for runs the user started (Sync now, deletion answers) and skip it only for automatic runs
  in the foreground — needs the run's origin in `SyncState.Connected`.
- **C:** keep today's behaviour (a notification per run is the honest signal that the network is used).
