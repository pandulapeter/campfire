# 09 · Android: files picked after the Activity is recreated are silently dropped

**Severity:** high (an import or export that does nothing, with no message) · **Area:** `:presentation` (androidMain)

## Symptom

Songs → Import → the system document picker opens → rotate the phone (or the OS reclaims the backgrounded Activity)
→ pick files → back in Campfire nothing is imported and nothing is said. Export does the same: the "save as" location
is picked and no file is written.

## Cause

`rememberAndroidFilePicker` (`presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.android.kt:37–39`)
creates `AndroidFilePicker` with a plain `remember(context)`, and the suspended continuation
(`pickContinuation` / `saveContinuation`) lives in that instance. `rememberLauncherForActivityResult` re-registers the
launcher under a saveable key across recreation, so the result *is* delivered — to `picker::onFilesPicked` of the
**new** instance, whose continuation is null. `onFilesPicked` (:116) does nothing, and the `importFiles` coroutine in
`viewModelScope` waits forever. `_isImporting` is only set after `pickFiles()` returns, so nothing visible is stuck.

## Fix

Make the object that holds the continuation outlive the Activity, and re-attach only the launchers per composition.

1. Turn `AndroidFilePicker` into a Koin singleton in the same file:

   ```kotlin
   @Single
   internal class AndroidFilePicker(@Provided private val context: Context) : FilePicker { … }
   ```

   `context` is the `Application` (`CampfireAndroidApplication` passes `androidContext(this)`), which is what the
   class already uses (`applicationContext`). `:presentation` has the Koin compiler plugin and an empty
   `PresentationModule` with `@ComponentScan`, so the scan picks it up; `@Provided` is the documented way to mark the
   Android `Context` (see the root `CLAUDE.md`, Koin section).

2. `rememberAndroidFilePicker` becomes:

   ```kotlin
   val picker = koinInject<AndroidFilePicker>()   // org.koin.compose.koinInject
   picker.openLauncher = rememberLauncherForActivityResult(…, picker::onFilesPicked)
   picker.createTextLauncher = …
   picker.createArchiveLauncher = …
   return picker
   ```

   The launcher fields are overwritten on every composition, which is right: the old Activity's launchers are dead.

3. `pickFiles` / `saveFile` already resume with an empty result when no launcher is attached; keep that. Add a guard
   for a stale continuation: if `pickContinuation` is non-null and still active when a new `pickFiles` starts, resume
   the old one with an empty list first (two pickers cannot be open at once).

4. `docs`: `presentation/CLAUDE.md` and `app/android/CLAUDE.md` if either describes the picker's lifetime.

## Verification

Emulator with **Don't keep activities** on: Import → pick a file → the import runs. Export → pick a location → the
file is written. Rotate mid-picker in both cases.
