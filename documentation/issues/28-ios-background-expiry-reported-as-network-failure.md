# 28 · iOS: a sync run the system suspended in the background comes back as "could not reach Dropbox" instead of "interrupted"

**Severity:** wrong behaviour (iOS; likely for any first sync of a library that takes longer than the ~30 s of
background time — which the docs call "the expected end of a large library") · **Area:** `:app:ios` (`IosSyncNotifier`)

## Symptom
1. iOS, connected account, a few hundred songs to bring down. Start **Sync now**, then leave the app.
2. About 30 s later iOS ends the background task and suspends the process in the middle of the run's requests.
3. Come back a few minutes later. The run resumes only to fail: Settings says the sync failed because Dropbox could not
   be reached (`settings_sync_failed_network`), although the network is fine. What the design promises for exactly
   this case — `SyncOutcome.Interrupted`, "stopped, carry on with Sync now" (`IosSyncNotifier.kt:22-27`,
   `app/ios/CLAUDE.md`) — only happens if iOS also *kills* the suspended process, not when it merely resumes it.

## Cause
`IosSyncNotifier.kt:66-71`:

```kotlin
backgroundTask = UIApplication.sharedApplication.beginBackgroundTaskWithName("sync") {
    endBackgroundTask()
}
```

The expiration handler only ends the task; the run is left going and is frozen mid-request. On resume, Ktor's
`HttpTimeout` (60 s, `HttpClientConfiguration.kt`), which runs on the coroutine clock, has long expired, or
`NSURLSession` reports the torn-down connection (`NSURLErrorNetworkConnectionLost`); `DropboxSyncProvider.transport`
turns either into `SyncNetworkException` (`DropboxSyncProvider.kt:399-400`), which ends the run as
`SyncOutcome.Failure(SyncFailureReason.NETWORK)` (`SyncRepositoryImpl.kt:384-387`).

Android handles the equivalent moment — the system saying the background allowance is over — by stopping the run
(`CampfireSyncService.onTimeout`, `CampfireSyncService.kt:139-143`), which reports `Interrupted` and writes the index
while the process can still run.

## Fix
Stop the run when iOS says the time is up, then end the task, the way Android's `onTimeout` does.

1. `app/ios/build.gradle.kts`: add `implementation(project(":domain:api"))` next to the other project dependencies
   (`:app:android` has the same one for the same use case).
2. `IosSyncNotifier` takes what to do on expiry rather than knowing about Koin:
   ```kotlin
   class IosSyncNotifier(
       /** Stops the run when iOS ends the background time, see [beginBackgroundTask]. */
       private val onBackgroundTimeExpired: () -> Unit,
   ) : SyncNotifier
   ```
   and the handler becomes
   ```kotlin
   backgroundTask = UIApplication.sharedApplication.beginBackgroundTaskWithName("sync") {
       // Stopped rather than left to be frozen mid request: frozen, it resumes into requests that have timed out and
       // reports a network failure; stopped, it writes its index and says it was interrupted, which is what it was.
       onBackgroundTimeExpired()
       endBackgroundTask()
   }
   ```
   Update the KDoc of `beginBackgroundTask` ("The expiration handler is not optional …") with the same reason.
3. `CampfireViewController.kt:29`: `IosSyncNotifier(onBackgroundTimeExpired = {
   KoinPlatform.getKoin().get<CancelSynchronizationUseCase>().invoke() })` — the same lookup
   `CampfireSyncService.onTimeout` uses; Koin is started two lines above by `koinApplication`.
4. The stopped run's clean up (`finishRunCutShort`: the index write and the rescan) is asynchronous and may itself be
   suspended half way; that is fine — it resumes with the app, and the marker written at the start still covers a kill.
   Do **not** wait for it inside the handler: iOS kills an app whose handler does not return promptly.

## Tests
None (app shell, untested).

## Verify
1. `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64`, run on the simulator with a Dropbox test account holding a
   few hundred songs, empty library on the device.
2. **Sync now**, then Home. Wait ~40 s (Xcode's console shows the expiration), come back after two more minutes.
   Settings says the run was interrupted; **Sync now** carries on and downloads only the rest.
3. Same with the app killed from the switcher while suspended: still "interrupted" at the next start (unchanged).

## Docs
`app/ios/CLAUDE.md`, the `IosSyncNotifier.kt` paragraph: "so a large library is suspended mid run — which is the case
the index's "a run was going" marker exists for" becomes "so when iOS says that time is up the run is stopped — it
writes its index and reports itself as interrupted, the way Android's `onTimeout` does — and a process killed before
that is still found by the index's "a run was going" marker at the next start".

## Touches
- `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosSyncNotifier.kt`
- `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/CampfireViewController.kt`
- `app/ios/build.gradle.kts`
- `app/ios/CLAUDE.md`

## Depends on
Nothing. 44 also edits `IosSyncNotifier.kt` (the posting); schedule one after the other.
