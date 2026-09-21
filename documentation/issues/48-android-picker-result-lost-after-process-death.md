# 48 · Android: a file picker result that comes back to a restarted process is dropped — an import does nothing, and an export leaves an empty file that looks like a backup

**Severity:** wrong behaviour / false sense of having a backup (android; unlikely on a flagship, routine on a low-memory phone or with "Don't keep activities" plus a background process limit) · **Area:** `:presentation` `androidMain` (`FilePicker.android.kt`: `AndroidFilePicker`, `CampfireAndroidApp.kt`), one line each in `CampfireViewModel` and `FilePicker.kt`

## Symptom
Export:
1. Settings → Library → Export library. The system's "save as" screen (DocumentsUI) opens.
2. While it is in front, Android kills Campfire's process (memory pressure; to reproduce:
   `adb shell am kill com.pandulapeter.campfire.debug`).
3. Confirm the location. Campfire is started again and shows the screen it was on. No message.
4. `campfire_library.zip` exists in Downloads with **0 bytes**. Nothing distinguishes this from a successful export,
   which shows no message either.

Import: the same with Import files — the files that were picked are ignored, silently.

A smaller hole in the same code, with no process death needed: when the document is created but writing it fails (the
provider throws, the volume is full), the empty or half-written document stays behind **and no message is shown**,
because `saveFile` answers `false` for it, which `CampfireViewModel.save` does not look at
(`CampfireViewModel.kt:1228-1229`: `export()?.let { … filePicker.saveFile(it) } ?: _messages.send(ExportFailed)` —
the `?:` only fires when there was nothing to export).

## Cause
`presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.android.kt`.

Commit `f19e27eb` made the picker survive an *Activity* recreation: `AndroidFilePicker` became a Koin `@Single`, the
coroutine waiting for the system picker is suspended on that singleton (`pickContinuation` / `saveContinuation`), and
every composition re-attaches its launchers — registered under a saved key, so the result is still delivered — to the
same object. A *process* death takes the singleton, the continuation, the view model's coroutine and the
`ExportedFile` it held. The launcher is registered again under its restored key and the pending result is delivered
to a picker nobody is waiting on:

```kotlin
// :129
fun onFilesPicked(uris: List<Uri>) {
    pickContinuation?.takeIf { it.isActive }?.resume(uris)      // null after process death: the URIs are dropped
    pickContinuation = null
}

fun onSaveLocationPicked(uri: Uri?) {
    saveContinuation?.takeIf { it.isActive }?.resume(uri)       // null: nothing is written,
    saveContinuation = null                                     // but CreateDocument has already created the document
}
```

and the write itself (`:93-99`) reports a failure as `false` and leaves the document where it is:

```kotlin
return withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { it.write(file.bytes) } != null
    } catch (exception: Exception) {
        println("Could not write \"${file.name}\": ${exception.message}")
        false
    }
}
```

That `withContext` is also cancellable: a scope cancelled between the dialog being confirmed and the write starting
leaves the same empty document.

## Fix
### What has to outlive the process, and where it is kept
- **The kind of operation** needs no saving: the launcher a result arrives at *is* the kind (`openLauncher` →
  import, the two `create…Launcher`s → export), and "nobody is waiting for it" is what marks it as orphaned.
- **An import needs nothing else.** The result carries the URIs and the read grant with it; they are read and handed
  to the ordinary import queue.
- **An export needs what was being exported.** It is kept as the exported **bytes in a file under `cacheDir`**, written
  just before the system picker is launched and removed when its result has been dealt with — not as a description
  of the export in `SavedStateHandle`. The reasons, so that nobody "simplifies" this the other way:
  the bytes are exactly what the user asked for, whatever a sync run does to the library in between; the whole
  mechanism stays inside `AndroidFilePicker`, where the failure mode lives, instead of teaching the common view model
  to persist and replay exports for the sake of one platform (it would also need a new `FilePicker` method to write
  to a location somebody else picked); and a library archive does not fit a saved-state `Bundle` anyway, so the
  alternative could only ever save names and export again. `cacheDir` is private, is never backed up (plan 25), and
  is where `shareFile` already puts its copies. The cost is one extra local write per export.

