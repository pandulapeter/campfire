# Make the import queue wait for an import screen that is already open before it starts the next batch

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (most easily desktop and web drag and drop, Android "open with")
**Challenged:** amended — the test runs the controller in `backgroundScope` (the consumer never ends, so a child of the test scope would fail `runTest`), and the import screen's `CLAUDE.md` sentence now names the reopened result; the fix itself held against the first-run demo import, Settings' Add demo library, the deletion wait, What's new, and the desktop exit.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/ImportController.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/ImportControllerQueueTest.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/CLAUDE.md`

## Problem

The queue consumer waits for an import to settle only *after* it has started one
(`ImportController.startQueue`, at 72d48deb7):

```kotlin
for (request in importQueue) {
    try {
        while (isDeletingLibrary.value) isDeletingLibrary.first { !it }
        if (isLeaving()) continue
        import(request)
        // The next batch waits for this one's review too: it would have nowhere to be shown.
        awaitImportSettled()
    } finally { … }
}
```

and `import` guards only against a running import or a pending request, not against a report on screen:

```kotlin
if (request.source.isEmpty || _isImporting.value || activeRequest != null) return
```

The snackbar's **Details** reopens a finished import while the queue is idle:

```kotlin
fun openImportReport(result: ImportResult) {
    if (_importReport.value != null || _isImporting.value) return
    showImportReport(ImportReport.Finished(result))
}
```

So while the user reads that result, a file dropped on the desktop or web window, or handed over by Android's "open
with", is taken at once: `import` → `showImportReport(ImportReport.Loading(…))` overwrites `_importReport` in place.
`isImportReportOnBackStack` is already true, so nothing is pushed; the screen the user is reading crossfades into the new
import's Loading and then its Review. Pressing Back or Close "to leave the old result" runs `onImportReportLeft`, which
cancels the Loading or drops the Review (`activeRequest = null; _importReport.value = null`), and the batch is settled in
the consumer's `finally`. The user's new import is lost without a word. A single song handed over by "open with" (not
reviewed) whose name is taken goes the same way, through `showImportReport(review)` in `prepare`.

The import screen's `CLAUDE.md` already promises the opposite: "leaving a result lets the next batch of the queue start,
which waits for the report as it waits for an import (`awaitImportSettled`)".

## Fix

Wait for the queue to be free *before* each batch as well as after it, in `startQueue`. The deletion wait must stay
inside the same loop, since a Details report can be opened while a deletion is waited out and the other way round:

```kotlin
for (request in importQueue) {
    try {
        // A result reopened from the snackbar's Details is on screen while the queue is idle; a batch taken now
        // would replace it in place, and leaving the screen would drop the new import with it.
        do {
            awaitImportSettled()
            while (isDeletingLibrary.value) isDeletingLibrary.first { !it }
        } while (_importReport.value != null || _isImporting.value)
        if (isLeaving()) continue
        import(request)
        awaitImportSettled()
    } finally { … }
}
```

No suspension point lies between the loop's last check and `import` claiming the queue (`showImportReport` sets
`_importReport` synchronously for a reviewed batch, `prepare` sets `_isImporting` before its first suspension for any
other), so a Details tap cannot slip in between. Keep the comment in `import` ("this holds by construction") true by
adding "and for any report on screen" to it. Update the KDoc of `awaitImportSettled` to say the consumer also waits for
it before taking a batch, since a result reopened from Details is a report the queue did not start.

The first run's demo import calls `import` directly before the consumer takes anything, so it is unaffected. Settings'
Add demo library goes through the queue and waits like any batch; it cannot be tapped while a report covers Settings.
The desktop exit waits only for `isImporting`, and a batch still waiting behind a result when the process leaves is
skipped by the `isLeaving()` check after the wait, as today. Nothing deadlocks: a reopened result is only ever cleared
by leaving its screen, which is the wait's end, and `showImportReport` pushes it once no dialog, unsaved editor, or
onboarding is in the way, as it pushes every other report.

In the import screen's `CLAUDE.md`, "leaving a result lets the next batch of the queue start, which waits for the report
as it waits for an import (`awaitImportSettled`)" becomes "…which waits for the report, a result reopened from
**Details** included, as it waits for an import (`awaitImportSettled`)".

## Tests

Add `ImportControllerQueueTest` in `presentation/src/commonTest/.../ui/state/` with `runTest`, passing `backgroundScope` as
the controller's scope (the queue's consumer loops for good, and a child of the test scope would keep `runTest` from
ending) and `advanceUntilIdle()` / `runCurrent()` to move it. Build an `ImportController` over real
`DialogHost(backgroundScope)` and `MessageSink(backgroundScope)`, `launchFileTransfer = { null }`, a `mutableStateListOf(Songs)` back
stack whose `updateBackStack` applies the block and calls `controller.onBackStackChanged()`, `MutableStateFlow`s for
`songTexts`/`editorDraft`, `hasUnsavedEditorText = { false }`, `isLeaving = { false }`, and small fakes of
`DownloadImportFileUseCase`, `PrepareImportUseCase` (returns an `ImportPlan` with one new song), `ImportFilesUseCase`
(returns an `ImportResult` naming it), `RememberDemoLibraryFilesUseCase`, and `DeleteLibraryUseCase`. Call
`Snapshot.sendApplyNotifications()` after changing the back stack outside a composition, since `showImportReport`
reads it through `snapshotFlow`.

- `a batch queued while a reopened result is on screen waits until the screen is left`: start the queue with a completed
  `demoLibraryDecision`, call `openImportReport(ImportResult(importedSongFileNames = listOf("a.cho")))`, advance, assert
  `importReport.value is ImportReport.Finished`; `enqueueImport(ImportSource.Files(listOf(file)))`, advance, assert it is
  still `Finished` and the prepare fake was not called; remove `ImportReport` from the back stack, advance, assert the
  report is now `Loading` or `Review` and the prepare fake was called once.
- `leaving a reopened result does not settle a batch queued meanwhile`: as above, assert the `CompletableDeferred`
  returned by `enqueueImport` is not completed after the Finished screen was left, and is completed after its own
  Review is left.

## Manual check

On the desktop: import an archive that leaves something out so the result is a snackbar with Details (or any clean
import), press **Details**, and while the result is on screen drag another `.zip` onto the window. The result stays as it
was. Close it: the new import's screen opens with its review. On Android, do the same with a file opened with Campfire
from the Files app while the Details result is up.
