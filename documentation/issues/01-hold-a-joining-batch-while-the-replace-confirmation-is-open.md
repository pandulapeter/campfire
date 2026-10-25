<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Hold a batch that would join the import review while its "Replace N files?" confirmation is open, and count the import tag sheet's songs from the live review

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** amended — step 1's wait is now a loop whose last check of the dialog runs with no suspension before
`addToReview` sets `_isAddingToReview` (the draft resumed from `first` through a dispatch and joined without looking at
the dialog again, so a confirmation put up in that gap reopened both scenarios; the gap is argued, not unit-testable);
step 3 remembers the live summary per report. Step 2's reorder and the tests checked and kept.
**Files:**
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportController.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CampfireDialogs.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/DialogType.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/labels/ImportTagsSheet.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ImportReportScreen.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/review/ImportReviewItems.kt`
- `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportControllerQueueTest.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/CLAUDE.md`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CLAUDE.md`

## Problem

Since 7ec93c49d a batch arriving during a review joins it (`ImportController.awaitImportSettled(joined, held)` →
`addToReview`). Nothing stops that while the review's Replace confirmation is open:

```kotlin
fun confirmImport() {
    val review = reportState.report as? ImportReport.Review ?: return
    if (_isAddingToReview.value) return
    val count = replaceCount(review.plan, review.choices)
    if (count > 0) dialogHost.showDialog(DialogType.ConfirmImportReplace(count)) else resolveImport()
}

fun resolveImport() {
    val review = reportState.report as? ImportReport.Review ?: return
    val request = activeRequest ?: return
    if (_isAddingToReview.value) return
    ...
```

and `CampfireDialogs.kt` confirms with `viewModel.dialogHost.dismissDialog(); viewModel.importController.resolveImport()`.

- **A: unconfirmed replacements.** While "Replace 2 songs?" is up, the user opens a Campfire link to a setlist they
  imported before. `addToReview` adds that link's taken names on Replace (`linkResolutions`) to `reportState.report`;
  `resolveImport` re-reads the report, so Replace overwrites 7 files where the user confirmed 2.
- **B: a swallowed tap.** Replace pressed while the joining batch is still being read: the dialog closes, `resolveImport`
  returns on `_isAddingToReview`, nothing is written, and nothing says so.
- **Same root, the tag sheet.** `ImportReportScreen` opens `DialogType.ImportTags(summary.songCount)`, and
  `ImportTagsSheet` shows `pluralStringResource(Res.plurals.import_review_tags_subtitle, dialog.songCount, ...)`: a batch
  landing while the sheet is open (or one already being read when it was opened, since the review stays usable while a
  batch is added) leaves "for N songs" stale.

## Fix

Options weighed:

- (a) Hold a joining batch while a dialog is over the review, and let it join once that closes.
- (b) Pass the confirmed count into `resolveImport` and ask again when it changed or a batch is being added.

**Recommended: (a) for the confirmation only, plus a live count in the tag sheet.** (b) needs a second dialog round
and a new "the list changed" wording; (a) needs neither and keeps "what was confirmed is what is written" by
construction. The tag sheet does not need holding at all once its count is read live: a join never changes its tags
(`addToReview` keeps `choices.tags`), and a live count also covers a sheet opened while a batch was already being read,
which no hold can.

