# 55 · Closing a sheet just as an import's conflicts question appears dismisses the question, and no import works again until the app is restarted

**Severity:** wrong behaviour (all platforms; unlikely — the sheet has to be closed within the ~300 ms of its hide animation in which a slow import finishes planning — but silent and permanent for the session when it happens) · **Area:** `:presentation` (`Dialogs.kt`: `CampfireBottomSheet` and its five call sites; `CampfireViewModel`: `showDialog`, `dismissDialog`, `resolveImport`, `cancelImport`)

## Symptom
1. Start importing a large archive that collides with the library. Planning it takes seconds on a slow phone, and
   only a progress bar shows while it runs: the rest of the UI is free.
2. Meanwhile open "Sort and filter" (or the display options, or either picker sheet) and close it — with its **X**,
   by tapping the scrim, or with back — just as the plan is finished.
3. The "files already exist" question replaces the sheet and vanishes again at once, unanswered.

Nothing was imported, which is fair enough, but from then on every import does nothing at all: the picker, a drop, an
"open with", the demo library from Settings. No progress bar, no message. Only restarting the app helps.

## Cause
Two things, one at each end.

**The sheet reports a dismissal it did not have.**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt:1110`:

```kotlin
onClose = { coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
```

`invokeOnCompletion` runs for a cancelled job as well as for a finished one. When `showDialog(ImportConflicts)`
replaces the sheet, the sheet leaves the composition, its `rememberCoroutineScope` is cancelled, `hide()` is cancelled
with it, and `onDismiss()` — `viewModel::dismissDialog` at all five call sites (`:215`, `:225`, `:245`, `:839`,
`:939`) — runs against whatever dialog is on screen by then. Material's own two ways out have the same shape
(`androidx/compose/material3/ModalBottomSheet.kt:113-133`, material3 1.12.0-alpha03), so guarding the X alone would
leave the scrim and the back press open:

```kotlin
scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) { onDismissRequest() } }   // scrim
scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }                                  // back
```

(`isVisible` reads the anchor the sheet is *closest to*, so it is already false in the second half of the animation.)

**The view model can be left with a plan and no question.** `CampfireViewModel.kt:1479-1482`:

```kotlin
fun dismissDialog() {
    pendingExit = null
    _visibleDialog.update { null }
}
```

drops the `ImportConflicts` dialog without dropping `pendingImportPlan` (`:597`). `awaitImportSettled()` (`:1073`)
looks at the dialog, so the queue moves on, and every later batch returns at the first line of `import` (`:1149`):

```kotlin
if (files.isEmpty() || _isImporting.value || pendingImportPlan != null) return
```

Commit `7e7d893c` closed one way into this state — a dialog put *over* the question clears the plan in `showDialog`
(`:1468-1477`) — but the rule lives in one of the three places that write `_visibleDialog`, so the state is still
reachable. `pendingExit` is kept by the mirror image of the same arrangement: cleared by `dismissDialog`, not by a
dialog shown over `UnsavedChanges`.

## Fix
### A. `CampfireViewModel.kt` — parked work goes with its dialog, in the one place the dialog is set
1. Replace `showDialog` and `dismissDialog` (`:1468-1482`) with:

   ```kotlin
   /**
    * The one place [visibleDialog] is given a value, because two dialogs have work parked behind them that nothing
    * else can answer for: the plan behind [DialogType.ImportConflicts] and the exit behind
    * [DialogType.UnsavedChanges]. Either goes with its dialog, however that leaves the screen - answered, dismissed,
    * or replaced, the way the desktop's close button puts the unsaved changes question over anything. A question
    * nobody can answer any more must not keep every later import from starting, and dropping its plan leaves the
    * library exactly as cancelling would have.
    */
   private fun setVisibleDialog(dialogType: DialogType?) {
       if (dialogType !is DialogType.ImportConflicts) pendingImportPlan = null
       if (dialogType != DialogType.UnsavedChanges) pendingExit = null
       _visibleDialog.update { dialogType }
   }

   fun showDialog(dialogType: DialogType) = setVisibleDialog(dialogType)

   fun dismissDialog() = setVisibleDialog(null)

   /**
    * What a bottom sheet dismisses itself with: [dialogType] goes only while it is still the dialog on screen. A
    * sheet reports its dismissal from the end of its hide animation, and one that is replaced while it is hiding
    * reports the cancellation of that animation the same way - Material's scrim and back handlers included - by
    * which time the dialog on screen is the one that replaced it.
    */
   fun dismissSheet(dialogType: DialogType) {
       if (_visibleDialog.value == dialogType) dismissDialog()
   }
   ```

   A separate name rather than an overload of `dismissDialog`, which is passed as `viewModel::dismissDialog` in some
   twenty places.
2. `createSetlist` (`:1248`) keeps its `_visibleDialog.compareAndSet(null, DialogType.SongPicker(setlist))`: it only
   ever writes over `null`, where both parked values are already null, and it needs the comparison. Say so in a
   comment there so the next reader does not "fix" it:

   ```kotlin
   // Not through setVisibleDialog: this only ever replaces no dialog at all, behind which nothing is parked.
   ```
3. `resolveImport` and `cancelImport` (`:1172-1186`) lose the assignments the setter now makes:

   ```kotlin
   /** The answer to [DialogType.ImportConflicts], which is the only thing that ever overwrites a library file. */
   fun resolveImport(resolution: ImportConflictResolution) {
       val plan = pendingImportPlan ?: return
       // Claimed before the question goes away rather than once the import has started, so that nothing waiting for
       // the two of them to be over (see importDemoLibrary) sees a moment with neither.
       _isImporting.update { true }
       dismissDialog()
       viewModelScope.launch { applyImportPlan(plan, resolution) }
   }

   /** Cancelling leaves the library exactly as it was: the plan is what is thrown away, not a half written import. */
   fun cancelImport() = dismissDialog()
   ```

   A second tap on the confirm button still returns at the first line, since the first one's `dismissDialog()`
   dropped the plan.
4. Update the two KDocs that describe the old arrangement:
   - `pendingImportPlan` (`:592-597`): append "It never outlives that dialog: see [setVisibleDialog]."
   - `pendingExit` (`:610-614`): "Any other way the dialog goes away is staying, so [dismissDialog] forgets it."
     becomes "Any other way the dialog goes away is staying, so [setVisibleDialog] forgets it."
5. Leave alone: the order in `import` (`pendingImportPlan = plan`, then `showDialog(ImportConflicts)` — the setter
   keeps a plan for that type) and in `requestExit` (`pendingExit = exit`, then `showDialog(UnsavedChanges)`);
   `saveEditorChangesAndLeave` / `leaveEditorWithoutSaving`, which take `pendingExit` before `leaveEditor()`
   dismisses; the guard at the top of `import`, which now cannot be stale; `awaitImportSettled`. Do **not** move the
   plan into `DialogType.ImportConflicts`: the dialog holds only what it draws, and the plan holds the bytes of the
   archive.

### B. `Dialogs.kt` — a sheet only ever dismisses itself
1. `CampfireBottomSheet` (`:1105-1111`): dismiss only for a hide that ran to its end.

   ```kotlin
   SheetHeader(
       title = title,
       subtitle = subtitle,
       // Hiding the sheet by hand does not count as dismissing it, so the dialog state is cleared once it is
       // gone: left as it was, the invisible sheet's modal layer would stay over the screen, swallowing the next
       // tap. Only a hide that ran to its end counts: one cut short by a finger taking hold of the sheet leaves the
       // sheet where Material settles it, and one cut short by another dialog replacing the sheet has nothing left
       // to dismiss.
       onClose = { coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { cause -> if (cause == null) onDismiss() } },
   )
   ```

   Do not use Material's `if (!sheetState.isVisible)` idiom here instead: it is true halfway through a cancelled hide.
2. The five call sites pass a dismissal addressed to their own dialog, which is what covers Material's scrim and back
   paths (they call `onDismissRequest`, which is this same `onDismiss`):
   - `:215` `onDismiss = { viewModel.dismissSheet(CampfireViewModel.DialogType.SongsControls) },`
   - `:225` `onDismiss = { viewModel.dismissSheet(CampfireViewModel.DialogType.SetlistsControls) },`
   - `:245` `onDismiss = { viewModel.dismissSheet(dialog) },` (`SongDisplayControls`, `dialog` is smart cast there)
   - `:839` (`SetlistPicker`) and `:939` (`SongPicker`): `onDismiss = { viewModel.dismissSheet(dialog) },` — both
     functions already receive their `dialog`, and neither does anything else on the way out (the pickers write on
     every tick).
   Add to `CampfireBottomSheet`'s KDoc: "@param onDismiss Has to dismiss this sheet's own dialog and nothing else
   (`CampfireViewModel.dismissSheet`): it is called from the end of a hide animation, by which time another dialog
   may have taken the sheet's place."
3. Leave every `AlertDialog`'s `viewModel::dismissDialog` alone — those run synchronously from a tap on a dialog that
   is on screen — and `DismissSheetWhenSidePanelAppears` (`components/Controls.kt:102`), which checks that its sheet
   is the visible dialog before it dismisses. `closeNamingDialog` in `SetlistPicker` (`:834`) is an `AlertDialog`
   path too.

Either half alone removes the dead state; both are needed for the right outcome. With only A, a stray dismissal
cancels the import silently; with only B, the next caller of a bare `dismissDialog()` from a coroutine brings the
dead state back.

## Tests
None (UI is untested).

## Verify
The coincidence is too narrow to hit by hand, so widen it for the check and take it out again: in `import`, put
`delay(3_000)` after `prepareImport(files)` (do not commit it).
1. `./gradlew :app:desktop:run`. Export a song, then drop the exported file back on the window after editing the
   library's copy (so the two differ and the import conflicts). Within the three seconds open "Sort and filter" and
   click **X** at about the three second mark; repeat with a click on the scrim and with Esc. A few tries land inside
   the animation. Before: the question flashes and is gone, and dropping the file again does nothing. After: the
   question stays; **Keep both** imports the file.
2. The invariant on its own: with the question on screen press the desktop window's close button while the editor has
   unsaved text (the unsaved changes dialog replaces the question), choose to stay, then import again: the import
   runs and asks again (this is `7e7d893c`'s case and must still work).
3. Close each of the five sheets with X, scrim, swipe and back on Android (`./gradlew :app:android:assembleDebug`):
   each closes, and the next tap on the screen behind is not swallowed. Grab a sheet while its X animation is
   running and drag it back up: it stays open and usable.
4. Desktop: with unsaved editor text press the window's close button, choose **Discard**: the app still exits
   (`pendingExit` survives `showDialog(UnsavedChanges)`). Choose to stay instead, then leave the editor with back
   and **Discard**: the app does not exit.
5. Remove the `delay`. Compile checks:
   `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`.

## Docs
`presentation/CLAUDE.md`:
- `ui/dialogs/Dialogs.kt` bullet: "The close button hides the sheet (`sheetState.hide()`) and then clears
  `visibleDialog` itself: …" — after that sentence add: "It does so only for a hide that ran to its end, and every
  sheet dismisses through `dismissSheet(itsOwnDialog)`, which does nothing once another dialog has taken the sheet's
  place: a sheet replaced while it is hiding reports the cancelled animation as a dismissal, Material's scrim and
  back handlers included."
- `ui/CampfireViewModel.kt` bullet, where `visibleDialog` is listed: add "`visibleDialog` is written through
  `setVisibleDialog` alone, which is where the work parked behind a dialog — the import plan behind the conflicts
  question, the exit behind the unsaved changes one — is dropped whenever that dialog stops being the one on screen."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. Plan 05 rewrites `saveEditorChangesAndLeave` and `requestExit` next to the functions changed here and relies
on `dismissDialog()` forgetting `pendingExit`, which it still does; plan 34 moves `prepareImport` off the main thread
and leaves `pendingImportPlan` alone. `CampfireViewModel.kt` and `Dialogs.kt` are edited by many plans (03, 05, 14,
15, 33, 34, 35, 46, 47, 48 and 63 among them): schedule this one after another with them, not side by side.
