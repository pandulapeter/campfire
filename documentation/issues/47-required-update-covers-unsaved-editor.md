# 47 · Android: a required update covers an editor with unsaved text, and "Restart" does not ask either

**Severity:** data loss (android, Play-installed builds only; needs a release published with `updatePriority` 4–5 —
or a flexible update that has finished downloading — to meet an editor with unsaved text, which is exactly what a
resume from the browser the chords were copied from looks like) · **Area:** `:presentation` — `ui/AppUpdateGate.kt`,
`ui/platform/AppUpdate.android.kt`, `CampfireViewModel`, `CampfireApp`

## Symptom
Required update (priority 4–5):
1. The user is typing a song, switches to a browser to copy the chords, and comes back.
2. `ON_RESUME` → the check → Play reports a priority-5 update → the immediate flow is started without asking.
3. Accepting it installs the update and restarts the app: the text typed since the last save is gone. Declining it
   lands on the blocking screen, which covers the (still composed) editor and offers two things only: **Update**, and
   Back, which is `activity.finish()`. There is no branch left in which the text can be saved.

Flexible update (priority 2–3), milder: once the download has finished, the "Update downloaded — Restart" dialog is
put up over the editor on the next resume, and **Restart** calls `completeUpdate()`, which ends the process, without
looking at the editor. A sync run that is going is cut off the same way.

## Cause
`presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/AppUpdate.android.kt`,
`onAppUpdateInfoReceived`, starts the immediate flow from the check's answer, whatever the app is showing:

```kotlin
state = appUpdateInfo.toAppUpdateState()
…
if (state == AppUpdateState.Required && !hasStartedImmediateFlow) startUpdate()
```

and `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/AppUpdateGate.kt:62-95` draws from the
controller's state alone:

```kotlin
AnimatedVisibility(visible = state == AppUpdateState.Required, …) { AppUpdateRequiredScreen(…) }
…
AppUpdateState.ReadyToInstall -> AppUpdateDialog(…, onConfirm = controller::installUpdate, …)
```

Neither knows about `CampfireViewModel.hasUnsavedEditorChanges`, which so far only `navigateBack` and `requestExit`
consult.

## Fix
The gate **waits**: nothing of an update that ends the process is put in the user's way while the editor holds text
that has not been written. The moment the text is saved or the editor is left — by the editor's own Save, or through
the ordinary *Unsaved changes* question on the way out, which after plan 05 only lets go once the write has
succeeded — the gate carries on from where it was held.

