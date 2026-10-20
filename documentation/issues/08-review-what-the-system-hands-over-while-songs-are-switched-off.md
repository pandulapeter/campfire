<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Review a shared text and a lone song file handed over while Songs is switched off, instead of writing them at once

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** Android and iOS for a shared text; all for a lone song file
**Challenged:** amended — step 3 kept the editor's `areSongsEnabled()` guard and added a re-check of the switch to the review branch, since `!isReviewed` alone opened an editor on a screen the switch removes when Songs went off mid-read; added that test, made the settled/close assertions exact.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportOutcome.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportController.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportControllerQueueTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md` (shared with other plans),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/CLAUDE.md`

## Problem

With Settings → Features → Songs off, two ways in write a file into the library unreviewed, with nothing on screen that
says the song will stay out of sight.

**One shared text** (Android share, iOS share extension inbox). `PastedSongController.importSharedText` sends a lone
text down `enqueueImport(..., isNewSongFromText = true, ...)`. `ImportOutcome.kt:24-25` (at 396acf923):

```kotlin
internal val ImportRequest.isReviewed
    get() = shouldReview && !isNewSongFromText && !(shouldOpenSong && (source as? ImportSource.Files)?.files?.singleOrNull()?.let { isSingleSong(it.name) } == true)
```

so it is never reviewed up front, and `ImportController.prepare` (`ImportController.kt:299-317`):

```kotlin
val newSong = if (request.isNewSongFromText && areSongsEnabled()) plan.newSongOf() else null
if (newSong != null) {
    ...
    return newSong
} else if (isReviewed || plan.hasConflicts || request.isNewSongFromText && plan.loneSong == null) {
    // review
} else {
    applyImportPlan(plan = plan, choices = ImportChoices(), request = request, isReported = false)
}
```

With Songs off `newSong` is null and `plan.loneSong` is not, so a text that is one song with a free name falls through
to `applyImportPlan`: it is written at once, named from the share's subject or its first line (or "untitled"). The
editor's placeholders and "Save needs a title" never run, and the import screen's `ImportFeatureWarning.SONGS_OFF` card
("nothing imported here will show until you switch them back on") never shows. This goes against the root product rules
"nothing is written before Save" and "Imports are reviewed before writing". A share of two texts is reviewed, with the
warning.

**One song file** opened with the app, handed off, or dropped (`ImportController.importFiles`, `shouldOpenSong = true`).
`isReviewed` is false for it too, so with a free name it goes straight to `applyImportPlan`, and the snackbar it ends
in has no Open (`Messages.kt` keeps `convertedSongToOpen` only `takeIf { areSongsEnabled }`). A lone file skips the
review only because the song it holds opens at once, and with Songs off nothing opens. `ui/CLAUDE.md`'s Songs bullet
already says such files are imported "under a warning atop the import screen", which they are not.

The behaviour is pinned by the tests `with the songs switched off a pasted song is written and nothing is opened` and
`with the songs switched off a song handed over is written and not opened` (`ImportControllerQueueTest.kt:214`, `:229`).
So it may have been meant, hence the decision below.

## Fix

**Decision:** with Songs off, should a lone shared text and a lone song file handed over go to the import screen's
review (warning card, the user presses Import), or keep being written at once? Recommended: **review both.** The only
reason either skips the review is what happens next (the editor opens, or the song opens), and with Songs off neither
can. The review then shows the warning the import screen has for this case, and the lone-file half makes the code
match what `ui/CLAUDE.md` already claims. If the user keeps today's behaviour, drop this plan, but still correct
`ui/CLAUDE.md`'s Songs bullet so it doesn't promise a warning for files opened with or shared to the app.

If reviewed:

1. `ImportOutcome.kt`: turn the property into a function of the switch, and move the Songs-off case into its KDoc:

   ```kotlin
   internal fun ImportRequest.isReviewed(areSongsEnabled: Boolean) = shouldReview && (
       !areSongsEnabled || !isNewSongFromText && !(shouldOpenSong && (source as? ImportSource.Files)?.files?.singleOrNull()?.let { isSingleSong(it.name) } == true)
   )
   ```

   KDoc addition, in the file's voice: with the songs switched off nothing is opened and no editor is started, so what
   skips the review because it would be opened is reviewed after all, under the import screen's warning that it will
   not be seen. `shouldReview = false` (the demo library) stays unreviewed.
