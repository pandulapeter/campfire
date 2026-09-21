# 50 · Rotating during a Play update download brings the "Update available" dialog back, and a late Play answer can crash

**Severity:** minor — wrong behaviour (android, Play-installed builds only; every rotation during a flexible download), plus a crash with a very narrow window (a required update whose check is answered just after the Activity was recreated) · **Area:** `:presentation` `androidMain` — `ui/platform/AppUpdate.android.kt` (`rememberAppUpdateController`, `AndroidAppUpdateController`)

## Symptom
A release with `updatePriority` 2–3 is on Play. The user taps **Update** in Campfire's dialog, accepts Play's sheet,
and the download starts in the background.

1. They rotate the phone (or change the theme of the system, or fold the device — anything that recreates the
   Activity). The "Update available" dialog is back, over an update that is already downloading. Tapping **Update**
   again asks Play to start a flow it is already running.
2. The download finishes while the app is in front: nothing happens. The "Restart to finish" dialog only appears
   the next time the app is left and returned to.
3. Same family: on the blocking screen of a required update (priority 4–5) the user backs out of Play's full screen
   flow, lands on Campfire's blocking screen, and rotates. Play's flow opens again by itself — on every rotation.
4. The crash: the app is started or resumed with a required update pending, and the Activity is recreated in the
   few hundred milliseconds between the check being sent and Play answering it (a rotation during launch, a
   configuration change delivered on resume). The app dies with
   `IllegalStateException: Attempting to launch an unregistered ActivityResultLauncher`.

None of it can be seen outside a build installed from a Play track.

## Cause
`presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/AppUpdate.android.kt`.

