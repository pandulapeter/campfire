# 05 · "Save" in the unsaved-changes dialog closes the editor before the write has succeeded

**Severity:** data loss (all platforms; needs a failing write — a full or read-only disk, a file locked by another
program on Windows, OPFS quota or a missing `createWritable` on the web — and then loses everything typed since the
last save; on desktop the app exits on top of it) · **Area:** `:presentation` — `CampfireViewModel`
(`saveEditorChangesAndLeave`, `saveSongContent`, `requestExit`), `ui/dialogs/Dialogs.kt`

## Symptom
1. Open a song in the editor and type.
2. Press Back / Close / Escape, or close the desktop window (or Cmd+Q). The **Unsaved changes** dialog appears.
3. Answer **Save**. The write fails.
4. The editor is already gone: the user is looking at the song list with a "Could not save the song" snackbar, and
   there is nothing left to retry from — the editor's entry was popped, so the field's state went with it, and the
   view model's draft was cleared. When the dialog was raised by closing the desktop window the application exits
   as well, and not even the snackbar is seen.

The same write failing behind the editor's own Save button loses nothing: the editor stays, the text stays, the
snackbar says so. The dialog's Save has to behave the same way.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:889-895`:

```kotlin
fun saveEditorChangesAndLeave() {
    // Taken before leaving, which dismisses the dialog and with it the exit the dialog was asked for.
    val exit = pendingExit
    _editorDraft.value?.let { saveSongContent(fileName = it.fileName, text = it.text) }
    leaveEditor()
    exit?.let(::exitOnceSaved)
}
```

`saveSongContent` (`:925-939`) only *launches* the write, and turns a failure into `Message.SaveFailed` inside that
coroutine, so it completes normally either way. `leaveEditor()` (`:914-919`) runs at once: draft nulled, dialog
dismissed, editor popped. `exitOnceSaved` (`:773-776`) then joins the job and calls `exit()` whatever became of it:

```kotlin
private fun exitOnceSaved(exit: () -> Unit) = viewModelScope.launch {
    currentSaveJob?.join()
    exit()
}
```

The same `join()`-then-exit is what `requestExit` does when the text *looks* saved or a save is still running, so a
save started with the Save button and failing while the window is being closed exits over the failure too.

## Fix
Leaving becomes what a successful write earns, in one coroutine; nothing about how the write itself is done changes
(`songWriteMutex`, `NonCancellable`, `writeSongContent`, `Message.SaveFailed` are all reused).

All of steps 1–4 are in `CampfireViewModel.kt`.