2. `ImportController.import`: `val isReviewed = request.isReviewed(areSongsEnabled = areSongsEnabled())`.
3. `ImportController.prepare`: a switch flipped while the file is being read must neither hang the queue nor open an
   editor with the songs off. Keep the editor guarded by the switch as it is now, and send what is not reviewed but
   would be reviewed under the switch as it now stands to the review:

   ```kotlin
   val newSong = if (request.isNewSongFromText && !isReviewed && areSongsEnabled()) plan.newSongOf() else null
   ...
   } else if (isReviewed || plan.hasConflicts || request.isReviewed(areSongsEnabled = areSongsEnabled()) ||
       request.isNewSongFromText && plan.loneSong == null
   ) {
   ```

   - Off at the start, on by the time the plan is in: `isReviewed` is true, so no new song is returned (returning one
     would leave the `Loading` report up with `activeRequest` set, and the queue would hang); the review comes up.
   - On at the start, off by the time the plan is in: `newSong` is null (pushing an editor with the songs off would put
     a switched-off screen on the stack), and `request.isReviewed(false)` is true, so the review comes up through the
     existing `reportState.show(review)` (not reviewed at the start, so no `Loading` was put up).
   - The switch unchanged, on: `request.isReviewed(true)` is the old property, false for these requests, so nothing
     changes. The demo library (`shouldReview = false`) stays unreviewed whatever the switch.
   `retryImport` passes `isReviewed = true`, which keeps a retried Songs-off batch in the review. Everything after it
   stays: `resolveImport` writes it, and `applyImportPlan` gives no `songToOpen` while `areSongsEnabled()` is false.
4. KDoc: update `ImportController`'s `@param areSongsEnabled` ("a file or a text sent to the app is then reviewed as
   any batch is, with nothing opened and no editor started on it") and `importFiles`' "a single song file is written
   without a review unless its name is taken" (add: while the songs are switched on).
5. `ui/CLAUDE.md`, Songs bullet: "Settings' Import, dropped files, and files opened with or shared to the app are still
   imported, under a warning atop the import screen" → also name the shared text: "…and files or a text opened with or
   shared to the app are still imported, always through the import screen's review and under a warning atop it…".
   `screens/importReport/CLAUDE.md`, "When it shows", second bullet: "Not reviewed: the demo library (`shouldReview =
   false`) and, while the songs are switched on, a single song file opened with, shared to, or dropped on the app…".

The iOS share extension's inbox lets go of a share once its `Deferred` settles, which for a reviewed batch is when the
review has been left. A two-text share already works this way, so nothing changes there.

## Tests

In `ImportControllerQueueTest.kt`, replace the two tests named above:

- `with the songs switched off a pasted song is reviewed and written only once imported`: `areSongsEnabled = false`,
  `plannedSongs = listOf(songEntry("new.cho", text = "[G]Hello"))`, `enqueuePaste()`, `runCurrent()`, then
  `Snapshot.sendApplyNotifications()` and `runCurrent()`. Assert `reportState.importReport.value is
  ImportReport.Review`, `importCount == 0`, and `newSongTexts` empty. Then `controller.confirmImport()`, `runCurrent()`,
  and assert `importCount == 1`, `openedSongs` empty, and the report closed and `settled` completed (see below).
- `with the songs switched off a song handed over is reviewed, written once imported and not opened`: the same with
  `handOver()`.
- Give the test class an `onPrepare: () -> Unit = {}` hook that the fake `prepareImport` calls before returning its
  plan; the switch then flips after `import` has decided on the review but before the plan is handled.
- `a switch turned on while a songs-off share is read still ends in its review`: `areSongsEnabled = false`, `onPrepare
  = { areSongsEnabled = true }`. Enqueue the paste, `runCurrent()`, `Snapshot.sendApplyNotifications()`,
  `runCurrent()`, and assert that the report is a `Review`, `newSongTexts` is empty, and `importCount == 0`; then
  `confirmImport()`, `runCurrent()`, and assert `importCount == 1` and `controller.isImporting.value` is false (nothing
  hangs).
- `a switch turned off while a share is read ends in its review, not in the editor`: `areSongsEnabled = true`,
  `onPrepare = { areSongsEnabled = false }`, the same steps; assert a `Review`, `newSongTexts` empty, no
  `CampfireDestination.SongEditor` on `backStack`, and `importCount == 0`.

For the first two tests, the clean-import path of a shown report closes it and announces with a snackbar
(`isReportShown && result.isClean`), so assert after `confirmImport()` and `runCurrent()` that
`CampfireDestination.ImportReport` is no longer on `backStack` and `settled.isCompleted`.

Keep `a new song from a share opens the editor with its suggestion` and the Songs-on tests unchanged; they must still
pass.