The controller is remembered against the Activity (`:57`), so a recreation makes a new one that knows nothing:
`state` starts at `NotAvailable`, no install listener is registered (the old controller's `release()` took it away),
and `hasStartedImmediateFlow` is `false` again. Only `isPostponed` was lifted out into saved state (`:56`).

```kotlin
val isPostponed = rememberSaveable { mutableStateOf(false) }
val controller = remember(activity) { AndroidAppUpdateController(activity, isPostponed) }
```

The new controller's first check then maps Play's answer without ever looking at a download in progress (`:192-208`):
`installStatus()` is only compared with `DOWNLOADED`, so `UPDATE_AVAILABLE` + `DOWNLOADING` falls through to the
priority branches and comes out as `Optional` — the dialog.

```kotlin
installStatus() == InstallStatus.DOWNLOADED -> if (isPostponed) AppUpdateState.NotAvailable else AppUpdateState.ReadyToInstall
updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE -> AppUpdateState.NotAvailable
```

Worse, the first branch takes `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS` for "an immediate update the user walked out
of" unconditionally. Play's documentation does not promise that a *flexible* flow in progress is reported as
`UPDATE_AVAILABLE`; several reports say it is reported as `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS` as well. If that is
so, the rotation in (1) does not bring back the dialog but the **blocking screen**, and starts an immediate flow for
an update that was meant to be optional. This could not be checked from here (it takes a Play track), so the fix is
written to be right under either answer.

The crash (`:96-97`, `:156-163`, `:114-126`): the success listener is not tied to anything.

```kotlin
appUpdateManager.appUpdateInfo
    .addOnSuccessListener(::onAppUpdateInfoReceived)
```

A controller whose composition is gone still receives the answer, maps it to `Required`, and calls `startUpdate()`
with the launcher it was given — which `rememberLauncherForActivityResult` unregistered when it left the
composition. `startUpdateFlowForResult` launches it synchronously and is not inside any `try`.

## Fix
All in `AppUpdate.android.kt`. No change to the `AppUpdateController` contract, `AppUpdateGate.kt` or the other three
actuals.

1. **`rememberAppUpdateController()`** — lift the second answer-the-user-already-gave out of the controller, next to
   the first:

   ```kotlin
       // Kept outside the controller so that a rotation does not undo them: the activity, and with it everything
       // remembered against it, is recreated, while a "later" the user already gave should still stand, and an
       // immediate flow they already backed out of should not open itself again.
       val isPostponed = rememberSaveable { mutableStateOf(false) }
       val hasStartedImmediateFlow = rememberSaveable { mutableStateOf(false) }
       val controller = remember(activity) {
           AndroidAppUpdateController(
               activity = activity,
               isPostponed = isPostponed,
               hasStartedImmediateFlow = hasStartedImmediateFlow,
           )
       }
   ```

   and in the class: a third constructor parameter `hasStartedImmediateFlow: MutableState<Boolean>,` and
   `private var hasStartedImmediateFlow by hasStartedImmediateFlow` in place of the plain `var` on line 82.

2. **`release()`** — a controller that has left the composition must not act on anything that arrives afterwards:

   ```kotlin
       /**
        * Play's answer to a check is not tied to the activity that asked: it can arrive after this controller has left
        * the composition, when the launcher it holds is no longer registered and launching it throws.
        */
       private var isReleased = false

       fun release() {
           isReleased = true
           launcher = null
           unregisterInstallListener()
       }
   ```

3. **`checkForUpdate()`** — drop the early return. It existed because a check during a download was answered with
   `Optional`; with step 5 it is answered with `Downloading`, and asking is what heals a `DOWNLOADED` event that was
   missed (it is the only thing that can, for a controller that registered its listener after the fact):

   ```kotlin
       fun checkForUpdate() {
           appUpdateManager.appUpdateInfo
               .addOnSuccessListener(::onAppUpdateInfoReceived)
               // A failed check is the normal answer for a build Play did not install, and there is nothing the user
               // could do about a real failure either: the app simply stays the version it is.
               .addOnFailureListener { }
       }
   ```

4. **`onAppUpdateInfoReceived()`**:

   ```kotlin
       private fun onAppUpdateInfoReceived(appUpdateInfo: AppUpdateInfo) {
           if (isReleased) return
           availableUpdate = appUpdateInfo
           val newState = appUpdateInfo.toAppUpdateState()
           // Play takes a moment to notice a download it has just been asked for, and the check that runs as its sheet
           // closes can still be answered with the offer. The offer has been taken, so it is not made again.
           if (state == AppUpdateState.Downloading && newState == AppUpdateState.Optional) return
           state = newState
           // A download this controller did not start - one that was going when the activity was recreated - has
           // nobody listening for its end yet.
           if (newState == AppUpdateState.Downloading) registerInstallListener()
           // A required update is started without asking, but only the first time: after that the blocking screen's
           // own button is what starts it again, so that cancelling the Play flow cannot turn into a loop of the app
           // reopening it the moment the user is back.
           if (newState == AppUpdateState.Required && !hasStartedImmediateFlow) startUpdate()
       }
   ```

   `registerInstallListener()` is already idempotent, and `release()` / the `DOWNLOADED`, `CANCELED` and `FAILED`
   branches already unregister it.

5. **`toAppUpdateState()`** — look at the install status before the availability, and let the priority say which
   kind of flow an update in progress is. The priority is the whole policy (4–5 is only ever started as immediate,
   2–3 only ever as flexible), so it answers that question whatever `updateAvailability()` Play reports for a
   flexible flow. Replace the body, keep the KDoc above it:

   ```kotlin
       private fun AppUpdateInfo.toAppUpdateState(): AppUpdateState {
           val isRequired = updatePriority() >= MINIMUM_REQUIRED_PRIORITY
           val isInProgress = updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
           return when {
               // An immediate update the user walked out of halfway; Play resumes it, and until it does the app is the
               // old one, which is exactly what the blocking screen is for. Play does not say which kind of flow is in
               // progress, but the priority does: a required update is never started as anything else.
               isInProgress && isRequired -> AppUpdateState.Required

               installStatus() == InstallStatus.DOWNLOADED -> if (isPostponed) AppUpdateState.NotAvailable else AppUpdateState.ReadyToInstall

               // A flexible download that is going already, which is what a recreated activity finds.
               installStatus() == InstallStatus.PENDING || installStatus() == InstallStatus.DOWNLOADING -> AppUpdateState.Downloading

               updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE -> AppUpdateState.NotAvailable

               isRequired -> if (isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) AppUpdateState.Required else AppUpdateState.NotAvailable

               updatePriority() >= MINIMUM_OPTIONAL_PRIORITY ->
                   if (!isPostponed && isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) AppUpdateState.Optional else AppUpdateState.NotAvailable

               else -> AppUpdateState.NotAvailable
           }
       }
   ```

   `InstallStatus.INSTALLING` is deliberately not mapped: it follows `completeUpdate()`, and the process is about to
   be replaced.

6. **`startUpdate()`** — both launches go through one guarded helper. `startUpdateFlowForResult` throws when the
   launcher is not registered and when the `AppUpdateInfo` has been used before (it is single use), and returns
   `false` when Play refuses the flow:

   ```kotlin
       override fun startUpdate() {
           val appUpdateInfo = availableUpdate ?: return
           val launcher = launcher ?: return
           isPostponed = false
           when (state) {
               AppUpdateState.Required -> {
                   hasStartedImmediateFlow = true
                   // The blocking screen stays whatever happens here, and its button is the way to try again.
                   if (!launchUpdateFlow(appUpdateInfo, launcher, AppUpdateType.IMMEDIATE)) checkForUpdate()
               }

               AppUpdateState.Optional -> {
                   registerInstallListener()
                   // Said before Play is asked, so that the hint is gone by the time its sheet is over the app.
                   state = AppUpdateState.Downloading
                   if (!launchUpdateFlow(appUpdateInfo, launcher, AppUpdateType.FLEXIBLE)) {
                       unregisterInstallListener()
                       state = AppUpdateState.NotAvailable
                       checkForUpdate()
                   }
               }

               else -> Unit
           }
       }

       /**
        * Whether Play took the flow. An [AppUpdateInfo] starts one flow and no more, and the launcher belongs to a
        * composition that may be gone, so this can throw as well as refuse; either way the answer in hand is spent,
        * and the caller asks for a new one.
        */
       private fun launchUpdateFlow(
           appUpdateInfo: AppUpdateInfo,
           launcher: ActivityResultLauncher<IntentSenderRequest>,
           @AppUpdateType updateType: Int,
       ) = try {
           appUpdateManager.startUpdateFlowForResult(appUpdateInfo, launcher, AppUpdateOptions.newBuilder(updateType).build())
       } catch (exception: Exception) {
           false
       }
   ```

   The `checkForUpdate()` after a failed flexible launch maps to `Optional` again (the dialog comes back with a
   fresh `AppUpdateInfo`), which is right: the user asked for the update and did not get it. It cannot loop, since
   nothing starts a flexible flow but a tap.

7. **`onUpdateFlowResult()`** — the result of a Play sheet that was open across a recreation is delivered to the
   *new* controller, whose state is not `Downloading` yet, so a declined sheet is followed by Campfire's own dialog
   asking the same question. Decline whenever the flow was not the required one:

   ```kotlin
       fun onUpdateFlowResult(isSuccessful: Boolean) {
           // Backing out of the Play sheet is the user declining the download, and only the flexible flow can be
           // declined - an immediate one is re-offered by the next check, which does not ask whether it was postponed.
           // The state is not asked whether it is a download: a sheet that was open while the activity was recreated
           // answers to a controller that has not heard from Play yet.
           if (!isSuccessful && state != AppUpdateState.Required) {
               unregisterInstallListener()
               postponeUpdate()
           }
       }
   ```

   This is safe for a required update whose controller has not been answered yet either: `postponeUpdate()` sets
   `isPostponed`, and the `Required` branches of step 5 never read it.

Do **not** move the controller into the view model or make `AppUpdateManager` a Koin singleton to "survive" rotation:
Play is the source of truth and is asked on every resume; everything above is about mapping its answer completely.

## Tests
None (UI is untested, and Play Core has no fake in the project; `FakeAppUpdateManager` would need a test module the
repo does not have).

## Verify
Only possible from a Play track (internal testing). Upload version N+1 with `update-priority: 3`, install N from the
track, and:
1. Open the app, tap **Update**, accept Play's sheet, rotate while the notification shows the download: no dialog
   comes back. Keep the app in front until the download ends: the "restart" dialog appears without leaving the app.
2. Same, but decline Play's sheet after rotating with the sheet open: Campfire's dialog does not come back in this
   session.
3. Upload N+2 with `update-priority: 5`. Open the app: Play's full screen flow starts. Back out: blocking screen.
   Rotate: the blocking screen stays, Play's flow does not reopen; the **Update** button still opens it.
4. With N+2 pending, rotate the device repeatedly while cold-starting the app (or enable "Don't keep activities"
   and switch away and back during start): no crash. `adb logcat | grep -i "unregistered ActivityResultLauncher"`
   stays empty.
5. While a priority 3 download is going, rotate and watch which screen appears; if it is ever the blocking screen,
   the `isInProgress && isRequired` branch is not being reached the way this plan assumes — capture
   `updateAvailability()`, `installStatus()` and `updatePriority()` in a log line and revisit step 5.

Compile: `./gradlew :app:android:assembleDebug` (a debug build answers every check with an error, so it only proves
that it builds).

## Docs
Root `CLAUDE.md`, Updates section, and `presentation/CLAUDE.md` (the `ui/platform/AppUpdate.kt` bullet): neither
says anything that becomes untrue. Add one sentence to the root file's first bullet, after "The thresholds live in
`AppUpdate.android.kt`": "The priority also says which kind of flow an update already in progress is, since Play's
answer does not — which is what lets an Activity recreated mid-download pick the download up instead of offering
it again."

## Touches
- `presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/AppUpdate.android.kt`
- `CLAUDE.md`

## Depends on
Nothing. Plan 47 (a required update covering an unsaved editor) edits `AppUpdateGate.kt` and possibly
`installUpdate()` / the `AppUpdateController` contract in this file's neighbourhood; the hunks here are in
`rememberAppUpdateController`, `checkForUpdate`, `onUpdateFlowResult`, `release`, `startUpdate`,
`onAppUpdateInfoReceived` and `toAppUpdateState`, so land them one after the other rather than side by side.
