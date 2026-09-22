# 10 · Double-tapping "Export library" or "Import files" builds two exports and opens two pickers; on Android the second one reports "Export failed" and deletes the file it just created

**Severity:** wrong behaviour (Android: spurious failure message and a deleted document. Desktop: two nested file dialogs. iOS: the second picker is refused by UIKit and its coroutine never resumes. Likely: the settings rows and the empty-state buttons stay enabled while an export is being built, and building a large library's archive takes seconds) · **Area:** `:presentation` (`CampfireViewModel.kt`: `importFiles(filePicker)`, `exportSong`, `shareSong`, `exportSetlist`, `exportLibrary`)

## Symptom
Android, a library of a few thousand songs:
1. Settings → Library → tap "Export all" twice (or three times) while nothing seems to happen yet. The archive is
   being built and the row gives no sign of it.
2. A system "save as" screen opens. When it is confirmed, a second one is underneath it.
3. Confirm the second one too. A snackbar says "Export failed", and the file it named does not exist (the app
   deleted it). Cancel it instead and nothing is said, but the app zipped the whole library twice for nothing.

The same with "Import files" (the empty song list's button, the Settings row): two pickers open. Files picked in the
one underneath are imported as if another app had handed them over, and a single song picked there is opened.

Desktop: the second click opens a second modal file dialog nested inside the first. iOS: the second tap tries to
present a document picker over the one on screen, UIKit refuses ("Attempt to present … which is already
presenting"), and that request's coroutine waits forever.

## Cause
None of the picker intents is single-flight. `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:1125-1137`:

```kotlin
fun importFiles(filePicker: FilePicker) = viewModelScope.launch {
    if (_isImporting.value) return@launch
    val files = try {
        filePicker.pickFiles()
```

`_isImporting` is only set once the picked files are being planned (`import()`, `:1248`), so it is false for the whole
time the picker is up. And `:1310-1345`:

```kotlin
fun exportLibrary(filePicker: FilePicker) = viewModelScope.launch {
    save(filePicker) { exportLibrary.invoke() }
}
...
private suspend fun save(filePicker: FilePicker, isShare: Boolean = false, export: suspend () -> ExportedFile?) = try {
    export()?.let { if (isShare) filePicker.shareFile(it) else filePicker.saveFile(it) } ?: _messages.send(Message.ExportFailed)
```

Every tap builds its own archive (`exportLibrary.invoke()` reads and zips the whole library) and then asks the picker.
The row stays enabled throughout (`SettingsScreen.kt:462-468`: `isEnabled = !isPerformanceModeEnabled`).

What the Android picker then does with two requests
(`presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.android.kt:118-135`,
`:161-180`):
- Call 2 resumes call 1's continuation with `null` (`saveContinuation?.takeIf { it.isActive }?.resume(null)`). Call 1
  goes on to `clearPendingExport()` while call 2 is writing its own pending copy. The two race on the same
  `cacheDir/pending_export` file.
- Call 2 launches a second `CreateDocument` activity over the first one, which is still open. Whichever the user
  confirms first goes to call 2's continuation. The other one finds no continuation, takes the "orphaned" path, and
  fills the document from `pendingExport`. Call 2's resumption has already cleared that copy, so the write fails, the
  document is deleted (`DocumentsContract.deleteDocument`), and `orphanedExportResults` sends `false`, which
  `CampfireAndroidApp` turns into `Message.ExportFailed`.

`pickFiles` (`:107-116`) is the same for imports: the second picker's answer lands on the orphan path
(`onFilesPicked` → `_orphanedFiles`), which `CampfireAndroidApp` merges into `filesToImport`. It is then imported with
`shouldOpenSong = true`.

On iOS, `IosFilePicker.present` (`app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt:97-106`)
keeps a single `delegate` field and presents from the same controller. A second presentation while one is up is
refused by UIKit, and its continuation is never resumed. The first picker's delegate has been replaced in that field,
and `controller.delegate` is a weak reference, so the first picker's answer can be lost as well.

## Fix
One file transfer at a time, decided in the view model where all five intents meet. A request while one is running is
ignored, not queued: the user already has a picker coming, and queuing a second one would just be a delayed version
of the same double dialog.

1. `CampfireViewModel.kt`, in the "Import and export" section:

   ```kotlin
   /**
    * The pick, export or share that is running, from the tap until its picker has answered. A second one is ignored
    * rather than queued: a double tap on a row is one request, the platforms cannot show two pickers at once (Android
    * stacks them and routes the second answer to nobody, UIKit refuses to present over its own, the desktop nests two
    * modal dialogs), and an export builds its whole archive before its picker shows, which is seconds in which the
    * row looks as if it had not been tapped.
    */
   private var fileTransferJob: Job? = null

   private fun launchFileTransfer(block: suspend () -> Unit) {
       if (fileTransferJob?.isActive == true) return
       fileTransferJob = viewModelScope.launch { block() }
   }
   ```

2. Route the five intents through it. They currently return the `Job` of `viewModelScope.launch`. Check the call
   sites (`SongsScreen.kt:303`, `SetlistsScreen.kt:283`, `SettingsScreen.kt:448` / `:467`, `NewItemMenu.kt:74`,
   `SongActions.kt:164` / `:174`, `SetlistActions.kt:104`). None of them uses the returned job, so they can become
   `Unit`-returning:

   ```kotlin
   fun importFiles(filePicker: FilePicker) = launchFileTransfer {
       if (_isImporting.value) return@launchFileTransfer
       ...
   }

   fun exportSong(filePicker: FilePicker, songFileName: String) = launchFileTransfer { save(filePicker) { exportSongs(listOf(songFileName)) } }
   ```

   and the same for `shareSong`, `exportSetlist` and `exportLibrary`. `importFiles(files: List<ImportedFile>)` (files
   the system handed over) is **not** a file transfer in this sense and stays as it is.

3. The guard is only safe if every platform picker always resumes its continuation, since a request that never ends
   would block every later import and export until the app restarts. Check each one while landing this:
   - Android: every launch gets a result (the singleton receives it even after a recreation). A `launch` that throws
     (no activity can handle `OPEN_DOCUMENT`) throws out of `suspendCancellableCoroutine`, and `importFiles` / `save`
     already catch that. Keep the "resume a stale continuation" lines in `AndroidFilePicker`; they are harmless.
   - Desktop: `FileDialog.isVisible = true` returns when the dialog closes.
   - Web: `pickFiles` resolves on `change`, on `cancel`, or 1.5 s after focus comes back. `saveFile` downloads
     without a dialog.
   - iOS: `documentPickerWasCancelled` covers Cancel. **Add** `presentationControllerDidDismiss` handling for a sheet
     swiped away: set the picker's `presentationController?.delegate` to the `DocumentPickerDelegate`, which
     implements `UIAdaptivePresentationControllerDelegateProtocol` as well and answers `onFinished(emptyList())`
     there. And make `present` (`IosFilePicker.kt:97-106`) refuse to present over something, and never drop a
     delegate that is still waiting (it is the only strong reference to it; UIKit holds delegates weakly):

     ```kotlin
     private fun present(controller: UIDocumentPickerViewController, onFinished: (List<NSURL>) -> Unit) {
         val host = viewController()
         // UIKit refuses a second presentation with nothing but a log line, and the answer it would have given
         // never comes: the caller is told nothing was picked instead of waiting for good.
         if (host.presentedViewController != null) {
             onFinished(emptyList())
             return
         }
         // Nothing is on screen, so a delegate still held is one whose picker went away without telling it: its
         // caller is answered now rather than never.
         (delegate as? DocumentPickerDelegate)?.finish(emptyList())
         …existing body, presenting from `host`…
     }
     ```

     with `DocumentPickerDelegate` gaining a `fun finish(urls: List<NSURL>)` that calls `onFinished` at most once (a
     `private var isFinished` checked and set there; `didPickDocumentsAtURLs`, `documentPickerWasCancelled` and
     `presentationControllerDidDismiss` all go through it), so a late callback of a stale picker cannot resume its
     continuation a second time. Key the refusal on `presentedViewController`, not on `delegate != null`: a delegate
     that is never called back would otherwise refuse every later pick.
   - Why Android needs the guard most: the comment in `AndroidFilePicker.pickFiles` ("Two system pickers cannot be
     open at once, so one still waiting is one whose answer is never coming") is wrong — two `ACTION_OPEN_DOCUMENT` /
     `CreateDocument` activities *can* stack. Both launches use the one registry key of their launcher, and
     `ActivityResultRegistry` (activity 1.13.0) keeps `launchedKeys` as a `MutableList`, adding the key once per
     `launch` and removing one occurrence per delivered result, so **both** results are delivered to the callback
     straight away: the first to the waiting continuation, the second to the orphan path described under Cause.
     Correct that comment while here: with the view model's guard a second launch no longer happens, and the
     stale-continuation line stays as a last resort.
   - Web, for the record: a browser that refused `input.click()` for want of a user activation would leave
     `pickFiles` unsettled for good. That does not happen today — the pick starts within milliseconds of the click,
     well inside every browser's activation window — but it is why nothing may be put between the tap and
     `filePicker.pickFiles()` that suspends (a read, a delay) once the guard is in.

4. Do **not**:
   - use a time window ("refuse a second picker within a second") instead of the job: the brief rules out
     time-based guards where state can decide, and every platform picker answers once the iOS half above is in.
   - disable the rows with a flag the UI collects (`isExporting`). That is a second source of truth for the same job,
     and a disabled row that re-enables itself is an animation narrating nothing. Ignoring the second request is the
     whole of the behaviour wanted.
   - cancel the running transfer and start the new one. On Android a cancelled `saveFile` leaves its activity open,
     and its answer would go down the orphan path, which is the very bug.

## Tests
None (UI is untested).

## Verify
1. Android (`.debug` build) with a library of a few hundred songs (import a large zip): Settings → tap "Export all"
   three times fast. One save screen opens. Save: no "Export failed", the archive is there. Tap "Export all" again
   once it is done: it opens again (the guard is released).
2. Empty library (fresh install, delete the demo songs): double-tap "Import files" on the empty state. One picker.
   Cancel it and tap again: it opens.
3. Song menu → Share, twice quickly (before the menu is gone, or from two rows with two fingers): one chooser.
4. Desktop: double-click Settings → "Export all": one dialog.
5. iOS simulator: tap "Import files", swipe the picker sheet down, then tap "Import files" again: it opens (proves the
   dismissal resumes). Double-tap: one picker.
6. Web: double-click "Import files": one browser file dialog, and after cancelling it the next click works (within
   1.5 s of the window regaining focus the previous request is still settling; the click after that works).

## Docs
`presentation/CLAUDE.md`, the `ui/platform/FilePicker.kt` bullet, at the end: "The view model runs one pick, export or
share at a time (`CampfireViewModel.launchFileTransfer`) and ignores another asked for meanwhile, which is why every
picker has to answer on every way its screen can go away, a sheet swiped off on iOS included: one that never answered
would keep the app from importing or exporting anything again."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFilePicker.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 33 and 34 also edit `CampfireViewModel.kt` (34 every `_messages` line, `save` and `importFiles` included;
land 34 after this or rebase it). 11 is complementary and touches only the menus: it makes a menu entry fire once
per opening, which this guard cannot do for the transfers that end at once (Android's share, the web's download);
this one covers the rows and buttons that are not in a menu and the seconds an archive takes to build. Land both.
