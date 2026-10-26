# Keep an import preview on the back stack only while the review it was opened over is the one on screen

**Kind:** bug · **Severity:** low · **Platforms:** web (the screen's self-close: all)
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ImportReport.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/ImportPreview.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/BrowserRoutes.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/CLAUDE.md` (only if it names the rule),
`presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/BrowserHistory.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportReportState.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportPreviewing.kt` (KDoc naming `ImportPreview.SingleSong`),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/ImportPreviewState.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/BrowserRoutesTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/NavigatorTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportReportStateTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportControllerQueueTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportPreviewingTest.kt` (its `ImportPreview(...)`)
**Challenged:** amended — `ImportPreview.reviewId` is required, not defaulted (the web's history entries live in
`BrowserHistory.entries`, in memory, with only the depth in `history.state`, and Android / iOS saved state is the same
build's and the Navigator drops previews on restore, so no older encoding is ever decoded); the self-close skips an
`Importing` report (a single-song preview composed afresh during its write — an Android activity recreated by a change
`configChanges` does not cover, such as dark mode — would otherwise be popped mid-write and the import reported as
left), and lives in a small composable in `ImportPreviewState.kt` rather than growing `SongDetailsScreen` (plan 01).

**Lane P, first.** Lane P is cut from the main checkout's `HEAD` after the 46th review's lane A has merged (it edits
`SongDetailsScreen.kt` and `screens/songDetails/CLAUDE.md`, which that lane owns).

## Problem

A browser Forward can put an import preview back on the back stack with no live review behind it. `BrowserRoutes.validate`
keeps a preview whenever any import report exists (`Loading`, `Review`, `Importing`, `Finished`):

```kotlin
is CampfireDestination.SongDetails -> if (destination.importPreview != null) {
    destination.takeIf { hasImportReport }
} else if (destination.setlistFileName == null) {
```

and the web passes it as (`BrowserHistory.goForward`):

```kotlin
hasImportReport = viewModel.importReportState.importReport.value != null,
```

**Case 1 — an empty screen that never closes.** Open a review row's preview (`ImportReportState.openReviewPreview`), press
browser Back (the preview is popped, the import screen is on top), press the review's Import: the report goes
`Importing` → `Finished`, the import screen stays. Browser Forward: `validate` keeps both `ImportReport` and the preview
(a report exists), so the preview is pushed again. `rememberImportPreviewState` starts from a fresh `LastReview`, sees a
`Finished` report, and returns null:

```kotlin
val preview = destination.importPreview ?: return null
val review = lastReview.review ?: return null
```

so the screen has no songs, and its self-close is guarded off for previews (`SongDetailsScreen.kt`):

```kotlin
LaunchedEffect(resolvedSongs?.isEmpty()) {
    ...
    if (!isImportPreview && resolvedSongs?.isEmpty() == true && viewModel.navigator.backStack.lastOrNull() == destination) onBack()
}
```

(`resolvedSongs` is `importPreview?.songs`, null here, so even without the guard it would not fire). The user sees an
empty song screen addressed `/import` until they press Back.

**Case 2 — another review's songs.** Leave a review that had a preview open (browser Back twice), start a different import
that reaches a review, go Back to the songs, then Forward twice: `validate` keeps the old preview (a report exists), and
`rememberImportPreviewState` pages through the *new* plan with the old entry's `songIndices` / `planSongCount`
(`importPreviewSongIndices` only filters indices out of range), showing unrelated songs, with the page's Include and
Keep both / Replace bound to the new review's rows. The same holds for a single-song preview (`ImportPreview.SingleSong`)
gone forward into over a later single-song review: it shows that review's song, which is harmless only by luck.

Nothing is written by either case (Import still asks the live review), so the severity is low; the screen is wrong.

## Fix

Give each review an identity that survives its in-place updates, carry it in the preview, and keep (and draw) a preview
only over that very review.

1. `ImportReport.Review` (`screens/importReport/ImportReport.kt`) gains, as its last parameter,
   `val id: String = Uuid.random().toString()` (as `CampfireDestination.SongDetails.id` is generated; `kotlin.uuid.Uuid`).
   Every in-place change is a `copy` and keeps it: choices (`ImportReportState.updateChoices`), a joining batch
   (`ImportController.addToReview`'s `review.copy(plan = …)`), `restorePreview` (puts the same object back). The only
   construction is `ImportController`'s `val review = ImportReport.Review(` — nothing to change there. KDoc: what the id
   is for (a preview names the review it was opened over).
2. `ImportPreview` (`navigation/ImportPreview.kt`) gains `val reviewId: String`, required, so that no construction can
   forget it (no older encoding is ever decoded: the web keeps its history entries' states in memory, and a saved back
   stack is the same build's, its previews dropped on restore by `Navigator`). KDoc `@param reviewId`: the
   `ImportReport.Review.id` the preview was opened over. `SingleSong` becomes `fun singleSong(reviewId: String)` in the companion; update its callers
   (`ImportReportState.screenOf(report)`, which has the review) and every KDoc naming `ImportPreview.SingleSong`
   (`ImportReport.kt`, `ImportPreviewing.kt`, `screens/songDetails/CLAUDE.md`). `ImportReportState.openReviewPreview`
   passes `reviewId = review.id`.
3. `BrowserRoutes.validate` gains `reviewId: String?` — the id of the `ImportReport.Review` the import holds now, null for
   any other report or none — and keeps a preview only when
   `destination.importPreview.reviewId == reviewId` (`reviewId != null`). `hasImportReport` stays for
   `CampfireDestination.ImportReport`. Update the KDoc ("…and an import preview once the review it was opened over is
   not the one on screen"). `BrowserHistory.goForward` passes
   `reviewId = (viewModel.importReportState.importReport.value as? ImportReport.Review)?.id`.
4. `rememberImportPreviewState` takes a report as its review only when it is that preview's:
   `(report as? ImportReport.Review)?.takeIf { it.id == destination.importPreview?.reviewId }?.let { lastReview.review = it }`.
   The `Importing` / `Finished` reports that follow keep the last matching review drawn, as now.
5. A preview with no state closes itself, as a library song with no songs does — except while the report is
   `ImportReport.Importing`: a single-song preview composed afresh during its write (an Android activity recreated by a
   change `configChanges` does not cover, such as a dark mode switch, forgets `LastReview`) must stay until
   `followImportedSong` rewrites it, as today, rather than be popped, which `applyImportPlan` would take for the import
   screen being left. Put it in `ImportPreviewState.kt` as a small composable called from `SongDetailsScreen` next to the
   existing effect (keep that one as it is), so that the screen's composable does not grow (plan 01):
   ```kotlin
   @Composable
   internal fun CloseImportPreviewWithoutReview(viewModel: CampfireViewModel, destination: CampfireDestination.SongDetails, importPreview: ImportPreviewState?, onBack: () -> Unit) {
       val isOrphaned = destination.importPreview != null && importPreview == null
       LaunchedEffect(isOrphaned) {
           if (isOrphaned && viewModel.importReportState.importReport.value != ImportReport.Importing &&
               viewModel.navigator.backStack.lastOrNull() == destination
           ) onBack()
       }
   }
   ```
   with a `//` comment saying why (a preview gone forward into, or restored over, a review that is not its own has
   nothing to show) and KDoc. With step 4 this also catches what `validate` cannot see on other platforms. A preview on
   its first frame is never null while its review is up: `collectAsStateWithLifecycle` starts from the `StateFlow`'s
   value, and every way a preview is pushed (`show`, `showInPlace`, `openReviewPreview`) sets the review first, so the
   effect cannot close a preview being opened. A review-pager preview never meets `Importing` (`resolveImport` takes it
   off first); `restorePreview` puts the same `Review` object back, id included.

Recommended as above; an id is the simplest sound form — comparing plans breaks on a joining batch, and a counter in
`ImportController` would need the same plumbing as the id. Docs: one clause in `screens/songDetails/CLAUDE.md`'s import
preview section ("bound to the review it was opened over (`ImportPreview.reviewId`); without it the screen closes
itself"), and `navigation/CLAUDE.md` only if it states the `validate` rule.

## Tests

- `BrowserRoutesTest`: replace `a preview is addressed as the import is, and cut without a report` with one that keeps a
  preview over its own review id, and cuts it for `reviewId = null` (a `Finished` or no report) and for a different id;
  the `ImportReport` destination still follows `hasImportReport` alone. Update every other `validate` call for the new
  parameter.
- `ImportReportStateTest`: `openReviewPreview` and a single-song `show` / `showInPlace` push a preview carrying the
  review's id, and a review whose choices change keeps its id. Replace comparisons with `ImportPreview.SingleSong`
  (`ImportReportStateTest`, `ImportControllerQueueTest`'s `isSongPreview` helper and its assertion, `NavigatorTest`)
  by `isReviewPager == false` or by `singleSong(review.id)` as fits.
- `ImportPreviewingTest` and `BrowserRoutesTest` / `NavigatorTest` build `ImportPreview`s: pass a `reviewId`.
- The screen's self-close is composition: no unit test; `:tools:screenshots:test` must stay green.
