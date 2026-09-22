# 33 · An import's conflicts question replaces whatever dialog the user is in the middle of: a name being typed, a delete being confirmed, the editor's unsaved-changes question

**Severity:** wrong behaviour, minor (all platforms. Needs an import with conflicts to finish planning while another dialog or sheet is open: a large archive being planned in the background, or a file opened with the app / dropped on the window while the user works) · **Area:** `:presentation` (`CampfireViewModel.kt`: `import`)

## Symptom
1. Have a library with some songs. Start importing a large archive that contains a few of them in a changed form, so
   the import will ask a conflicts question. On a phone or the web, an archive of a few hundred songs takes long
   enough to plan.
2. While the progress bar runs, open "New song" and start typing a title. Or open a song's delete confirmation, or
   the song picker sheet of a setlist.
3. The conflicts question appears in its place. What was typed is gone. The delete that was about to be confirmed
   has to be started again. A sheet disappears mid-tick.
4. Same with the editor: press back with unsaved text, and while the "unsaved changes" question is up, drop a file
   onto the desktop window or open one with the app. The question is replaced by the import's. Once that one is
   answered, the editor is still there and nothing was lost, but the user has to ask to leave again.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:1259-1263`:

```kotlin
if (plan.hasConflicts) {
    _isImporting.update { false }
    pendingImport = PendingImport(plan = plan, request = request)
    showDialog(DialogType.ImportConflicts(plan.summary))
}
```

`showDialog` goes through `setVisibleDialog` (`:1600-1604`), which replaces whatever `visibleDialog` holds. The import
runs on the queue's own schedule (`importQueue`, `:699-709`), so it lands whenever planning finishes, whatever the user
is doing then. `visibleDialog` holds one dialog at a time (`presentation/CLAUDE.md`), and the dialogs keep their text
in their own composition state, which goes when they leave.

The dialog replaced first loses its work silently. The desktop close button replacing a question on purpose
(`requestExit`) is a different case: that one is the user's own request.

## Fix
The question waits for the screen to be free of dialogs, the way the next batch in the queue already waits for the
question. Nothing is lost while it waits: the plan is held by the running `import()` call, the progress bar is off
("nothing is happening while the question is on screen"), and the queue's consumer is suspended in this very call.

1. `CampfireViewModel.kt:1259-1263`:

   ```kotlin
   if (plan.hasConflicts) {
       // Nothing is happening while the question is on screen, and a progress bar under it would say otherwise.
       _isImporting.update { false }
       // Asked once nothing else is: the user may be in the middle of another dialog, and whatever they had typed
       // into it would go with it. The plan waits with the queue, which is waiting for this question anyway.
       val question = DialogType.ImportConflicts(plan.summary)
       while (!_visibleDialog.compareAndSet(expect = null, update = question)) {
           _visibleDialog.first { it == null }
       }
       pendingImport = PendingImport(plan = plan, request = request)
   }
   ```

   `pendingImport` is assigned after the dialog is up, not before. While the call waits, any other dialog that is
   shown goes through `setVisibleDialog`, which would clear `pendingImport` (`if (dialogType !is
   DialogType.ImportConflicts) pendingImport = null`). The `compareAndSet` bypasses `setVisibleDialog`. That is
   correct here: it only ever replaces no dialog, and nothing is parked behind no dialog (the same reasoning as
   `createSetlist`, `:1357-1358`). `resolveImport` reads `pendingImport` only once the question can be answered,
   which is after both assignments.

2. Check `awaitImportSettled` (`:1170-1172`). It is only called once `import()` has returned, so the wait above
   happens before it and it needs no change.

3. Do **not**:
   - queue the question behind the dialog in a list of pending dialogs. `visibleDialog` holding one dialog is a
     documented design.
   - show the question over the other dialog as a second window. The UI has no stacking of `visibleDialog`s, and the
     setlist picker's local naming dialog is the one deliberate exception.
   - make the wait time out. The user closing their dialog is what ends it, and until then the batch simply waits,
     like the ones queued behind it.

## Tests
None (UI is untested).

## Verify
1. Desktop (`./gradlew :app:desktop:run`): export the library, edit one song in the exported archive (unzip, change a
   line, zip again), open "New song" and type a title, then drop the archive on the window. The dialog stays with the
   typed title. Cancel it: the conflicts question appears right away. Answer it: the import goes through.
2. Editor with unsaved text, press Escape: the unsaved-changes question. Drop the same archive: the question stays.
   Answer Cancel: the conflicts question appears.
3. With no dialog open, drop the archive: the question appears as before, and dropping a second archive while it is
   up still waits its turn (the queue).
4. Android: open the song picker sheet of a setlist, then open a changed `.cho` of an existing song with Campfire from
   a file manager. The sheet stays until it is closed, then the question appears.

## Docs
`presentation/CLAUDE.md`, the `ui/dialogs/Dialogs.kt` bullet, after "…wait their turn rather than being dropped;": add
"an import's question itself waits for whatever dialog or sheet the user has open to be closed rather than replacing
it, since that dialog's typing would go with it;".

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 09 adds a message after an export in the same file and 10, 16, 17, 27 and 34 edit other functions of it; schedule them one after another.