Two alternatives were considered and rejected. Writing the draft from the gate before starting the update (the
reviewer's second suggestion) breaks the rule that nothing but the user's Save ever writes a song. Asking the
*Unsaved changes* question the moment the update arrives answers a question nobody asked ("before leaving the
editor?") and still needs the waiting for its Cancel answer — so the waiting is the whole mechanism, and the
existing question keeps being asked where it always was.

A sync run is treated differently on the two paths, on purpose:
- **Restart** (`completeUpdate`) waits for a run as well. It costs a boolean, the dialog is dismissible and in no
  hurry, and it spares the user a needless "the last sync was interrupted".
- The **required** update does not wait for a run. A first sync can take many minutes, and priority 4–5 is reserved
  for builds nobody should be left on. Cutting a run off is what the engine is built for: every file is written
  atomically, the index carries the "a run was going" marker from before anything moved, so the next start reports
  the run as interrupted and leaves it to the user to start again, and the stale notification is cleared at process
  start. Nothing is lost.

The **Optional** offer ("Update available") is left alone: accepting it only starts a background download.

1. **`CampfireViewModel.kt`**, after `syncState` (`:310`):

   ```kotlin
   /** True while a sync run is going. Acted on rather than drawn: a restart the app offers by itself waits for it. */
   val isSyncing = syncState.map { it is SyncState.Connected && it.isSyncing }.asState(false)
   ```

2. **`AppUpdateGate.kt`** — the gate takes the view model and decides *when*; the controller keeps deciding *what*.

   ```kotlin
   @Composable
   internal fun AppUpdateGate(
       modifier: Modifier = Modifier,
       viewModel: CampfireViewModel,
       content: @Composable () -> Unit,
   ) {
       val controller = rememberAppUpdateController()
       val state = controller.state
       val hasUnsavedEditorChanges by viewModel.hasUnsavedEditorChanges.collectAsStateWithLifecycle()
       val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
       // A required update ends with the process being replaced, and the screen it puts up leaves no way back to
       // the app, so neither is allowed near an editor holding text that has not been written: both wait until it
       // has been saved or let go of. Once the screen is up it stays up - the text can only become unsaved behind
       // it by the file changing underneath an editor nobody typed into - and a rotation must not uncover the app.
       var isRequiredUpdateInTheWay by rememberSaveable { mutableStateOf(false) }
       LaunchedEffect(state, hasUnsavedEditorChanges) {
           if (state == AppUpdateState.Required && !hasUnsavedEditorChanges) isRequiredUpdateInTheWay = true
       }
       val isRequiredScreenVisible = isRequiredUpdateInTheWay && state == AppUpdateState.Required
       // Started without asking as the screen goes up, which the controller only does once: after that the
       // screen's own button is what starts it again.
       LaunchedEffect(isRequiredScreenVisible) {
           if (isRequiredScreenVisible) controller.startRequiredUpdateOnce()
       }
       Box(modifier = modifier.fillMaxSize()) {
           content()
           AnimatedVisibility(
               visible = isRequiredScreenVisible,
               enter = fadeIn(),
               exit = fadeOut(),
           ) {
               AppUpdateRequiredScreen(
                   onUpdate = controller::startUpdate,
                   onClose = controller::closeApp,
               )
           }
       }
       when (state) {
           AppUpdateState.Optional -> AppUpdateDialog(…)   // unchanged

           // Restarting is the app ending itself, so it is not offered over text that would go with it, nor over a
           // sync run it would cut off. The offer is still there when the text is saved or the run has finished.
           AppUpdateState.ReadyToInstall -> if (!hasUnsavedEditorChanges && !isSyncing) {
               AppUpdateDialog(…)                           // unchanged
           }

           AppUpdateState.NotAvailable, AppUpdateState.Required, AppUpdateState.Downloading -> Unit
       }
   }
   ```
   New imports: `LaunchedEffect`, `getValue`, `setValue`, `mutableStateOf`, `rememberSaveable`,
   `collectAsStateWithLifecycle`. Extend the function's KDoc with one paragraph saying what the comment above says.

3. **`CampfireApp.kt:177`**: `AppUpdateGate(viewModel = viewModel) { … }`.

4. **`AppUpdate.kt`** — the contract gains the one thing the gate now asks for, after `startUpdate()`:

   ```kotlin
   /**
    * Starts a [AppUpdateState.Required] update without having been asked to, which is done once: after that the
    * blocking screen's own button is what starts it again, so that cancelling the store's flow cannot turn into a
    * loop of the app reopening it the moment the user is back. Called by [AppUpdateGate] as it puts that screen
    * up, which is not necessarily when the update was found - see there for what it waits for.
    */
   fun startRequiredUpdateOnce()
   ```
   and `override fun startRequiredUpdateOnce() = Unit` in `NoAppUpdates`. The three no-op actuals are untouched.

5. **`AppUpdate.android.kt`** — the trigger moves out, the once-only stays:
   - in `onAppUpdateInfoReceived`, delete the comment and the line
     `if (state == AppUpdateState.Required && !hasStartedImmediateFlow) startUpdate()`;
   - add, next to `startUpdate()`:

     ```kotlin
     override fun startRequiredUpdateOnce() {
         if (state == AppUpdateState.Required && !hasStartedImmediateFlow) startUpdate()
     }
     ```
   `hasStartedImmediateFlow` and the line that sets it in `startUpdate` stay exactly as they are, as does everything
   else in the file; `startUpdate()` already returns early while there is no `availableUpdate` or launcher.

Do **not** make the blocking screen dismissible, add a "save" button to it, or let Back through to the app behind
it: with the waiting in place it never covers unsaved text in the first place.

Known limit, outside the app's control: a flexible update that has been downloaded and never restarted into is
eventually installed by Play while the app is in the background, which ends the process like any background kill.
What survives that is the editor's saved state, which is plan 14's subject.

## Tests
None (the UI is untested, and Play answers every check with an error outside a Play-installed build).

## Verify
Compile: `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`.

The real flow only exists on a Play track: upload build N to internal testing, install it from Play, then publish
N+1 with `update_priority` 5 (and, for a second pass, 3).
1. Priority 5, editor open with unsaved text, Home, reopen: no Play sheet, no blocking screen; the editor is usable.
   Press Save: the blocking screen fades in and the Play flow starts. Cancel the Play flow: the blocking screen
   stays, the flow does not reopen by itself, Update reopens it, Back closes the app.
2. Priority 5, unsaved text, press Back in the editor → **Discard** (or **Save**): the same happens once the editor
   has been left.
3. Priority 5 with no editor open: immediate flow on resume, as before. Rotate on the blocking screen: still covered.
4. Priority 3: accept, wait for the download, open the editor and type, Home, reopen: no "Restart" dialog. Save: the
   dialog appears; Restart installs. Repeat with a sync run going (a large first sync) and no editor: the dialog
   appears when the run finishes.
Without a Play track, the gate alone can be exercised by temporarily returning a fake controller from the Android
actual whose `state` is `Required` or `ReadyToInstall` (not to be committed).

## Docs
- Root `CLAUDE.md`, *Updates*, new bullet: "Neither the blocking screen (nor the immediate flow started with it) nor
  the flexible update's Restart is put over an editor with unsaved text: the gate waits until the text has been saved
  or let go of (`hasUnsavedEditorChanges`). Restart waits for a sync run too; a required update does not — a run it
  cuts off is reported as interrupted the ordinary way." In the bullet about thresholds nothing changes.
- `presentation/CLAUDE.md`, the `ui/platform/AppUpdate.kt` / `ui/AppUpdateGate.kt` bullet: add "The controller
  reports and the gate decides when to act: the immediate flow is started by the gate as its screen goes up, and
  both that and the Restart offer wait for an editor holding unsaved text (Restart also for a sync run)."; and in
  the `CampfireViewModel` bullet's list of states that are acted on rather than drawn, add `isSyncing`.

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/AppUpdateGate.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/AppUpdate.kt`
- `presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/AppUpdate.android.kt`
- `CLAUDE.md`
- `presentation/CLAUDE.md`

## Depends on
**05** — the waiting trusts `hasUnsavedEditorChanges`, and only with 05 does the *Unsaved changes* dialog's Save keep
the draft (and so keep the gate waiting) until the write has actually succeeded; before it, the draft is dropped the
moment Save is pressed. Plan **50** (update state lost on rotation) edits `AppUpdate.android.kt` too and is
written against the code as it is today. The two are compatible in either order with one adaptation: the
`onAppUpdateInfoReceived` that plan 50 gives in full must end *without* its last statement
(`if (newState == AppUpdateState.Required && !hasStartedImmediateFlow) startUpdate()`) and the comment above it, since
that trigger lives in `startRequiredUpdateOnce()` now. Plan 50 lifting `hasStartedImmediateFlow` into saved state
works unchanged with this plan, and is what keeps the flow from reopening itself after a rotation.