### Steps
1. **`FilePicker.android.kt`, `rememberAndroidFilePicker`**: change the return type from `FilePicker` to
   `AndroidFilePicker` (both `internal`), so the shell can reach what step 2 adds. Nothing else in it changes.

2. **`FilePicker.android.kt`, `AndroidFilePicker`**. New imports: `android.provider.DocumentsContract`,
   `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.NonCancellable`, `kotlinx.coroutines.SupervisorJob`,
   `kotlinx.coroutines.channels.Channel`, `kotlinx.coroutines.flow.receiveAsFlow`, `kotlinx.coroutines.launch`,
   `java.io.IOException`, `java.io.OutputStream`. Extend the class KDoc with a second paragraph:

   ```kotlin
    * A process that dies under the system picker takes this object with it, and the result then arrives at one that
    * nobody is suspended on. That is what an orphaned result is, and neither kind is dropped: picked files are read
    * and offered through [orphanedFiles], and the location of an export is filled from the copy [saveFile] left in
    * the cache directory before it opened the picker, the outcome going to [orphanedExportResults].
   ```

   Add next to the continuations:

   ```kotlin
   /**
    * For the results nobody is waiting for. Its own scope rather than the composition's: the read and the write
    * must not end with an Activity that happens to be recreated while they run.
    */
   private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
   private val _orphanedFiles = Channel<List<ImportedFile>>(Channel.BUFFERED)
   private val _orphanedExportResults = Channel<Boolean>(Channel.BUFFERED)

   /** Files picked for a process that did not live to read them, to be imported like any the system hands over. */
   val orphanedFiles = _orphanedFiles.receiveAsFlow()

   /** Whether an export whose process died under the "save as" screen was written after all. */
   val orphanedExportResults = _orphanedExportResults.receiveAsFlow()

   private val pendingExport get() = java.io.File(java.io.File(context.cacheDir, PENDING_EXPORT_DIRECTORY), PENDING_EXPORT_NAME)
   ```

   Replace `saveFile`:

   ```kotlin
   override suspend fun saveFile(file: ExportedFile): Boolean {
       val launcher = if (file.mimeType == ExportedFile.ZIP_MIME_TYPE) createArchiveLauncher else createTextLauncher
       saveContinuation?.takeIf { it.isActive }?.resume(null)
       withContext(Dispatchers.IO) { keepPendingExport(file) }
       val uri = suspendCancellableCoroutine<Uri?> { continuation ->
           saveContinuation = continuation
           continuation.invokeOnCancellation { saveContinuation = null }
           launcher?.launch(file.name) ?: continuation.resume(null)
       }
       // The document exists from the moment the dialog is confirmed, so from here on the write is owed: a scope
       // cancelled at this point would leave it empty.
       return withContext(NonCancellable + Dispatchers.IO) {
           clearPendingExport()
           when {
               uri == null -> false
               write(uri) { it.write(file.bytes) } -> true
               else -> throw IOException("Could not write \"${file.name}\".")
           }
       }
   }
   ```

   A coroutine cancelled *while the picker is up* leaves the pending copy in place on purpose: its
   `invokeOnCancellation` clears `saveContinuation`, so the result takes the orphaned path below and the file is
   still written.

   Replace the two callbacks:

   ```kotlin
   fun onFilesPicked(uris: List<Uri>) {
       val continuation = pickContinuation?.takeIf { it.isActive }
       pickContinuation = null
       when {
           continuation != null -> continuation.resume(uris)
           uris.isNotEmpty() -> scope.launch { _orphanedFiles.send(uris.mapNotNull { it.toImportedFile(context) }) }
       }
   }

   fun onSaveLocationPicked(uri: Uri?) {
       val continuation = saveContinuation?.takeIf { it.isActive }
       saveContinuation = null
       when {
           continuation != null -> continuation.resume(uri)
           uri == null -> scope.launch { clearPendingExport() }
           else -> scope.launch {
               // A copy that is no longer there fails the write like anything else, which removes the document.
               val isWritten = write(uri) { output -> pendingExport.inputStream().use { it.copyTo(output) } }
               clearPendingExport()
               _orphanedExportResults.send(isWritten)
           }
       }
   }
   ```

   Both are called on the main thread while the launchers are being registered (inside the first composition after
   the restart), which is why they only launch.

   Add the helpers and constants:

   ```kotlin
   /**
    * Fills the document the system picker created, and removes it again where that fails: it is created empty the
    * moment the dialog is confirmed, and an empty "campfire_library.zip" in Downloads reads as a backup.
    */
   private fun write(uri: Uri, content: (OutputStream) -> Unit): Boolean {
       val isWritten = try {
           context.contentResolver.openOutputStream(uri)?.use(content) != null
       } catch (exception: Exception) {
           println("Could not write \"$uri\": ${exception.message}")
           false
       }
       if (!isWritten) {
           try {
               DocumentsContract.deleteDocument(context.contentResolver, uri)
           } catch (exception: Exception) {
               // Not every provider lets a document be deleted. The message is all that is left to do then.
               println("Could not remove \"$uri\": ${exception.message}")
           }
       }
       return isWritten
   }

   /** A failure here costs only the safety net, never the export it is for. */
   private fun keepPendingExport(file: ExportedFile) {
       try {
           clearPendingExport()
           pendingExport.apply { parentFile?.mkdirs() }.writeBytes(file.bytes)
       } catch (exception: Exception) {
           println("Could not keep a copy of \"${file.name}\": ${exception.message}")
       }
   }

   private fun clearPendingExport() {
       pendingExport.parentFile?.deleteRecursively()
   }
   ```

   ```kotlin
   const val PENDING_EXPORT_DIRECTORY = "pending_export"
   const val PENDING_EXPORT_NAME = "export"
   ```

   Do **not** clear the pending copy when the picker object is created or the app starts: that is exactly the moment
   an orphaned result is about to arrive. A copy whose result never comes (the task was swiped away under the system
   picker) is replaced by the next export and lives in a directory the system may purge.

   While here, `shareFile` has the same silent `false`. Let a copy that cannot be prepared throw, so that it is
   reported like any failed export; the rest of the function stays as it is:

   ```kotlin
   val uri = withContext(Dispatchers.IO) {
       val directory = java.io.File(context.cacheDir, SHARED_DIRECTORY).apply { mkdirs() }
       val target = java.io.File(directory, file.name).apply { writeBytes(file.bytes) }
       FileProvider.getUriForFile(context, "${context.packageName}.files", target)
   }
   ```

