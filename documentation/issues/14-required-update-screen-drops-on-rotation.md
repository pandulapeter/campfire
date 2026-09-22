# 14 · The required-update screen drops away on every rotation until Play answers again

**Severity:** wrong behaviour (Android, Play-installed builds only, and only while a priority 4-5 release is live -
or a 2-3 one for the dialogs. Every rotation, fold or theme change uncovers the app for as long as the Play IPC takes,
typically a few hundred milliseconds, and the "Update available" / "Restart" dialogs blink out and back the same way)
· **Area:** `:presentation` (`androidMain/.../ui/platform/AppUpdate.android.kt`)

## Symptom
1. With a required (priority 4-5) update live, the blocking "Update required" screen is up.
2. Rotate the phone. The screen fades out, the app underneath is visible and takes touches and back gestures, and
   a moment later the screen fades back in.
3. With an optional (2-3) update, the "Update available" dialog disappears and reappears on every rotation; the same
   for the "Restart" dialog of a downloaded update.

## Cause
The controller is created per activity (`presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/AppUpdate.android.kt:57-63`,
`remember(activity) { AndroidAppUpdateController(...) }`), and a new one starts from nothing (`:99-100`):

```kotlin
override var state by mutableStateOf(AppUpdateState.NotAvailable)
    private set
```

It only learns the state again when the `ON_RESUME` check (`:70`) is answered. The gate saves its own latch across
the recreation, but visibility still needs the live state
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/AppUpdateGate.kt:81-85`):

```kotlin
var isRequiredUpdateInTheWay by rememberSaveable { mutableStateOf(false) }
...
val isRequiredScreenVisible = isRequiredUpdateInTheWay && state == AppUpdateState.Required
```

Only `isPostponed` and `hasStartedImmediateFlow` are carried across (`:54-56`); the comment at `AppUpdateGate.kt:80`
("a rotation must not uncover the app") is not held.

## Fix
Carry the last known state across the recreation the way `isPostponed` is carried, and believe it only until Play
has been asked.

`AppUpdate.android.kt`:

1. In `rememberAppUpdateController`, extend the comment at `:51-53` and add a third saved state:

   ```kotlin
       // Kept outside the controller so that a rotation does not undo them: the activity, and with it everything
       // remembered against it, is recreated, while a "later" the user already gave should still stand, an
       // immediate flow they already backed out of should not open itself again, and what Play last said should be
       // on screen from the first frame rather than a few hundred milliseconds later - long enough for a rotation to
       // uncover an app the blocking screen was keeping the user out of.
       val isPostponed = rememberSaveable { mutableStateOf(false) }
       val hasStartedImmediateFlow = rememberSaveable { mutableStateOf(false) }
       val lastKnownState = rememberSaveable { mutableStateOf(AppUpdateState.NotAvailable) }
   ```

   and pass `lastKnownState = lastKnownState` to the constructor (`:57-63`, trailing comma kept). An enum is
   `Serializable`, so the default autosaver takes it on Android.

2. `AndroidAppUpdateController`: add the constructor parameter `lastKnownState: MutableState<AppUpdateState>,` after
   `hasStartedImmediateFlow`, and replace `:99-100` with:

   ```kotlin
       /**
        * Starts from what the controller of the previous activity knew, which Play corrects with the first answer.
        * A download is the exception: it draws nothing, and the first answer is what registers a listener for its end.
        */
       override var state by lastKnownState
           private set

       init {
           if (state == AppUpdateState.Downloading) state = AppUpdateState.NotAvailable
       }
   ```

   Place the `init` block after the property declarations (Kotlin initializes in order; `state` must be delegated
   first). Mapping `Downloading` away matters because `onAppUpdateInfoReceived` keeps `Downloading` over an
   `Optional` answer (`:196`); carried over, a download that was cancelled while the activity was recreated would
   hold the state at `Downloading` for the rest of the session instead of offering the update again.

3. Make a carried-over state that Play cannot confirm go away, so it can never lock the app. In `checkForUpdate`
   (`:107-113`) replace the failure listener:

   ```kotlin
               // A failed check is the normal answer for a build Play did not install, and there is nothing the user
               // could do about a real failure either: the app simply stays the version it is. A state carried over
               // from the previous activity is only believed until Play has been asked, though, and one it has not
               // confirmed is not left standing - above all not a blocking screen whose button needs Play's answer.
               .addOnFailureListener { if (!isReleased && availableUpdate == null) state = AppUpdateState.NotAvailable }
   ```

   `availableUpdate` is null exactly until this controller has had an answer, so an answer already had (and a
   transient failure after it) keeps today's behaviour.

Why nothing else changes:
- The gate's `LaunchedEffect(isRequiredScreenVisible)` runs `startRequiredUpdateOnce()` again in the new composition;
  `hasStartedImmediateFlow` is saved, so it does not reopen the Play flow. Where it had not been started (the gate
  was waiting for unsaved text) `startUpdate` returns early while `availableUpdate` is null, and the screen's button
  works as soon as the `ON_RESUME` answer arrives.
- `onUpdateFlowResult` (`:115-124`) is now answered by a controller that is `Required` rather than `NotAvailable`
  after a recreation during an immediate flow, which is what its `state != Required` test always meant (a cancelled
  immediate flow is no longer turned into a postponement).
- `installUpdate` (`completeUpdate`) needs no `AppUpdateInfo`, so a carried-over `ReadyToInstall` dialog works at once.
- The state is saved through process death too. After an update that installed, the process is the new version and
  the first answer is `NotAvailable`; with no Play at all, step 3 drops it.

**Requires 02 part 2.** With the state no longer reset, a rotation of an editor holding unsaved text would otherwise
latch the gate: the old editor's dispose clears the draft (`onEditorClosed`), the new composition sees
`state == Required && !hasUnsavedEditorChanges` for a frame, `isRequiredUpdateInTheWay` (`AppUpdateGate.kt:82-84`)
becomes true and the immediate flow starts over the draft.

## Tests
None (UI).

## Verify
Only a Play-installed build answers (internal testing track), see the Updates section of the root `CLAUDE.md`.
1. Publish to the internal track a build with priority 5, install the previous one from the track. The blocking
   screen comes up; back out of the Play flow. Rotate several times: the screen stays up on every frame; touches and
   back never reach the app.
2. With the editor holding unsaved text and a required update available (the gate waiting), rotate: the blocking
   screen does not come up and no Play flow starts. Save: the screen comes up.
3. Priority 3: the "Update available" dialog stays up through a rotation (a new dialog window, no fade-out/in gap
   longer than the recreation itself). "Later", rotate: stays dismissed.
4. Sideloaded debug APK (no Play answer): nothing ever shows, before or after a rotation.

Compile: `:app:android:assembleDebug` (the change is `androidMain` only); `:presentation:compileKotlinDesktop` to be
sure common code is untouched.

## Docs
`presentation/CLAUDE.md`, the `ui/platform/AppUpdate.kt` / `ui/AppUpdateGate.kt` bullet, after "…wait for an editor
holding unsaved text (Restart also for a sync run)." add: "What Play last said is saved with the activity, so a
rotation keeps the blocking screen and the dialogs up rather than uncovering the app until Play answers again; a
carried-over state that the first check cannot confirm is dropped."

## Touches
- `presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/AppUpdate.android.kt`
- `presentation/CLAUDE.md`

## Depends on
02 (part 2, `onEditorClosed` leaving the draft alone while the editor is on the stack).