1. **Split the write out of `saveSongContent`** so that a caller can wait for its outcome. Replace `:921-939` with:

   ```kotlin
   /** The editor's Save action. Fire and forget: the outcome reaches the user as the editor's own state. */
   fun saveSongContent(fileName: String, text: String) = viewModelScope.launch {
       writeEditorText(fileName = fileName, text = text)
   }.also { currentSaveJob = it }

   /**
    * Writes the edited text, keeps the copy the viewer renders from in step, and answers whether the file now holds
    * it. A failure is reported from here as [Message.SaveFailed], so that every way of saving says the same thing.
    * Runs on [NonCancellable] because the last save of an editing session can be started as the screen is going
    * away, which cancels its scope.
    */
   private suspend fun writeEditorText(fileName: String, text: String) = try {
       _isSavingSong.update { true }
       withContext(NonCancellable) {
           songWriteMutex.withLock { writeSongContent(fileName = fileName, text = text) }
       }
   } catch (exception: CancellationException) {
       throw exception
   } catch (exception: Exception) {
       println("Could not save the song \"$fileName\": ${exception.message}")
       _messages.send(Message.SaveFailed)
       false
   } finally {
       _isSavingSong.update { false }
   }
   ```
   (`writeSongContent` already returns the use case's `Boolean`, which is always `true` without an `expectedText`.)

2. **`saveEditorChangesAndLeave`** (`:888-895`) waits for that answer. Add the job next to `currentSaveJob` (`:608`):

   ```kotlin
   /** The save the `UnsavedChanges` dialog is waiting for, so that a second press of its Save does not start another. */
   private var editorLeaveJob: Job? = null
   ```

   ```kotlin
   /**
    * The "Save" answer of the unsaved changes dialog. The editor holds the only copy of the text until the file
    * does, so leaving - and, where the dialog was asked by [requestExit], ending the process - is what a write that
    * succeeded earns, not what follows one that was started. The dialog stays up while the file is written. A write
    * that fails takes the dialog away and leaves the editor as it was, which is what the same failure does behind
    * the editor's own Save button; a dialog dismissed in the meantime still means staying, with the text saved.
    */
   fun saveEditorChangesAndLeave() {
       if (editorLeaveJob?.isActive == true) return
       val draft = _editorDraft.value ?: return leaveEditorWithoutSaving()
       editorLeaveJob = viewModelScope.launch {
           val isSaved = writeEditorText(fileName = draft.fileName, text = draft.text)
           when {
               !isSaved -> dismissDialog()
               _visibleDialog.value == DialogType.UnsavedChanges -> {
                   // Taken before leaving, which dismisses the dialog and with it the exit the dialog was asked for.
                   val exit = pendingExit
                   leaveEditor()
                   exit?.invoke()
               }
           }
       }.also { currentSaveJob = it }
   }
   ```
   `dismissDialog()` on failure also clears `pendingExit`, which is the point: a failed save must never be followed
   by `exit()`. Do not keep the dialog up after a failure instead — the snackbar that reports it is drawn in the
   app's window, behind the dialog's scrim.

3. **`requestExit` / `exitOnceSaved`** (`:758-776`): the question is asked *after* waiting for a running save, and from
   the texts themselves rather than from `hasUnsavedEditorChanges.value` — that state is a `combine` collected on
   `viewModelScope`, so right after a `join()` it may not have seen the write yet. Replace both functions with:

   ```kotlin
   /**
    * Closing the application, which is a way out of the editor like any other. A save that is still being written
    * is waited for first, since the process ends with [exit] - and since only then is it known whether it worked:
    * with unsaved text in the editor, which is also what a save that failed leaves behind, the `UnsavedChanges`
    * question is asked, and [exit] only runs once that has been answered with something other than staying.
    */
   fun requestExit(exit: () -> Unit) {
       viewModelScope.launch {
           currentSaveJob?.join()
           if (hasUnsavedEditorText() && backStack.lastOrNull() is CampfireDestination.SongEditor) {
               pendingExit = exit
               showDialog(DialogType.UnsavedChanges)
           } else {
               exit()
           }
       }
   }

   /** [hasUnsavedEditorChanges] as of this moment, for a decision taken right after a write rather than drawn. */
   private fun hasUnsavedEditorText() = _editorDraft.value?.let { it.text != _songTexts.value[it.fileName] } == true
   ```
   and in `leaveEditorWithoutSaving` (`:898-902`) replace `exit?.let(::exitOnceSaved)` with `exit?.let(::requestExit)`
   (the draft is gone by then, so it waits for a running save and exits). `navigateBack` keeps reading
   `hasUnsavedEditorChanges.value`: it is called from a tap, long after the state settled.

4. Update the KDoc of `currentSaveJob` (`:607`): "The last write of the editor's text, from its Save action or from the
   `UnsavedChanges` dialog, which closing the application waits for, see [requestExit]."

5. **`ui/dialogs/Dialogs.kt`** — no double activation, and no Discard racing a write:

   ```kotlin
   CampfireViewModel.DialogType.UnsavedChanges -> UnsavedChangesDialog(
       isSaving = viewModel.isSavingSong.collectAsStateWithLifecycle().value,
       onCancel = viewModel::dismissDialog,
       onDiscard = viewModel::leaveEditorWithoutSaving,
       onSave = viewModel::saveEditorChangesAndLeave,
   )
   ```
   `UnsavedChangesDialog` gains `isSaving: Boolean` as its first parameter; the Save and the Discard `TextButton`s
   get `enabled = !isSaving`. Extend its KDoc with: "While the text is being written only Cancel is left, which
   still means staying: the write is not something to be pressed twice, and Discard would be answering a question
   the write is already answering." Cancel, the scrim and Back stay live during the write on purpose — step 2
   treats a dialog that is gone when the write returns as "stay", so **Back during the save** leaves the user in the
   editor with the text saved, and nothing is popped behind their back.

6. Nothing changes in `SongEditorScreen.kt`, `CampfireDesktopApp.kt` or `:app:desktop`: `requestExit` keeps its
   signature and its `Unit` result.

## Tests
None (the view model and the UI are untested).

## Verify
Compile: `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`.

By hand on desktop (`./gradlew :app:desktop:run`; the library folder is shown in Settings → Library):
1. Happy path: edit, Escape, **Save** → editor closes, reopening shows the text. Edit, close the window, **Save** →
   the app exits and the text is in the file.
2. Failing write: open a song in the editor, then make the write fail — `chmod a-w <library>/songs` on macOS/Linux
   (the atomic save creates a temp file in that directory). Type, press Escape, **Save**: the dialog goes away, the
   editor is still open with the text in it, "Could not save the song" is shown, Save is still enabled.
   `chmod u+w` again, press Save: written.
3. Same set-up, close the window instead of Escape, **Save**: the app does **not** exit; same result as 2. Close the
   window again: the question is asked again.
4. Same set-up, press the editor's Save button and close the window right away: the app does not exit; the question
   appears (the failed save left the text unsaved).
5. Discard still leaves (and exits, when asked by a window close) without writing.

## Docs
- `presentation/CLAUDE.md`, the editor paragraph, last sentences ("The draft is also what the dialog saves, which
  is why it lives in the view model rather than here - the answer can come after this screen is gone."): replace
  with "The draft is also what the dialog saves, which is why it lives in the view model rather than here. The
  dialog's Save leaves only once the write has reported success (`saveEditorChangesAndLeave`): until then the editor
  holds the only copy of the text, so a write that fails takes the dialog away and leaves the editor, the text and
  the ordinary `SaveFailed` message — and a desktop window that was being closed stays open."
- `app/desktop/CLAUDE.md:14`: "…which asks the editor's unsaved changes question first and waits for a save still
  being written before `exitApplication` ends the process" becomes "…which waits for a save still being written,
  asks the editor's unsaved changes question if there is unsaved text then — a save that failed included — and only
  lets `exitApplication` end the process once the text is in the file or has been discarded."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`
- `presentation/CLAUDE.md`
- `app/desktop/CLAUDE.md`

## Depends on
Nothing. Plan **47** (a required update over an unsaved editor) builds on the functions introduced here
(`writeEditorText`, `hasUnsavedEditorText`) and must land after it; plan **46** (the editor's file disappears) edits
the same view model and relies on the dialog's Save being trustworthy, so it is scheduled after this one too.