1. `ImportController.awaitImportSettled(joined, held)`: before `addToReview(next)`, wait until the confirmation is not
   shown, as a loop whose **last** check runs with no suspension before `addToReview` claims `_isAddingToReview` (its
   `scope.async` and the flag are set before its first suspension), so that a confirmation can never be put up between
   the wait ending and the batch being marked as being added — `first` resumes through a dispatch, and a tap on Import
   landing there would pass `confirmImport`'s `_isAddingToReview` check:

   ```kotlin
   // Replace confirms the review as it stands: a batch joining under the dialog would add replacements nobody
   // confirmed, or make the confirmation do nothing while it is read. Checked again after every wait, with nothing
   // suspending between the last check and addToReview marking the batch as being added.
   while (canJoinReview(next) && dialogHost.visibleDialog.value is DialogType.ConfirmImportReplace) {
       combine(dialogHost.visibleDialog, reportState.importReport) { dialog, _ ->
           dialog !is DialogType.ConfirmImportReplace || !canJoinReview(next)
       }.first { it }
   }
   if (canJoinReview(next)) { joined += next; addToReview(next) } else held += next
   ```

   `visibleDialog` is enough: nothing stacks over `ConfirmImportReplace` (`DialogStack.kt`'s `stacksOver`), so it is
   never in `openDialogs` without being on top. `canJoinReview` also reads `activeRequest` and `isLeaving()`, which are
   not flows: every change of `activeRequest` comes with a change of `reportState.report`, and an exit that begins
   during the wait is caught by the check after it. A dialog replacing the confirmation (the desktop's close button
   putting Unsaved changes up) ends the wait as a cancel does; the next Import asks again with the new count.
   Batches arriving during the wait stay in `importQueue` and are taken, in order, by the next turns of the loop; a held
   batch is imported after the review as today. Leaving the screen ends the wait through `importReport` going null.
   Update the KDoc of `awaitImportSettled(joined, held)` (and of `confirmImport` if it reads wrong) to say a batch waits
   while the confirmation is open.
2. `CampfireDialogs.kt`, `ConfirmImportReplace`'s `onConfirm`: call `viewModel.importController.resolveImport()` **before**
   `viewModel.dialogHost.dismissDialog()`. `viewModelScope` is `Dispatchers.Main.immediate`, so the wait in step 1 can
   resume inside `dismissDialog` and start `addToReview` before `resolveImport` runs (scenario B again); `resolveImport`
   first clears `activeRequest` and sets `ImportReport.Importing`, so the resumed wait sees `canJoinReview` false and holds
   the batch. Add a one-line comment saying so. `resolveImport`'s `_isAddingToReview` check stays as a guard.
3. Tag sheet: make `DialogType.ImportTags` a `data object` (KDoc: "The tags the import under review puts on every song it
   writes."). In `ImportTagsSheet`, read the report with `viewModel.importReportState.importReport.collectAsStateWithLifecycle()`
   and compute the subtitle's count as `importReviewSummary(review.plan, review.choices).songCount` (from
   `screens/importReport/review/ImportReviewSections.kt`, 0 when the report is no longer a review), inside
   `remember(report)` so that the sheet's own recompositions (typing in its tag field) do not walk the plan again; keep reading the
   starting `tags` once, as now. `reviewHeader`'s `onEditTags` becomes `() -> Unit` (update its `@param` KDoc), and
   `ImportReportScreen` passes `{ viewModel.dialogHost.showDialog(DialogType.ImportTags) }`. `dismissSheet(dialog)` keeps
   working with the object.
4. Docs: in `ui/screens/importReport/CLAUDE.md` (Review bullet, after "a Replace is confirmed first") add that a batch
   arriving while that confirmation is open joins only once it is answered, and is imported after the review if Replace
   wrote it. Check `ui/dialogs/CLAUDE.md`'s `ConfirmImportReplace` line still holds.

## Tests

In `ImportControllerQueueTest` (`./gradlew :presentation:desktopTest`), using the existing `startController`, `enqueue`,
`settle`, `nextPlans`, and `leaveImportReport` helpers:

- `a batch arriving while Replace is being confirmed joins the review once the confirmation is cancelled`: review up,
  `dialogHost.showDialog(DialogType.ConfirmImportReplace(1))`, `nextPlans += listOf(songEntry("other.cho"))`, `enqueue()`,
  `settle()` → the review still holds only `new.cho`, `prepareCount == 1`, `isAddingToReview` false; `dialogHost.dismissDialog()`,
  `settle()` → the review holds `new.cho` and `other.cho`.
- `a batch arriving while Replace is being confirmed is not written by it, and is imported after it`: same start, then
  confirm in the dialog's order (`controller.resolveImport()`, then `dialogHost.dismissDialog()`), `settle()` → one
  import wrote only `new.cho` (`importCount == 1`, the library holds `new.cho`), and the other batch then comes up as a
  review of its own (or, if the fake's clean import leaves a finished report, after `leaveImportReport()`).

The sheet's live count is UI wiring over the tested `importReviewSummary`; no further test.