3. **`presentation/src/commonMain/.../ui/platform/FilePicker.kt`**, the KDoc of `saveFile` — the contract the view
   model already relies on (the desktop picker's `writeBytes` throws today):

   ```kotlin
   /**
    * Offers the file to be saved: a "save as" dialog, a share sheet or a download. False when the user dismissed it
    * and nothing was saved. A file that was meant to be saved and could not be written is an exception, not a
    * false: that is the one the caller tells the user about.
    */
   ```

   (`IosFilePicker` still answers `false` when its temporary copy cannot be written. It is a different file with
   other plans queued on it; bring it in line when one of them is there.)

4. **`CampfireViewModel.kt`**, with the import and export functions:

   ```kotlin
   /** For the Android shell, whose picker can finish an export long after the coroutine that asked for it is gone. */
   fun onExportFailed() {
       _messages.trySend(Message.ExportFailed)
   }
   ```

   `save()` itself is not changed: a throwing `saveFile` already ends in `Message.ExportFailed`.

5. **`presentation/src/androidMain/.../ui/CampfireAndroidApp.kt`**:

   ```kotlin
   val filePicker = rememberAndroidFilePicker()
   LaunchedEffect(filePicker, viewModel) {
       filePicker.orphanedExportResults.collect { isWritten -> if (!isWritten) viewModel.onExportFailed() }
   }
   val allFilesToImport = remember(filesToImport, filePicker) { merge(filesToImport, filePicker.orphanedFiles) }
   CompositionLocalProvider(
       LocalFilePicker provides filePicker,
       LocalSyncNotifier provides syncNotifier,
   ) {
       CampfireApp(
           viewModel = viewModel,
           urlOpener = { urlOpener(it, isDarkTheme) },
           filesToImport = allFilesToImport,
           onAppReady = onAppReady,
       )
   }
   ```

   Imports: `androidx.compose.runtime.remember`, `kotlinx.coroutines.flow.merge` (`LaunchedEffect` is already
   imported). Extend the `@param filesToImport` line of the KDoc: "…read by the activity that received the intent;
   the files of a pick that outlived its process join them here". `rememberAndroidFilePicker()` must stay outside the
   `CompositionLocalProvider(...)` argument list now that its result is used twice, and must keep being called
   unconditionally and in the same position on every composition — the launchers' saved keys depend on it.

   A successful orphaned export stays silent, like every successful export: with this plan a failure is always
   announced, so silence means success again. No new string is needed.

## Tests
None (UI is untested; `:presentation` has no Android tests).

## Verify
Debug build on a device or emulator (`./gradlew :app:android:assembleDebug`, package `com.pandulapeter.campfire.debug`).
1. **Orphaned export.** Settings → Export library; with the "save as" screen up run
   `adb shell am kill com.pandulapeter.campfire.debug` (check with `adb shell pidof com.pandulapeter.campfire.debug`
   that the process is gone), then confirm. Campfire restarts on the Settings screen, no message, and the zip in
   Downloads is a valid archive of the right size. Repeat with a single song (the text launcher).
2. **Orphaned export that cannot be written.** As 1, but before confirming run
   `adb shell run-as com.pandulapeter.campfire.debug rm -r cache/pending_export`. After confirming: "Export failed",
   and no `campfire_library.zip` is left in Downloads.
3. **Orphaned import.** Import files; kill the process under the picker; select two `.cho` files. Campfire restarts
   and the snackbar reports two imported songs.
4. **Cancelled after the kill.** As 1, but press Back in the system picker: no file, no message, and
   `cache/pending_export` is gone (`run-as … ls cache`).
5. **Rotation still works** (the `f19e27eb` case): rotate under both pickers, confirm; one import / one complete file.
6. **Ordinary failure.** Export to a USB-OTG or SD location and pull it before confirming, or temporarily make
   `write` throw: "Export failed", no empty document left.
7. `run-as … ls cache` after an ordinary export: no `pending_export`.

Compile: `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution :app:desktop:compileKotlin`
(the common `CampfireViewModel` and `FilePicker.kt` change).

## Docs
`presentation/CLAUDE.md`, the `ui/platform/FilePicker.kt` bullet (`:44`): append
"`saveFile` answers false for a dismissed dialog and throws for a file that could not be written, which is what the
view model reports. The Android one is a singleton the launchers of every composition are attached to, so a result
survives the Activity being recreated under the system picker; one that arrives after the *process* was recreated
finds nobody waiting and is still acted on — picked files join `filesToImport` in `CampfireAndroidApp`, and an export
is filled from the copy `saveFile` leaves in `cacheDir/pending_export` while the picker is up. A document that cannot
be filled is deleted again (`DocumentsContract.deleteDocument`), since `CreateDocument` creates it empty."

## Touches
- `presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.android.kt`
- `presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireAndroidApp.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. Plan 15 (size caps) changes `Uri.toImportedFile` in the same file, which this plan only calls; schedule them
one after the other, in either order.
