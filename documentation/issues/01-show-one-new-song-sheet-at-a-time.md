# Show the New song sheet of a paste or share only over nothing, one at a time, after the onboarding

**Challenged:** amended — the form now holds the queue until it is answered, as a review does (the old text both promised that other batches are not blocked and asked the consumer to wait for the form, which blocks them); it waits for exactly what the import screen waits for (`ImportReportState.show`: no dialog, no unsaved editor on the stack, no onboarding) plus no report on screen, and re-checks `isLeaving`; the request settles only once the form has left, which What's new and plan 03 rely on.

**Kind:** bug  ·  **Severity:** high  ·  **Platforms:** all (iOS and Android most: shares queue while the app is away)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportController.kt`,
`.../ui/state/importing/ImportReportState.kt` (the wait it already has, shared),
`.../ui/dialogs/forms/NewSongDialog.kt`,
`presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireDesktopApp.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportControllerQueueTest.kt`,
`presentation/CLAUDE.md` (Document imports)

## Problem

At HEAD (`51d66ea99`; the code is unchanged since `4bde19afe`), `ImportController.prepare` puts the form up and ends
the import at once:

```kotlin
val newSongDialog = if (request.isNewSongFromText) newSongFromTextDialogOf(plan) else null
if (newSongDialog != null) {
    // The form is not an import: nothing waits for it, and closing it writes nothing.
    _isImporting.value = false
    dialogHost.showDialog(newSongDialog)
}
```

Three things go wrong from there.

1. **A second text replaces the first form, keeping the first one's values.** The queue's consumer (`startQueue`) waits
   on `awaitImportSettled()` (`report != null || isImporting`), which is already false, so the next queued text is
   prepared while form A is open. `showDialog(NewSongFromText(B))` lands in the same slot (`DialogStack.kt`: `slotKey`
   is the class for every dialog but `SongInfo` / `SongEdit`), so the sheet stays composed, and `NewSongDialog` keeps
   its state: `var values by rememberSaveable(songFileName, stateSaver = songMetadataSaver) { mutableStateOf(offeredDraft) }`
   — `songFileName` is null for both. Create then writes B's text under A's title and artist, and A is lost.
   Scenario: share three songs from Safari through the iOS extension, then open Campfire; `drainShareInbox` sends three
   `SharedTextPayload`s in one burst. Android: two shares in a row. Desktop/web: two quick drops.
2. **A share's form replaces whatever dialog is open.** `NewSongFromText` stacks over nothing, so `showDialog` drops the
   whole stack: an open Song metadata sheet with typed values, a New setlist title, the tag sheet — gone without a word.
   Paste and the web drop check `isNewSongPasteAvailable` before they run, but a share never does, and the desktop's
   `onDrop` does not re-check what `shouldStartDragAndDrop` checked (a dialog can open during a long drag).
3. **On a first run the form opens over the onboarding.** The `songToOpen` branch of `applyImportPlan` waits
   (`snapshotFlow { backStack.none { it is CampfireDestination.Onboarding } }.first { it }`); the form does not. iOS:
   share from Safari right after installing, then open the app: the sheet covers onboarding page 1, and Create pushes
   the song and the editor over the onboarding entry.

## Fix

The form is treated like a review: it is put up once nothing is in its way, and the queue takes the next batch only
once it has left. Holding file batches behind an unanswered form is deliberate and matches what a review already does
(`startQueue`: "The next batch waits for this one's review too"): a file batch taken meanwhile would run a progress bar
under the form, and its import screen would wait for the form anyway (`ImportReportState.show` waits for no dialog).

- **Share the import screen's wait.** `ImportReportState.show` waits, launched, for
  `dialog == null && !(hasUnsavedEditorText() && stack.any { it is CampfireDestination.SongEditor }) && stack.none { it is CampfireDestination.Onboarding }`
  (combined over `dialogHost.visibleDialog`, `editorDraft`, `songTexts`, and `snapshotFlow { backStack.toList() }`).
  Pull that condition into an `internal suspend fun awaitNothingInTheWay()` on `ImportReportState` and use it from
  `show` and from the form. The editor clause matters for the form too: `PastedSongController` checked
  `hasUnsavedEditorText` when the text arrived, but the form now may wait, and its Create pushes a song and an editor.
- **`prepare` returns the form instead of showing it.** Keep `_isImporting.value = false` there (no progress bar under
  someone else's dialog); hand the `DialogType.NewSongFromText` back to `import`'s caller (e.g. `import` returns it,
  `null` otherwise; the first run's `plantDemoLibraryOnFirstRun` calls `import` and ignores the result, as it never
  sets `isNewSongFromText`). `retryImport` never sees a text request (texts are not reviewed, `isReviewed` is false).
- **The consumer shows it and holds the queue until it has left**, inside the `try` of `startQueue` so the `finally`
  (count, `settled`) runs only after:

  ```kotlin
  val form = import(request)
  if (form != null) {
      while (true) {
          reportState.awaitNothingInTheWay()
          // A result reopened from the snackbar's Details is a report the queue did not start; wait it out too.
          awaitImportSettled()
          if (isLeaving()) break
          // showIfNoneIsShown can lose to What's new (which also waits for null); then wait again.
          if (dialogHost.showIfNoneIsShown(form)) {
              dialogHost.openDialogs.first { form !in it }
              break
          }
      }
  }
  awaitImportSettled()
  ```

  (Shape, not wording; the two waits may be folded into one `combine`.) Consequences, all intended: `queuedImportCount`
  stays above zero while the form waits or is open, so What's new (`canShowWhatsNew`) waits for it — "What's new shows
  after startup import questions" — and there is no deadlock: What's new waits for the queue, the form waits for no
  dialog, and whichever wins `showIfNoneIsShown` is answered by the user first. `request.settled` completes only when
  the form has left (Create, close, or replaced by another dialog), which plan 03 uses as "taken". A form that is never
  answered holds the queue exactly as an unanswered review does.
- **Key the form's state by what it offers**, as a belt: `rememberSaveable(songFileName, offeredValues, stateSaver = …)`
  in `NewSongDialog`. With one form at a time it no longer matters for correctness; it is one argument.
- **Desktop drop**: in `onDrop`, re-check `viewModel.pastedSongController.isNewSongPasteAvailable` before
  `newSongFromText(text)` (return false otherwise), as `shouldStartDragAndDrop` and the web's `droppedTexts` collector do.
  `presentation/src/desktopMain/CLAUDE.md` already says "accepted only where `isNewSongPasteAvailable`".
- Update `presentation/CLAUDE.md`'s Document imports bullet (re-read it first; it is being edited): the sheet waits, as
  the import screen does, for any open dialog, unsaved editor text, and the onboarding, and holds the queue until it is
  closed, so texts arriving together are offered one after another.

Not in scope: a form waiting while read only mode is switched on (it still opens), and restoring the form after process
death (`NewSongFromText` is never a `SavedDialog`).

## Tests

In `ImportControllerQueueTest` (its fakes; move with `runCurrent()` and `Snapshot.sendApplyNotifications()` as its other
tests do). The existing "a pasted text that is one new song opens the New song form and writes nothing" must still pass;
its `settled` is now not complete until the form is dismissed.
- two texts enqueued together: only the first form is shown; after `dialogHost.dismissSheet(first)` the second appears,
  with its own `values`; the first `settled` completes only on that dismissal;
- a text arriving while another dialog is visible does not replace it, and its form appears once that dialog closes;
- a text arriving with an `Onboarding` entry on the back stack shows its form only after the entry is removed;
- a file batch enqueued behind an open form is not prepared (`prepareCount` unchanged) until the form is dismissed.
