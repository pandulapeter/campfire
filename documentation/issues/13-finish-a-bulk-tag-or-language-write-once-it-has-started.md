# Run the selection's bulk tag and language write on `NonCancellable` once it has started, as an import's writes are

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** Android mostly (an activity finished while the write runs); all in principle
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/SelectionEditing.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/SelectionEditingTest.kt` (created by plan 12; create it here if plan 12 was dropped), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CLAUDE.md`

Land after plan 12, which edits the same file.

## Problem

`SelectionEditing.edit` (L61–75 at d650f3cb0) launches the write in the view model's scope through
`messageSink.launchLibraryChange` (`MessageSink` is built with `viewModelScope`) and calls the use case directly:

```kotlin
private fun edit(fileNames: List<String>, edit: (String) -> String) = messageSink.launchLibraryChange {
    runningEdits.update { it + 1 }
    try {
        val result = editSongs(fileNames = fileNames, edit = edit)
```

`SongRepositoryImpl.editSongs` makes only each file's write `NonCancellable`; between files it takes
`libraryFileLock.withLock` cancellably, so once the scope is cancelled the loop stops at the next file. Saving a tag
onto 500 ticked songs and then leaving the app with Back (the Android activity finishes and the view model is cleared)
leaves the tag on some of the selection and not on the rest, with no message. `ui/CLAUDE.md` already states the rule
for the other bulk write: "An import's writes run on `NonCancellable` once they start, so the view model going away with
a finished Android activity does not leave an archive half imported", and `ImportController.applyImportPlan` does it
with `val result = withContext(NonCancellable) { importFiles.invoke(...) }`.

## Fix

In `SelectionEditing.edit`, match the import:

```kotlin
// Not cancellable once it has started: the view model going away with the Android activity is no reason to leave a
// tag on half of the songs it was saved onto, and the repository the files go into outlives it.
val result = withContext(NonCancellable) { editSongs(fileNames = fileNames, edit = edit) }
```

(imports `kotlinx.coroutines.NonCancellable` and `kotlinx.coroutines.withContext`). The snackbar after it is skipped
when the scope was cancelled meanwhile, since `withContext` rethrows the cancellation on return, which is what the
import does as well; `runningEdits` is still decremented in the `finally`.

Docs:
- `ui/CLAUDE.md`, the paragraph on intent handlers and `launchLibraryChange` ("An import's writes run on
  `NonCancellable` once they start…"): add "and so do the selection's tags and languages (`SelectionEditing`)", or name
  both in the sentence.
- `ui/dialogs/CLAUDE.md`, section "The selection mode's sheets", the Manage tags / Manage languages paragraph, the
  sentence "The selection's are written by `CampfireViewModel.setSelectionTags` / `setSelectionLanguages`
  (`SelectionEditing`), each file edited as it is on disk through `EditSongsUseCase`…": add that the write, once
  started, finishes even if the screen goes.

## Tests

In `SelectionEditingTest`: `a bulk tag write carries on when the view model's scope is cancelled`. Build
`SelectionEditing` and its `MessageSink` on a scope of its own,
`val viewModelScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())`, with an `EditSongsUseCase` fake
that, for each file, awaits a `CompletableDeferred` before applying `edit` to its map entry. Call `setSelectionTags`
over three files, `runCurrent()`, then `viewModelScope.cancel()`, complete the gate, `advanceUntilIdle()`: all three
files carry the tag. At d650f3cb0 the fake's `await` throws on cancellation and only the files before it are tagged.
(The fake has to suspend cancellably, as the real lock does, for the test to tell the two apart.)

## Manual check

On Android, with a few hundred songs ticked, Manage tags, tick a new tag, Save, and immediately press Back until the
activity finishes. Reopen the app: every ticked song carries the tag (check the tag filter's count).
