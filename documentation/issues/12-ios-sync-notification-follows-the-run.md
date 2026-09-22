# 12 · On iOS the sync notification is never seen, and the background task does not follow the run

**Severity:** wrong behaviour (iOS. Every sync run the user leaves the app during: the notification they were asked
permission for never shows, and a run that ends in the background keeps its background task until iOS expires it.
No data is at risk: the expiry stops a run that is already over, and the index marker covers a killed process) ·
**Area:** `:app:ios` (`IosSyncNotifier.kt`, `CampfireViewController.kt`), with KDoc in `:presentation`
(`SyncNotification` in `BackgroundSync.kt`)

## Symptom
1. Connect sync on an iPhone, allow notifications when asked, tap Sync now on a library large enough to take a while,
   then swipe to the home screen.
2. Open Notification Center: there is no "Syncing" notification, at any point of the run. It never appears.
3. If the run finishes within the ~30 s of background time, the app still holds its background task until iOS
   expires it; the expiration handler then calls `CancelSynchronizationUseCase` for a run that is already over.
4. Leaving within the moment between tapping Sync now and the first frame that shows the run means
   `beginBackgroundTask` is never called, and iOS suspends the run straight away.

## Cause
The notifier is driven only by the composition. `SyncNotificationEffect`
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt:870-910`) reads
`viewModel.syncState.collectAsStateWithLifecycle()` and calls `syncNotifier.onSyncNotificationChanged(...)` from a
`LaunchedEffect(notification)`. On iOS both stop the moment the app leaves the screen. In Compose Multiplatform
1.12.0:

- `ComposeContainerLifecycleDelegate.ios.kt:98-106` maps a scene that is not in the foreground to
  `Lifecycle.State.CREATED`, so `collectAsStateWithLifecycle` (minimum `STARTED`) stops collecting;
- `ComposeContainerView.ios.kt:116-117` sets `metalView.redrawer.isActive = isSceneInForeground`, so the frame clock
  stops and nothing recomposes, and no `LaunchedEffect` restarts, whatever the state does.

So once the app is in the background, `IosSyncNotifier.onSyncNotificationChanged`
(`app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosSyncNotifier.kt:60-76`) is never called again: no count
updates, and no `null` that would `endBackgroundTask()`. Every post it does make is made while the app is in the
foreground, and there is no `UNUserNotificationCenterDelegate` anywhere in the project; without a `willPresent`
that asks for `.list`/`.banner`, iOS silences a notification that arrives for an app in the foreground (as if
`UNNotificationPresentationOptionNone` had been passed), so it is neither shown nor kept in Notification Center.
The background task is begun from the same call (`:66`), so it also waits for the first frame that shows the run.

The Android shell does not have the problem because `CampfireSyncService` watches `GetSyncStateUseCase` itself and
renders the counts from the words it was handed (`app/android/src/main/java/com/pandulapeter/campfire/sync/CampfireSyncService.kt:80-97, 170-221`).
`SyncNotification` was built for exactly that (`preparingBody`, `progressBodyFormat`, `withSyncCounts`).

## Fix
Make `IosSyncNotifier` follow the run the way the Android service does: it watches the sync state itself, takes only
the translated words from the composition, begins and ends the background task on the run's own start and end, and
posts only while the app is in the background, where a post is actually shown.

1. `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/CampfireViewController.kt`: make the notifier one per
   process (the SwiftUI representable may create the controller more than once, and two notifiers would both hold
   background tasks and both post), and hand it the state:

   ```kotlin
   /**
    * One per process, like the run it follows: a second one would watch the same state and hold a background task of
    * its own. Created after Koin, which the state comes from.
    */
   private val syncNotifier by lazy {
       IosSyncNotifier(
           syncState = KoinPlatform.getKoin().get<GetSyncStateUseCase>().invoke(),
           onBackgroundTimeExpired = { KoinPlatform.getKoin().get<CancelSynchronizationUseCase>().invoke() },
       )
   }
   ```

   placed after `private val koinApplication by lazy { ... }`, and remove the local `val syncNotifier = IosSyncNotifier(...)`
   from `CampfireViewController()`; the `syncNotifier = syncNotifier` argument then refers to the property. `koinApplication`
   is touched on the first line of `CampfireViewController()`, before the composable (and so the property) is reached.
   Import `com.pandulapeter.campfire.domain.api.useCases.GetSyncStateUseCase`.

2. Replace the body of `IosSyncNotifier` (keep the file header and package). The class KDoc keeps its first two
   paragraphs; the rest:

   ```kotlin
   /**
    * Keeps a sync run going while the app is not in front of the user, and shows what it is doing.
    *
    * iOS is stricter than Android here, and honestly so: ... (the existing second paragraph, unchanged) ...
    *
    * It follows the run itself rather than the composition. Compose stops collecting and stops drawing the moment the
    * scene leaves the foreground, so a notifier driven by the UI hears nothing more once the app is out of sight -
    * which is exactly when the notification is for. The composition only hands over the words, already in the language
    * chosen in the app; the counts come from [syncState], as they do in the Android service.
    *
    * It posts only while the app is in the background. A notification that arrives for an app in the foreground is
    * silenced by iOS unless a notification center delegate asks for it to be shown, and there the settings screen
    * shows the run anyway, so the notification is posted when the app leaves and taken down when it comes back.
    *
    * The notification is informational: iOS has no progress bar in a notification and no way to put a button on one
    * without a registered category, so tapping it opens the app, where the settings screen has the stop action.
    */
   class IosSyncNotifier(
       syncState: Flow<SyncState>,
       /** Stops the run when iOS ends the background time, see [beginBackgroundTask]. */
       private val onBackgroundTimeExpired: () -> Unit,
   ) : SyncNotifier {

       private val scope = MainScope()
       private var backgroundTask: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
       private var hasRequestedAuthorization = false
       private var words: SyncNotification? = null
       private var progress: SyncProgress? = null
       private var isInBackground = false
       private var notificationUpdateJob: Job? = null

       /**
        * Takes down whatever the last launch left behind. (keep the existing paragraph about the stale notification,
        * dropping its sentence about the shared effect, which no longer applies: "A run cannot survive the process,
        * but its notification can: iOS suspending the app mid run is the expected end of a large library (it is what
        * the index's "a run was going" marker exists for), and nothing is left to take the notification down.
        * Without this, Notification Center keeps claiming a sync is in progress for as long as the user leaves it
        * there. Android reaches the same end by a different road and clears it the same way, from the application -
        * see CampfireSyncService.")
        *
        * The observers are never removed, since there is one notifier for the life of the process.
        */
       init {
           removeNotification()
           NSNotificationCenter.defaultCenter.addObserverForName(
               name = UIApplicationDidEnterBackgroundNotification,
               `object` = null,
               queue = NSOperationQueue.mainQueue,
           ) { _ ->
               isInBackground = true
               updateNotification()
           }
           NSNotificationCenter.defaultCenter.addObserverForName(
               name = UIApplicationWillEnterForegroundNotification,
               `object` = null,
               queue = NSOperationQueue.mainQueue,
           ) { _ ->
               isInBackground = false
               cancelNotificationUpdate()
               removeNotification()
           }
           scope.launch {
               syncState
                   .map { (it as? SyncState.Connected)?.progress }
                   .distinctUntilChanged()
                   .collect(::onProgressChanged)
           }
       }

       /**
        * Only the words are taken from here, see the note on the class. Asking for permission happens here as well: a
        * run is started from the settings screen, so this is the moment the user is looking at the app and the question
        * means something, and by the time the app leaves and something is posted, the answer is in.
        */
       override fun onSyncNotificationChanged(notification: SyncNotification?) {
           if (notification == null) return
           words = notification
           if (!hasRequestedAuthorization) {
               hasRequestedAuthorization = true
               UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(UNAuthorizationOptionAlert) { _, _ -> }
           }
       }

       private fun onProgressChanged(newProgress: SyncProgress?) {
           val wasPreparing = progress?.isPreparing != false
           progress = newProgress
           if (newProgress == null) {
               cancelNotificationUpdate()
               removeNotification()
               endBackgroundTask()
           } else {
               // From the state rather than from a frame, so the time is asked for even when the app leaves before the
               // run has been drawn once.
               beginBackgroundTask()
               if (isInBackground) {
                   scheduleNotificationUpdate(isCountingStarted = wasPreparing && !newProgress.isPreparing)
               }
           }
       }

       /** (keep the existing KDoc of beginBackgroundTask and its body unchanged) */
       private fun beginBackgroundTask() { ... }

       private fun endBackgroundTask() { ... }  // unchanged

       /**
        * Posts the latest counts at most once per [NOTIFICATION_UPDATE_INTERVAL], from one delayed job that reads them
        * when it fires: a run finishes several files a second, and a job that only skipped updates inside the interval
        * would leave the count where the last burst stopped. The step from preparing to counting goes out at once, as
        * it changes what the notification says rather than one of its numbers.
        */
       private fun scheduleNotificationUpdate(isCountingStarted: Boolean) {
           if (isCountingStarted) {
               cancelNotificationUpdate()
               updateNotification()
           } else if (notificationUpdateJob?.isActive != true) {
               notificationUpdateJob = scope.launch {
                   delay(NOTIFICATION_UPDATE_INTERVAL)
                   updateNotification()
               }
           }
       }

       private fun cancelNotificationUpdate() {
           notificationUpdateJob?.cancel()
           notificationUpdateJob = null
       }

       /**
        * Re-posted under the same identifier on every change, which is how a notification is updated on iOS: the new
        * request replaces the old one rather than adding a second.
        */
       private fun updateNotification() {
           val words = words ?: return
           val progress = progress ?: return
           if (!isInBackground) return
           val content = UNMutableNotificationContent().apply {
               setTitle(words.title)
               setBody(
                   if (progress.isPreparing) words.preparingBody else words.progressBodyFormat.withSyncCounts(progress.completed, progress.total)
               )
               // (keep the existing comment about the passive interruption level)
               setInterruptionLevel(UNNotificationInterruptionLevel.UNNotificationInterruptionLevelPassive)
           }
           UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(
               UNNotificationRequest.requestWithIdentifier(NOTIFICATION_ID, content, null)
           ) { }
       }

       /** (unchanged) */
       private fun removeNotification() = ...

       private companion object {
           /** Fixed, so that each update replaces the previous notification instead of stacking another one up. */
           const val NOTIFICATION_ID = "sync"

           val NOTIFICATION_UPDATE_INTERVAL = 1.seconds
       }
   }
   ```

   Remove `lastPostMark`, `showNotification`, the `post` extension and the `TimeSource` import: nothing uses them
   any more. Imports to add: `com.pandulapeter.campfire.data.model.domain.SyncProgress`,
   `com.pandulapeter.campfire.data.model.domain.SyncState`,
   `com.pandulapeter.campfire.presentation.ui.platform.withSyncCounts`, `kotlinx.coroutines.Job`,
   `kotlinx.coroutines.MainScope`, `kotlinx.coroutines.delay`, `kotlinx.coroutines.launch`,
   `kotlinx.coroutines.flow.Flow`, `kotlinx.coroutines.flow.distinctUntilChanged`, `kotlinx.coroutines.flow.map`,
   `platform.Foundation.NSNotificationCenter`, `platform.Foundation.NSOperationQueue`,
   `platform.UIKit.UIApplicationDidEnterBackgroundNotification`,
   `platform.UIKit.UIApplicationWillEnterForegroundNotification`. `:app:ios` already compiles against
   coroutines (`IosFileImport.kt`), `:domain:api` and `:data:model`.

   Why the pieces are the way they are:
   - The permission question moves from the first post to the first words. Posts now happen only in the background,
     where a permission alert cannot be shown; asked when the run starts, the question is in front of the user, as
     it is today.
   - `onSyncNotificationChanged(null)` is ignored: the state already says the run is over, and the composition only
     says null after it said something, so it cannot end anything the state has not.
   - `isInBackground` starts false because the notifier is created while the controller is being built, which is
     the app launching into the foreground; it is only ever flipped by the two notifications.
   - Dispatchers.Main on iOS is the main queue, which keeps running while the background task lasts, so the collector
     and the delayed job keep working after the app has left; when iOS suspends the app, they stop with it.
   - Do **not** add a `UNUserNotificationCenterDelegate` that shows the notification in the foreground: the settings
     screen already shows the run there, and a passive notification in the list while the user is watching the
     progress bar is noise.

3. `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/BackgroundSync.kt`, the
   `SyncNotification` KDoc: replace "for a platform whose notification outlives the UI that resolved these strings: on
   Android the run and its notification carry on after the app is swiped away, and from that point there is no
   composition left to send a new [body] as the count goes up. Handing the service the pieces lets it keep the text
   moving on its own, still in the language chosen in the app." with "for a platform whose notification outlives the
   UI that resolved these strings: on Android the run and its notification carry on after the app is swiped away, and
   on iOS the composition stops as soon as the app is in the background, so from that point there is nothing left to
   send a new [body] as the count goes up. Handing the shell the pieces lets it keep the text moving on its own, still
   in the language chosen in the app."

   `SyncNotificationEffect`'s own KDoc and body stay as they are; Android still needs its null.

## Tests
None (platform shell).

## Verify
iOS simulator or device, sync key configured, a Dropbox folder with a few hundred songs (or throttle the network with
the Network Link Conditioner so a small library takes a while):
1. First run: tap Sync now. The notification permission alert appears while Settings is in front. Allow.
2. Swipe to the home screen while it runs. Pull down Notification Center within a second or two: "Syncing" with the
   current count (or "Preparing"), and the count moves while you watch it (at most once a second).
3. Come back to the app: the notification is gone, the progress row in Settings shows the run.
4. Leave again and let the run finish in the background: the notification disappears when it finishes. With a
   breakpoint or a `println` in `endBackgroundTask`, confirm it is called at the end of the run, not ~30 s later
   from the expiration handler.
5. A run longer than the background time: iOS expires the task, the run is stopped, and the app reports it as
   interrupted next time it is opened (unchanged behaviour).
6. Language set to Hungarian in the app: the notification is in Hungarian.
7. Compile: `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64`, then the `xcodebuild` command from the root
   `CLAUDE.md`.

## Docs
`app/ios/CLAUDE.md`, replace the paragraph starting "`IosSyncNotifier.kt` holds a `beginBackgroundTask`..." up to
"...so stopping a run is done in the app." with:

"`IosSyncNotifier.kt` holds a `beginBackgroundTask` and posts a local notification while a sync run lasts, handed to
`CampfireIosApp` as its `SyncNotifier`. It is one per process and follows the run itself, the way the Android service
does: it collects `GetSyncStateUseCase`, begins the background task when a run starts and ends it, and takes the
notification down, when the run ends, and takes nothing from the composition but the translated words. Compose stops
collecting and drawing as soon as the scene leaves the foreground, so a notifier driven by the UI would hear nothing
once the app is out of sight. It posts only while the app is in the background — when it leaves, and then at most
once a second as the counts move — and takes the notification down when the app comes back, because iOS silences a
notification that arrives for an app in the foreground and the settings screen shows the run there anyway. The
permission is asked for when a run starts, while the user is looking at the app. iOS is stricter than Android here: a
background task buys tens of seconds, not minutes, so when iOS says that time is up the run is stopped — it writes its
index and reports itself as interrupted, the way Android's `onTimeout` does — and a process killed before that is
still found by the index's "a run was going" marker at the next start. The notification is informational (iOS has no
progress bar in one, and no button without a registered category), so stopping a run is done in the app."

## Touches
- `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosSyncNotifier.kt`
- `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/CampfireViewController.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/BackgroundSync.kt`
- `app/ios/CLAUDE.md`

## Depends on
Nothing.
