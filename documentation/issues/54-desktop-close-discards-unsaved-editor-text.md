# 54 · Desktop: the window's close button and Cmd+Q discard unsaved editor text without asking

**Severity:** medium · **Area:** `:app:desktop`, `:presentation` (desktopMain, `CampfireViewModel`)

`CampfireDesktopApplication.kt:44` sets `onCloseRequest = ::exitApplication`, which bypasses `navigateBack` and its
unsaved-changes check entirely; `exitApplication` also returns from `main` while a `NonCancellable` save may still be
writing on an IO thread (`application` calls `exitProcess` by default). The Escape-driven `onExit` in
`CampfireDesktopApp.handleKeyEvent` (:105) is only reachable with one entry on the stack, so it cannot hit the
editor, but the same "no join on the save" applies to Save → Escape → Escape.

## Fix

1. ViewModel: `fun requestExit(exit: () -> Unit)`:
   - if `hasUnsavedEditorChanges.value` and the editor is on top: remember `exit` in `private var pendingExit` and
     `showDialog(DialogType.UnsavedChanges)`; `saveEditorChangesAndLeave` / `leaveEditorWithoutSaving` then, after
     their own work, call `pendingExit?.invoke()` — for the save path only after the write's job completed
     (`saveSongContent` returns the `Job`; `join()` it inside a `viewModelScope.launch` before invoking).
     `cancel` on the dialog clears `pendingExit`.
   - otherwise: `viewModelScope.launch { currentSaveJob?.join(); exit() }` where `currentSaveJob` is the last
     `saveSongContent` job.
2. `CampfireDesktopApplication`: `onCloseRequest = { viewModel.value?.requestExit(::exitApplication) ?: exitApplication() }`,
   and `handleKeyEvent`'s `onExit` branch calls `requestExit` too.
3. Cmd+Q on macOS goes through `onCloseRequest` in Compose Desktop; verify, and if it does not, set a
   `Desktop.getDesktop().setQuitHandler` in `main` that calls the same thing.
4. `app/desktop/CLAUDE.md`: closing the window is a back-navigation as far as unsaved text is concerned.

## Verification

Desktop: type in the editor, click the window's close button: the dialog appears; Save closes the app after the
file is written (check the file); Discard closes without writing; Cancel keeps the window.
