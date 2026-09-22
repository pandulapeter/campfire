# 44 · During a sync the app restarts the Android foreground service, and re-posts the iOS notification, for every single file

**Severity:** performance (Android and iOS; certain on every run with anything to move — a first sync of a few hundred
songs is a few hundred service starts on the main thread, at the rate files finish, six at a time) · **Area:** `:presentation` (`CampfireApp.SyncNotificationEffect`), `:app:android` (`CampfireActivity.onSyncNotificationChanged`), `:app:ios` (`IosSyncNotifier`)

**Verifier:** the activity's guard is state-based rather than a one-second timer: it starts the service when the
service is not running (a process-wide flag the service keeps) or the words changed, which is exact where the timer
was a guess; the two notification-post throttles stay time-based, since the platform limit they answer is a rate.

## Symptom
While a run moves files with the app open (a first sync is started from Settings and usually watched there):
- Android: every finished file is a `startForegroundService` binder call from the main thread, an `onStartCommand`,
  a `startForeground` (a second binder call that re-posts the notification) and — from the service's own state
  watcher — a third post of the same notification. Files finish several times a second, so the system's notification
  rate limit (five updates a second per package) starts shedding updates and logging `Package enqueue rate is …
  Shedding …`, and the UI thread spends its frames in IPC while the user scrolls the list that the run is filling.
- iOS: every finished file is an `addNotificationRequest` of a passive notification, while the app is in front where
  nobody can see it.

## Cause
`CampfireApp.kt:822-858`: the notification is a data class that includes the counts and the rendered body, and the
effect is keyed on it:

```kotlin
val body = if (progress == null || progress.isPreparing) preparing else progressBodyFormat.withSyncCounts(progress.completed, progress.total)
val notification = progress?.let { SyncNotification(…, body = body, …, progress = it) }
…
LaunchedEffect(notification) {
    if (notification != null) {
        hasShownNotification = true
        syncNotifier.onSyncNotificationChanged(notification)
```

and `SyncRepositoryImpl` emits a new `progress` for every finished operation (`SyncEngine.kt:215`,
`SyncRepositoryImpl.kt:330-333`).

- Android: `CampfireActivity.kt:107-131` answers every call with `ContextCompat.startForegroundService(…)`, and
  `CampfireSyncService.onStartCommand` (`:107-121`) with `ServiceCompat.startForeground(…)`, while the service's own
  collector (`:79-92`) already re-renders the notification from the state for every progress change
  (`updateNotification`, `:162-166`) — which is how it keeps counting after the activity is gone, and which makes the
  activity's per-file restarts redundant.
- iOS: `IosSyncNotifier.showNotification` (`:78-93`) re-posts on every call.

## Fix
The shell needs to hear about a run starting, its words changing (a language switch) and it ending — not about every
file.

1. Android. `CampfireSyncService` keeps whether it is in the foreground where the activity can see it — the two are
   one process — as a companion `@Volatile var isRunning = false` (KDoc: "Whether a started service is in the
   foreground, for the activity to tell whether a run's progress needs a start at all. Process-wide, like the
   service."), set to `true` next to `isInForeground = true` in `onStartCommand` and to `false` in `stop()` and in
   `onDestroy()`.
   `CampfireActivity.onSyncNotificationChanged` then starts the service only when it has to:
   ```kotlin
   /**
    * The words the service was last started with. The counts need no forwarding - the service renders them from the
    * sync state itself - so a run's progress starts it again only when it is not running (it stops itself when it
    * sees a run end, which the activity may not have been watching) or when the words changed (a language switch).
    */
   private var lastSyncServiceWords: List<String>? = null
   ```
   with `words = listOf(channelName, title, preparingBody, progressBodyFormat, stopLabel)`: start
   (`startForegroundService`, as now) when `!CampfireSyncService.isRunning || words != lastSyncServiceWords`, then
   remember the words; on `notification == null` send the dismiss as now and set `lastSyncServiceWords = null`.
   Between a start and its `onStartCommand` (both on the main thread, one message apart) a few more progress updates
   may start it again; that is harmless and bounded.
   Do **not** reduce this to "start once per run": a service that stopped itself at the end of one run while the
   activity was in the background would then never be started for the next.
   Also throttle the service's own `updateNotification` to at most one post per ~500 ms (keep the latest state and
   post it from one delayed job on `scope`), posting at once when `total` leaves 0 (preparing → counting). This one is
   time-based on purpose: Android's limit is five enqueues a second per package, and what it sheds is arbitrary.
2. iOS, `IosSyncNotifier.onSyncNotificationChanged`: begin the background task as now, but post the notification only
   when there is none yet or at most once a second (`TimeSource.Monotonic` mark kept in the notifier); the passive
   notification is only read from Notification Center anyway.
3. `CampfireApp.SyncNotificationEffect` stays as it is — the contract "called with what to show while a run is going"
   is unchanged, and a platform that shows only what it is handed (iOS) still gets the latest body.
4. Do **not** drop the `body` from `SyncNotification` or stop sending progress to the notifier: iOS renders from it.

## Tests
None (platform shells, untested).

## Verify
1. Android emulator, debug build, a Dropbox test folder with ~300 songs, empty library. **Sync now** with Settings
   open, then scroll the song list during the run.
2. `adb logcat | grep -E "Shedding|CampfireSyncService"`: before, shedding lines and one `onStartCommand` per file;
   after, one start per run (a handful at most), no shedding.
3. Start a run, press Home, let it finish; reopen, **Sync now** at once, press Home: the second
   run still gets its notification and keeps going in the background.
4. The notification still counts up, still has **Stop**, still disappears at the end; swipe the app away mid-run —
   the notification keeps counting (service collector) and disappears when the run ends.
5. iOS simulator: the notification appears at the start of a run and, with the app backgrounded, shows a recent count.
6. `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64`.

## Docs
`app/android/CLAUDE.md` (the `CampfireSyncService` / activity paragraph, if it describes the start per update) and
`app/ios/CLAUDE.md` (`IosSyncNotifier.kt` paragraph): "The activity starts the service when a run starts, when its
words change and whenever the service is not running; the counts are the service's own business, rendered from the sync
state at most twice a second." / "posted when a run starts and then at most once a second".

## Touches
- `app/android/src/main/java/com/pandulapeter/campfire/CampfireActivity.kt`
- `app/android/src/main/java/com/pandulapeter/campfire/sync/CampfireSyncService.kt`
- `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosSyncNotifier.kt`
- `app/android/CLAUDE.md`, `app/ios/CLAUDE.md`

## Depends on
Nothing; 28 edits `IosSyncNotifier.kt` too (the expiration handler) — schedule one after the other.
