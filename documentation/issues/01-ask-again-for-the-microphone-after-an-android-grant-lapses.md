# Ask again for the microphone on Android after a grant lapses, rather than calling it refused for good

**Challenged:** amended — the Android flag must also stop hiding the never-asked notice (`tunerNoticeOf` hides
`NOT_ASKED` once `hasTurnedOnMicrophone` is set, which would leave a lapsed grant with no notice and no listening); a
refusal for good is judged from the rationale before and after the request, since a dialog dismissed by a tap outside
also comes back denied with no rationale; `wasRefused` goes; the docs to change are named.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** Android
**Files:** `presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/MicrophonePermission.android.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/MicrophonePermission.kt` (the
`expect`, its KDoc, and the new pure decision), the three other actuals (`MicrophonePermission.{desktop,ios,wasmJs}.kt`,
signature only), the two callers `ui/screens/tuner/TunerScreen.kt` and `ui/dialogs/instrument/TunerSheet.kt`,
`data/model/.../preferences/TunerSettings.kt` (KDoc only), `ui/tuner/CLAUDE.md`, `presentation/src/androidMain/CLAUDE.md`,
the root `CLAUDE.md`'s backup paragraph, and a new test in `presentation/src/commonTest/.../ui/platform/`

## Problem

`7b63224e1` (after 5.6.0) reads Android's "no rationale owed" as refused for good once this device has ever had an answer:

```kotlin
val rationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true
val isRefusedForGood = !isGranted && !rationale && (wasRefused || hasAsked)
...
canAskAgain = !isRefusedForGood,
```

`hasAsked` is `TunerSettings.hasTurnedOnMicrophone`, set by `onAnswered` after any answer, a grant included, and never
cleared; it is also in `preferences.json`, which the Android backup rules include, so a restore to a new phone carries
it. On Android 11+ a grant can go without any refusal, and then no rationale is owed and the system would still show its
question:

- "Only this time": the grant lapses soon after the app leaves the front.
- The system's auto-reset of an unused app's permissions.
- A restore to a new phone (the flag comes back, the permission does not).

Each then shows the REFUSED notice with only Open settings, no Ask again, and no ask on a tap-open
(`TunerAskOnOpenEffect` presses only a `NOT_ASKED` notice). 5.6.0 never set the flag on Android (only
`requestMicrophone`, the desktop's and the web's path, did), so it read these as NOT_ASKED and asked. Verified against
the code at `0feb3925f`.

## Fix

Remember a refusal for good, on Android only, in storage of its own; stop Android from reading or writing
`hasTurnedOnMicrophone`.

1. **Storage.** A `SharedPreferences` file of the app's own (e.g. `microphone_permission`), read in `remember(context)`
   on first composition (one tiny file; no StrictMode in the app). It is not backed up: both
   `app/android/src/main/res/xml/data_extraction_rules.xml` and `full_backup_content.xml` are allow-lists naming only
   `file` domain paths, so the `sharedpref` domain stays behind. Not a stored document of the stored-formats rule (no
   versioned JSON), so no fixture.
2. **Deciding a refusal for good** (the system's dialog can be dismissed by a tap outside on Android 11+, which comes
   back denied with no rationale although the question is still asked next time, so "denied with no rationale" alone is
   not enough):
   - Read `shouldShowRequestPermissionRationale` **in `request`, just before `launcher.launch`**, into a
     `rememberSaveable` (survives the Activity's recreation under the system dialog); read it again in the launcher
     callback (the result arrives once the Activity is started again, where the answer is current).
   - Granted: clear the stored state.
   - Denied with no rationale after, where one was owed before: refused for good (the second refusal; also the pre-11
     "Don't ask again" box, which shows only from the second ask).
   - Denied with no rationale before or after (dismissed, or the system answering silently because the state was lost):
     count it; a second such answer in a row is refused for good too, so a silent system costs one dead press, not one
     per launch.
   - Also clear the stored state whenever `LifecycleResumeEffect` reads `isGranted` true.
   - A result lost entirely (the composition gone, process death under the dialog) records nothing: the next Ask again
     gets an immediate answer, which then records. Accepted.
3. **Status.** `isRefusedForGood = !isGranted && !rationale && refusedForGood`; `status` and `canAskAgain` as now.
   Drop `wasRefused` (the stored state replaces it).
4. **The flag.** No actual reads `hasAsked` any more and only Android called `onAnswered`, so drop both parameters from
   the `expect` and every actual, and their KDoc. Keep `TunerController.markMicrophoneAsked` (inline it into
   `requestMicrophone` if nothing else calls it) and the field: `tunerNoticeOf` and `canListenWithoutTap` still read it
   for the desktop and the web (the root `CLAUDE.md`'s Tuner invariant). Never remove the stored field.
5. **The notice trap.** `tunerNoticeOf` hides `NOT_ASKED` while `hasRequested && listening !is Stopped` (the web's
   "prompt" while its own question is up). An Android device whose flag is already true (any build since `7b63224e1`, or
   a restore of one) would then show a lapsed grant as no notice at all, not listening (`canListenWithoutTap(NOT_ASKED)`
   is false), and `TunerAskOnOpenEffect` would not ask: a dead tuner. In both callers pass
   `hasRequested = hasTurnedOnMicrophone && permission.request == null` (the flag means something only where opening the
   input is what asks); iOS never sets it, so it changes nothing there.
6. **Docs.** `MicrophonePermission.android.kt`'s KDoc; `TunerSettings.hasTurnedOnMicrophone`'s KDoc (remove "Android
   sets it too…"); `ui/tuner/CLAUDE.md`'s Android bullet (lines 36–38: a refusal for good is now stored on the device by
   itself, never backed up, cleared by a grant); `presentation/src/androidMain/CLAUDE.md` (one bullet: the file, why it
   is not backed up); the root `CLAUDE.md`'s "Android/iOS system backup … never …" list gains the microphone answer.

Rejected alternatives: calling `onAnswered` only on a no-rationale denial (the flag is never cleared and is backed up, so
a lapse after a later grant or a restore still reads as refused); back to 5.6.0 (one dead Ask again per launch for a
real refusal for good, which `7b63224e1` removed).

## Tests

In `presentation/src/commonTest/.../ui/platform/` (new `MicrophonePermissionDecisionTest.kt`), over two `internal` pure
functions in commonMain beside the `expect`:

- `(rationaleBefore, rationaleAfter, granted, earlierSilentDenials) → stored state`: a grant clears; a denial after a
  rationale with none after is for good; a first silent denial is not, a second in a row is; a denial with a rationale
  after is not.
- `(isGranted, rationale, refusedForGood) → (status, canAskAgain)`: a lapsed grant (no rationale, nothing stored) is
  NOT_ASKED with Ask again; stored and no rationale is DENIED without; a rationale is DENIED with Ask again.

Add a `tunerNoticeOf` case only if its signature changes (it does not with step 5).

## Manual check

On an API 30+ emulator: Tuner → allow "Only this time", background the app until the grant lapses (or `adb shell pm
revoke`), return: the never-asked notice shows and a tap-open asks. Deny twice: Open settings only. Dismiss the dialog by
a tap outside once: Ask again still shows.
