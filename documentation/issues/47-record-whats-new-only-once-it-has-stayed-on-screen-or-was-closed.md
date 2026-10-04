# Record the What's new version only once the dialog has stayed on screen for a moment or was closed

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** Android (Play installs only)
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt, presentation/CLAUDE.md

**Challenged:** sound

## Problem

ea452ce38 moved the recording of an introduced version from where What's new is asked for to where it is composed,
so that the version is not recorded while the update required screen keeps dialogs from composing and its immediate
flow then ends the process. The reasoning is in `CampfireViewModel.showWhatsNewOnVersionChange`'s KDoc
(`CampfireViewModel.kt:2431-2441`): "Play's answer right after an update can still be the update it just installed,
which puts that screen up and starts its flow, which ends this process - a version recorded before anybody saw it would
never be introduced."

The fix only holds when Play has already answered by the time the dialog would open. It records on the first frame:

```kotlin
// Dialogs.kt:491-495
CampfireViewModel.DialogType.WhatsNew -> {
    // Here rather than where the dialog is asked for, since that can be long before it is on screen, or never
    // (see CampfireViewModel.showWhatsNewOnVersionChange).
    LaunchedEffect(Unit) { viewModel.onWhatsNewShown() }
    WhatsNewDialog(onDismiss = viewModel::dismissDialog)
}
```

and nothing waits for Play: `AppUpdateState` (`ui/platform/AppUpdate.kt:24-40`) starts at `NotAvailable` and has no
"not answered yet" value; the Android controller only asks on `ON_RESUME`
(`AppUpdate.android.kt:77`, `appUpdateManager.appUpdateInfo.addOnSuccessListener(...)`, an IPC round trip to the Play
Store app); `showWhatsNewOnVersionChange` waits only for `isAppOnScreen` and `canShowWhatsNew` (`WhatsNewGate.kt`, no
update input). `CampfireApp.kt:737` composes `CampfireDialogs` only while `!LocalIsCoveredByRequiredUpdate.current`,
which `AppUpdateGate` turns on once `state == AppUpdateState.Required`.

So when Play's answer arrives after the launch screen has gone (a slow Play Store process right after an update is
exactly when that happens), the sequence is: What's new composes, the version is recorded at once, Play answers
Required, the required screen covers the app and the dialog leaves the composition, the immediate flow starts and
ends the process. On the next start the version is already in `seenWhatsNewVersions`, so the release is never
introduced — the case ea452ce38 set out to fix. Whether Play's answer is late enough on a real device can only be
seen from a Play internal testing track.

## Fix

Options:

1. **Add a "not answered yet" `AppUpdateState`** that `canShowWhatsNew` waits for (with a timeout). Most precise, but
   it changes the `AppUpdateController` contract on all four platforms, the gate's `when`, and threads the update
   state into the view model, which today knows nothing of it. Too wide for the defect.
2. **Record on dismissal only.** Simple, but ea452ce38's KDoc deliberately records on appearance so that a process
   ended with the dialog up does not introduce it again.
3. **Recommended: record once the dialog has been composed for a short while, or once it is closed, whichever is
   first.** Covering the dialog takes it out of the composition, which cancels a delayed effect, so a dialog covered
   within the delay is not recorded and composes (and starts its delay) again if the required screen ever goes; a
   dialog closed quickly is recorded by the view model.

Concretely:

- `Dialogs.kt`: replace the effect with a delayed one, and a file-private constant next to `WHATS_NEW_HEADLINE`:

  ```kotlin
  CampfireViewModel.DialogType.WhatsNew -> {
      // Only once it has stayed up for a moment: Play's answer can arrive after the app is on screen and cover the
      // dialog with the update required screen, which takes it out of the composition and cancels this, and whose
      // flow ends the process (see CampfireViewModel.showWhatsNewOnVersionChange).
      LaunchedEffect(Unit) {
          delay(WHATS_NEW_SEEN_DELAY)
          viewModel.onWhatsNewShown()
      }
      WhatsNewDialog(onDismiss = viewModel::dismissDialog)
  }
  ```

  with `/** How long What's new has to stay uncovered before it counts as seen. */ private val WHATS_NEW_SEEN_DELAY =
  3.seconds` (`import kotlinx.coroutines.delay`, `import kotlin.time.Duration.Companion.seconds`). Three seconds is
  well past a Play answer's usual delay after resume and well under the time anyone reads the dialog for.

- `CampfireViewModel.showWhatsNewOnVersionChange`: record when the dialog goes away, which covers a close before the
  delay ran out (the Back gesture, the button, the scrim, or another dialog replacing it all leave `_visibleDialog`;
  being covered does not change it):

  ```kotlin
  if (_visibleDialog.compareAndSet(null, DialogType.WhatsNew)) {
      _visibleDialog.first { it != DialogType.WhatsNew }
      recordWhatsNewVersion()
  }
  ```

  `recordWhatsNewVersion` adds to a set, so recording twice is harmless. Update the KDoc at
  `CampfireViewModel.kt:2431-2441` ("are recorded by [onWhatsNewShown] once the dialog is on screen rather than here")
  to say: recorded once the dialog has stayed uncovered for a few seconds, or as it closes, so that a dialog the update
  required screen covers right away is not taken as seen; a process ended with the dialog up after that does not
  introduce it again. Update `onWhatsNewShown`'s one-line KDoc to "What [DialogType.WhatsNew] calls once it has stayed
  on screen for a moment".

- `presentation/CLAUDE.md:126` (the `ui/dialogs/Dialogs.kt` bullet): change "and the version is recorded once the
  dialog is composed (`onWhatsNewShown`) rather than when it is asked for, since on Android the update required screen
  keeps dialogs from composing and its flow can end the process first; ending the process with the dialog up does not
  repeat it" to say the version is recorded once the dialog has stayed composed for a few seconds (`onWhatsNewShown`)
  or as it closes, rather than when it is asked for or on its first frame, since on Android Play's answer can arrive
  after the dialog opened and the update required screen then covers it and its flow ends the process; ending the
  process with the dialog up after that does not repeat it.

Keep the change to these lines: `Dialogs.kt` and `CampfireViewModel.kt` are edited by other lanes.

## Tests

None: the timing lives in a composable effect and in a view model coroutine; `canShowWhatsNew` (the pure part) is
unchanged.

## Manual check

On any platform, after bumping `campfire.versionName` locally with a non-empty `whats_new_message`: open the app,
close What's new within a second, restart — it does not come back; open it again on another bump and leave it up for
five seconds, kill the process, restart — it does not come back.
On Android, from a Play internal testing track with an update published at priority 4-5: install the update, open the
app as soon as it is installed and confirm What's new appears once the required screen (if Play still shows it) is
gone, or on the next start if the flow ended the process.
