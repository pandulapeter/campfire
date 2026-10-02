# Let the user cancel an import while it is still being prepared

**Challenged:** amended — a Cancel that lands after the preparation finished but before the consumer resumed is now honoured (a flag checked after `await()`, not only `Deferred.cancel()`), the Cancel button fades in and out instead of popping with the phase, the `async` is spelled out as a sibling in the supervisor scope (not a child of the consumer), and the CLAUDE.md sentence that says the dialog "cannot be dismissed" is replaced rather than appended to.

**Kind:** robustness  ·  **Severity:** medium  ·  **Platforms:** all (worst on the web, where one thread does everything)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ImportProgressDialog.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt` (the call at ~line 241),
a new `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ImportProgressDialogTest.kt`,
`presentation/CLAUDE.md` (the `ImportProgressDialog.kt` bullet), `CLAUDE.md` (the Conventions bullet that says "An import
that takes a moment shows its phase and a processed-entry count in a dialog"). No strings: the existing `cancel` string
is reused.

## Problem
`ImportProgressDialogHost` shows an `AlertDialog(onDismissRequest = {}, ..., confirmButton = {})`: no button, not
dismissable. Preparing a big PDF songbook (UNPACKING, READING, COMPARING) can take minutes, and
`PrepareImportUseCaseImpl` yields between files and songs precisely so that it can be cancelled ("lets a preparation
nobody awaits any more notice cancellation"), but nobody can ask for it: the only way out is killing the app.
Nothing is written during those phases (`ImportFilesUseCaseImpl` does the writing, in `NonCancellable`), so cancelling
is free of consequences.

`CampfireViewModel.import(request)` runs `prepareImport(...)` inline in the queue consumer's coroutine and its
`catch (exception: CancellationException) { ...; throw exception }` is for the *view model going away*. A Cancel
therefore cannot simply cancel the consumer: that would end `for (request in importQueue)` for good, and every later
import would silently never run.

## Fix
1. In the view model keep `private var preparation: Deferred<ImportPlan>? = null` and
   `private var isPreparationCancelled = false` (both touched on the main thread only: `import()` runs in
   `viewModelScope`, and the dialog's click calls in on main). In `import()` replace the inline call with a deferred
   launched in `viewModelScope` — a **sibling** of the consumer under the scope's `SupervisorJob`, not a child of it, so
   that cancelling it cannot cancel the consumer and a failure inside it is only delivered by `await()` (an `async`
   never reports to the exception handler, and the supervisor does not cancel the scope):
   ```kotlin
   isPreparationCancelled = false
   val deferred = viewModelScope.async { prepareImport(request.files) { if (request.shouldAnnounceResult) _importProgress.value = it } }
   preparation = deferred
   val plan = try {
       deferred.await()
   } catch (exception: CancellationException) {
       _importProgress.value = null
       _isImporting.value = false
       // The user's Cancel cancels only the deferred; the consumer itself still being active is what tells the two apart.
       if (isPreparationCancelled && currentCoroutineContext().isActive) return
       throw exception
   } catch (exception: Exception) {
       ...unchanged...
   } finally {
       preparation = null
   }
   // A Cancel can land after the preparation finished but before this resumed (the resumption is dispatched to the main
   // thread, behind a click already queued there): it was still pressed while the dialog said "reading", so it wins.
   if (isPreparationCancelled) {
       _importProgress.value = null
       _isImporting.value = false
       return
   }
   ```
   `await()` only resumes once the deferred has *completed*, so no late `onProgress` from the `Dispatchers.Default`
   body can set the progress again after it was cleared here. Returning normally makes the loop go on to
   `awaitImportSettled()` (no report, not importing: returns at once) and `request.settled.complete(Unit)`, so the
   queue moves on to the next batch (an open-with batch queued behind it runs; `importDemoLibrary`'s
   `enqueueImport(files).await()` returns). The view model being cleared cancels both the consumer and the deferred:
   `currentCoroutineContext().isActive` is then false and the exception is rethrown as before.
2. ```kotlin
   fun cancelImportPreparation() {
       val deferred = preparation ?: return
       isPreparationCancelled = true
       deferred.cancel()
   }
   ```
   Announce nothing: the user pressed Cancel and the library is exactly as it was. Once `preparation` is null (the plan
   is being written, or a question is up) the call does nothing, so a stale click on a button that is fading out is
   harmless.
3. `ImportProgressDialogHost(progress, canShow, onCancel: () -> Unit)`: add a pure
   `internal val ImportProgress.Phase.isCancellable get() = this == UNPACKING || this == READING || this == COMPARING`
   (IMPORTING and FINISHING are the writing and stay uncancellable). Always pass a `dismissButton` slot, holding
   `AnimatedVisibility(visible = progress.phase.isCancellable, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically())
   { TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) } }`, so the button fades rather than
   popping when the phase moves on to IMPORTING (root CLAUDE.md: nearly every visible change is animated). Keep
   `onDismissRequest = {}`: a tap outside or Back must not cancel. Pass `viewModel::cancelImportPreparation` from
   `Dialogs.kt`.
4. Cancellation is noticed at the next `yield()`. A single huge `documentRepository.extract(file)` is not interruptible;
   say so in a comment on the dialog (no change in `PrepareImportUseCaseImpl`). On the web, where everything shares one
   thread, the click itself is only handled once the preparation yields.
5. The import screen (`ImportProgressContent` shown while a decided import is written) gets no Cancel: it is writing.
   The first-run demo import (`shouldAnnounceResult = false`) shows no dialog, so it cannot be cancelled either.
6. `presentation/CLAUDE.md`: in the `ImportProgressDialog.kt` bullet replace "in a progress dialog that cannot be
   dismissed" with a dialog whose Cancel, offered during the three preparing phases only, cancels the preparation (not
   the queue); the writing phases stay uncancellable and tapping outside never cancels. Root `CLAUDE.md`: add "and can
   be cancelled until it starts writing" to the sentence about the import's progress dialog.

## Tests
`ImportProgressDialogTest`: `isCancellable` is true for UNPACKING, READING and COMPARING and false for IMPORTING and
FINISHING (fails to compile before the change). The cancellation plumbing is UI/view-model code and is not unit-tested
in this repo.

## Manual check
Import a large PDF songbook (or a zip of a few hundred songs; on the web build too), press Cancel in the dialog while
it is reading: the dialog goes away, the library is unchanged, no snackbar. Then import a small file: it imports (the
queue still works). Open a second file with the app while the first is preparing, cancel the first: the second runs.
Watch the phase move from comparing to importing on a large zip without conflicts: the Cancel button fades out.
