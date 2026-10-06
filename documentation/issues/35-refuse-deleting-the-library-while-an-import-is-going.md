# Refuse deleting the library while an import is running or waiting for its question

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all (desktop/web drop, Android/iOS "open with" or share)
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt
**Challenged:** amended — added the reverse order: an import queued while the deletion is running now waits for it (a `isDeletingLibrary` flag the import queue's consumer waits on), and a second deletion is refused while one runs.

## Problem

`CampfireViewModel.kt:3529-3531` (8ee010b36)
```kotlin
fun deleteLibrary() = launchLibraryChange {
    deleteLibrary.invoke()
}
```
Only the Settings row that opens the sheet is disabled during an import (`SettingsScreen.kt:670`, `isEnabled = … &&
!isImporting && …`); the sheet's own Delete (`DeleteLibraryDialog`, `Dialogs.kt:823-…`, `enabled = isConfirmed`) and the
view model are not. Files can arrive while the sheet is open: a drop onto the desktop or web window, or an "open with" /
share on a phone, goes into the import queue, which does not wait for dialogs (only the report screen push does).

Scenario: Settings → delete the library; with the DELETE sheet up, drop a zip onto the window. A clean plan goes straight
to writing. Type DELETE and confirm: `DeleteLibraryUseCase` lists and deletes while `ImportFilesUseCase` writes. Files
written after the listing survive and are uploaded by the sync run the deletion starts; files the plan had marked as
duplicates of library files were skipped by the import and are deleted now. A plan with conflicts is just as bad: its
Review waits for the sheet to close (`showImportReport` waits for no dialog), and answering it afterwards applies a plan
made against the library that no longer exists (IDENTICAL entries are disregarded, so those songs are simply missing).
The import's snackbar reports counts that no longer hold.

## Fix

The two must exclude each other in both orders: a deletion must not start while an import is running or waiting for
its question, and an import must not start while a deletion is running. The second order is just as real: the sheet
closes as Delete is tapped, and `DeleteLibraryUseCase` (`deleteAllSongs`, `deleteAllSetlists`, the preferences, the
sync run) takes a while on a large library — on the web every file is an OPFS call — during which a drop or an "open
with" goes into `importQueue`, whose consumer only waits for the demo decision and the previous batch. That import
plans against a half-deleted library (an IDENTICAL entry is disregarded and then deleted) and writes files that the
deletion's listing missed.

1. A flag for the deletion, next to `_isImporting`:
   ```kotlin
   /** True while [deleteLibrary] is deleting, which the import queue waits for, see [deleteLibrary]. */
   private val isDeletingLibrary = MutableStateFlow(false)
   ```
2. In `deleteLibrary`, refuse while an import is in flight, its question is pending or another deletion is running,
   and hold the flag for as long as the use case runs:
   ```kotlin
   fun deleteLibrary() = launchLibraryChange {
       // An import writes against the library it planned for: one running, or one whose question is still open, would
       // either outlive the deletion in part or be applied to a library that is gone. The import queue waits for the
       // flag the other way round.
       if (_isImporting.value || pendingImport != null || isDeletingLibrary.value) return@launchLibraryChange sendMessage(Message.OperationFailed)
       isDeletingLibrary.value = true
       try {
           deleteLibrary.invoke()
       } finally {
           isDeletingLibrary.value = false
       }
   }
   ```
   (`_isImporting` is set at the start of `import()` and cleared once a Review is posted, and set again by
   `resolveImport` before the answer is written; `pendingImport` holds the Review's plan until it is answered or
   abandoned. A batch merely queued behind them is handled by step 3. The check and the `true` happen before the first
   suspension, on the main thread, like `import()`'s own claim of `_isImporting`, so the two cannot interleave.)
3. The import queue's consumer in `init` (`for (request in importQueue) { try { import(request) …`): before
   `import(request)`, wait for a deletion that is running, re-checking synchronously so that nothing can slip in between
   the check and `import()` claiming `_isImporting`:
   ```kotlin
   // A batch that arrived while the library was being deleted is planned against what the deletion left, not against
   // a library half gone.
   while (isDeletingLibrary.value) isDeletingLibrary.first { !it }
   import(request)
   ```
   Waiting rather than refusing: the files were asked for, and an empty library is a fine place for them.
4. `DeleteLibraryDialog`: collect `viewModel.isImporting` and `viewModel.importReport`, and make the Delete action
   `enabled = isConfirmed && !isImporting && importReport !is ImportReport.Review` (and gate the keyboard's Done path,
   the `isConfirmed ->` branch, the same way), so the button simply waits, greyed, until the import is over. Keep the
   view model check: it closes the window between the tap and the coroutine running.

No string is added (`error_operation_failed` already exists in both languages).

## Tests

None: the guard is view model state; no pure logic changes.

## Manual check

Desktop: Settings → Library → delete; with the sheet up, drop a folder of a few hundred `.cho` files onto the window.
While the import runs, Delete stays disabled even with DELETE typed; once the import's snackbar appears it enables and
deleting leaves an empty library. Repeat with a file that conflicts with a library song: Delete stays disabled while the
question is pending (close the sheet to see the question, answer it, reopen). Reverse order: with a few hundred songs
in the library, type DELETE, confirm, and drop a song onto the window at once: it is imported into the emptied library
(it is there afterwards and is uploaded by the next run), not lost to the deletion.
