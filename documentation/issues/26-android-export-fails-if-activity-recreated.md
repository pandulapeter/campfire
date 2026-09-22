# 26 · Android: rotating the phone (or any configuration change) while an export is being prepared makes it fail with "export failed", and the file picker keeps a finished Activity alive

**Severity:** wrong behaviour (Android; unlikely — the Activity has to be recreated in the short stretch between the
export being built and the "save as" screen opening, which for a large library export is the time it takes to write
the zip's safety copy; the user sees "export failed" and has to start again) plus a memory leak (one finished
Activity and its composition, held until the next Activity composes or the process ends) · **Area:** `:presentation`
androidMain (`ui/platform/FilePicker.android.kt`)

## Symptom
1. A library of a few hundred songs. Settings → export the library.
2. Rotate the phone (or switch the system to dark mode, change the font size, resize in split screen) at once.

Sometimes the "save as" screen never opens and the snackbar says the export failed; exporting again works. Separately:
leave the app with Back while a sync run keeps the process alive — the destroyed `CampfireActivity` stays reachable
from the `AndroidFilePicker` singleton.

## Cause
`presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.android.kt`

`rememberAndroidFilePicker` (`:51-66`) writes the composition's launchers into the Koin `@Single` on every
composition, and nothing ever takes them out. `rememberLauncherForActivityResult` unregisters its launcher when the
composition that made it is disposed (activity-compose 1.13.0, `ActivityResultRegistry.kt:102-106`), and launching
an unregistered launcher throws (`activity` 1.13.0, `ActivityResultRegistry.kt:122-126`,
`checkNotNull(keyToRc[key]) { "Attempting to launch an unregistered ActivityResultLauncher …" }`).

`saveFile` (`:113-131`) picks its launcher **before** it suspends:

```kotlin
val launcher = if (file.mimeType == ExportedFile.ZIP_MIME_TYPE) createArchiveLauncher else createTextLauncher
saveContinuation?.takeIf { it.isActive }?.resume(null)
withContext(Dispatchers.IO) { keepPendingExport(file) }
val uri = suspendCancellableCoroutine<Uri?> { continuation ->
    …
    launcher?.launch(file.name) ?: continuation.resume(null)
```

The manifest declares no `configChanges`, so a rotation destroys the Activity and disposes its composition. One that
lands while `keepPendingExport` writes the copy (up to the 24 MB of an archive), or in the gap between the old
composition leaving and the new one arriving at the moment `saveFile` starts, leaves `saveFile` holding a dead
launcher; `launch` throws `IllegalStateException`, which `CampfireViewModel.save` (`CampfireViewModel.kt:1337-1345`)
turns into `Message.ExportFailed`. `pickFiles` reads its launcher inside the suspension, at the tap, so it is not
affected in practice.

The same fields are also what keeps a finished Activity reachable: each launcher belongs to that Activity's
`ActivityResultRegistry`, and the singleton outlives it.

## Fix
Keep the launchers as one value that is present only while a composition holds them, and have both calls wait for a
present one at the moment they launch.

1. In `AndroidFilePicker`, replace the three `var …Launcher` fields with:

   ```kotlin
   /** The launchers of the composition that is on screen; null between one Activity and the next. */
   class Launchers(
       val open: ActivityResultLauncher<Array<String>>,
       val createText: ActivityResultLauncher<String>,
       val createArchive: ActivityResultLauncher<String>,
   )

   val launchers = MutableStateFlow<Launchers?>(null)
   ```

2. `rememberAndroidFilePicker` remembers the three launchers into locals as today and attaches them in an effect
   declared after them, so it is disposed before they unregister:

   ```kotlin
   DisposableEffect(picker, open, createText, createArchive) {
       val launchers = AndroidFilePicker.Launchers(open, createText, createArchive)
       picker.launchers.value = launchers
       // Only its own: the next Activity's composition may already have put its launchers here.
       onDispose { picker.launchers.compareAndSet(launchers, null) }
   }
   ```

3. `pickFiles` and `saveFile` take the launcher after every suspension, right before the
   `suspendCancellableCoroutine`, waiting for one if none is there:

   ```kotlin
   // Taken now rather than when the call began: an Activity recreated in between has unregistered the old ones,
   // and between two Activities there are none until the next one has composed.
   val launcher = launchers.filterNotNull().first().let { if (file.mimeType == ExportedFile.ZIP_MIME_TYPE) it.createArchive else it.createText }
   ```

   (`it.open` in `pickFiles`), and inside the coroutine block `launcher.launch(file.name)` without the `?:` branch.
   The wait is on state, not on time: it ends when the next Activity composes, or with `viewModelScope` when there
   will not be one. Move the `saveContinuation?.takeIf { it.isActive }?.resume(null)` line down next to it, so the
   "one still waiting" check is made where the new one begins.

The result callbacks stay bound to the singleton (`picker::onFilesPicked`, `picker::onSaveLocationPicked`), which
is what the class KDoc explains; update that KDoc's last sentence of the first paragraph to say that the launchers
are attached for as long as a composition holds them and taken at the moment of launching.

Do **not** add `configChanges` to the manifest to avoid the recreation (it changes every resource reload), and do not
retry a failed `launch` on a timer.

## Tests
None (UI is untested).

## Verify
1. `./gradlew :app:android:assembleDebug`, install. Import a large library (a few hundred songs), start the library export and rotate the emulator immediately
   (`adb shell settings put system user_rotation 1` with auto-rotate off, right after the tap; repeat a few times).
   Before: some attempts end in "export failed". After: the "save as" screen opens every time, and the file is
   written.
2. Import from Settings, rotate while the system picker is open, pick a file: imported (unchanged behaviour).
3. With "Don't keep activities" on: start an export, press Home as the save screen opens, come back, save — written.
4. Android Studio's memory profiler (or LeakCanary in a local debug build): back out of the app while a sync run
   keeps the process alive — no retained `CampfireActivity`.

## Docs
`presentation/CLAUDE.md`, the `ui/platform/FilePicker.kt` bullet (Android part): add "The launchers are attached to
the singleton only while a composition holds them, and taken at the moment of launching rather than when a pick or
an export began: an Activity recreated in between has unregistered the old ones."

## Touches
- `presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.android.kt`
- `presentation/CLAUDE.md`

## Depends on
23 edits `toImportedFiles` in the same file; any order, one after the other. 10 edits the view model's import and
export entry points and `IosFilePicker.kt`, not this file.
