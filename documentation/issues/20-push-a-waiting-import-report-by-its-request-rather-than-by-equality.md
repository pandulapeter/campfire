# Push a waiting import report by the request that put it up rather than by comparing its value, so that following a rename or a deletion can never strand it

**Kind:** bug (hardening)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt

**Challenged:** sound

## Problem

`showImportReport` (`CampfireViewModel.kt:2661-2671` at 800ebde0b) sets the report at once, waits until no dialog is
up and no editor with unsaved text is on the stack, and then pushes the screen only if the report is *equal* to the one
it was asked to show:

```kotlin
private fun showImportReport(report: ImportReport) {
    _importReport.value = report
    viewModelScope.launch {
        combine(_visibleDialog, _editorDraft, _songTexts, snapshotFlow { backStack.toList() }) { dialog, _, _, stack ->
            dialog == null && !(hasUnsavedEditorText() && stack.any { it is CampfireDestination.SongEditor })
        }.first { it }
        if (_importReport.value == report && !isImportReportOnBackStack) {
            updateBackStack { add(CampfireDestination.ImportReport) }
        }
    }
}
```

`followReportedFileNames` (`:2683-2685`) rewrites a `Finished` report whenever the app renames or deletes a file it
names — called from `renameSongFile` (`:1661`), `deleteSong` (`:1682`) and `deleteSetlist` (`:2980`):

```kotlin
private fun followReportedFileNames(fileName: (String) -> String?) = _importReport.update { report ->
    if (report is ImportReport.Finished && report.result != null) ImportReport.Finished(report.result.followingLibraryFileNames(fileName)) else report
}
```

`ImportReport.Finished` and `ImportResult` are data classes, so a follow that changes nothing leaves an equal value;
but a follow that does change a name (the deleted or renamed file is among `importedSongFileNames`,
`importedSetlistFileNames`, `duplicateFileNames` or `convertedSongFileNames`) makes the waiting coroutine's
`_importReport.value == report` false. The screen is then never pushed, so `onImportReportLeft` never runs and
`_importReport` stays non-null for the rest of the session. Everything gated on it is stuck: `awaitImportSettled`
(`:2338`) never returns, so every later import, "open with", share and Settings' demo songs wait forever;
`openImportReport` (`:2675`) refuses, so a snackbar's Details does nothing; `showWhatsNewOnVersionChange` (`:2448`)
never shows What's new.

How it can be reached today: only through ordering. The reviewer's scenario (deleting the song from the editor) does
not exist — the editor offers no Delete and no Update file name (`SongEditorScreen.kt:985`, `canUpdateFileName =
false`); the three callers are the song and setlist delete confirmations (`Dialogs.kt:372`, `:430`) and Song actions'
Update file name, which a waiting report's modal dialog keeps out of reach. A delete confirmation that is confirmed
while a report waits behind it calls `deleteSong` (which suspends on the file write) and then `dismissDialog`, so the
waiter normally resumes and pushes the screen before the follow runs; the bug needs the delete's I/O to come back
before the waiter is resumed. The failure is severe and silent, the guard is one line, and the next feature that
follows a file name while a report waits (an editor Delete, a rename from a sheet) makes it deterministic.

## Fix

Compare the request, not the value. Recommended:

1. Add next to `isImportReportOnBackStack`:

   ```kotlin
   /**
    * Counts the reports [showImportReport] has put up, so that a waiting one is pushed only if no later one replaced
    * it. The value cannot be compared instead: [followReportedFileNames] rewrites a waiting report as the library moves.
    */
   private var importReportRequest = 0
   ```

2. In `showImportReport`, take `val request = ++importReportRequest` before setting the value, and replace the check
   with `if (importReportRequest == request && _importReport.value != null && !isImportReportOnBackStack)`. The
   `!= null` keeps the existing behaviour of not pushing a report that was let go of meanwhile; the counter keeps a
   report that was replaced by a newer `showImportReport` from pushing twice (the newer one pushes itself).

Rejected alternative: re-arming (having `followReportedFileNames` update the waiter's expected value) — it spreads the
invariant over two places, which is how it broke in the first place (commit 560527ddd added the rewrite).

## Tests

None: the view model is not unit tested, and the change is in its coroutine plumbing, not in a pure helper.
`ImportReportSectionsTest` already covers `followingLibraryFileNames`.

## Manual check

On the desktop build (where a drop imports while anything is on screen): open a song's Delete song confirmation on the
Songs screen and leave it up; drop onto the window a zip holding that song's exact `.cho` file (a duplicate) and one
`.png` (skipped). Confirm the delete. The import screen comes up once the dialog has gone, and leaving it, a second
drop imports normally and the snackbar's Details opens. (Without the fix this only fails if the delete returns before
the waiter runs, so the check is that nothing regressed rather than that a reproducible failure disappeared.)
