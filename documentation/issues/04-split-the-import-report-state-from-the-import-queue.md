<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Split the import screen's state out of the import queue

**Kind:** structure · **Severity:** low · **Platforms:** all
**Challenged:** amended — the split leaves `ImportController.kt` at about 534 lines, not 470 (measured by deleting the
moved ranges from a copy), so the allowance is lowered rather than removed and the "move members to the bottom"
fallback, which shrinks nothing, is gone; `updateBackStack` leaves the controller too (its only users, `show` and
`close`, move), so the queue test's back stack lambda moves to the state; the controller's reads of
`isImportReportOnBackStack` are spelled out; `importReport/CLAUDE.md`'s `showImportReport` is re-pointed.
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/ImportController.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/ImportReportState.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/firstRun/FirstRunController.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/ImportControllerQueueTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/ImportReportStateTest.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/CLAUDE.md` (if it
names `ImportController` members that move),
`.github/scripts/size_allowances.json`

## Problem

`state/ImportController.kt` is 644 lines (on an allowance; the limit is 500) and its class KDoc lists six jobs: "the
queue the batches wait in, the download of a file from an address, the reading and comparing…, the review the import
screen holds, the writing, and what is said about it afterwards. Also the library's deletion". One of them is
self-contained: **the import screen's state** — the report it shows, its search field, whether it is on the back stack,
putting it up once nothing is in the way, the review's choices, keeping its file names following the library, and
saving whether an unwritten import was interrupted:

- `importReportSearch`, `_importReport` / `importReport`, `isImportReportOnBackStack`, `importReportRequest`
- `showImportReport(report)` (waits for no dialog, no unsaved editor, no onboarding, then pushes
  `CampfireDestination.ImportReport`), `closeImportReport()`
- `setImportEntriesChosen`, `setImportConflictResolution`, `setImportGroupConflictResolution`, `updateChoices`
- `followReportedFileNames`, `startReportingInterruption(savedStateStore)`
- `onBackStackChanged()` (tracks `isImportReportOnBackStack` and calls `onImportReportLeft()`)

None of these touch the queue, the preparation or the writing, except `onImportReportLeft()`, which cancels the
preparation or drops `activeRequest` depending on the stage — that half belongs to the import work.

Also a broken KDoc link: `showImportReport`'s KDoc says "(see [navigateBack])", which is `Navigator.navigateBack`.

## Fix

One commit: "Split the import screen's state out of the import queue." Behaviour preserved.

New `state/ImportReportState.kt`:

```kotlin
/**
 * What the import screen shows and whether it is up: the report from the files being read through the review to the
 * result, its search field, the review's choices, and the screen being put on the back stack once nothing is in its
 * way. The import work itself, which writes the report, is `ImportController`'s.
 */
internal class ImportReportState(
    private val scope: CoroutineScope,
    private val dialogHost: DialogHost,
    private val backStack: List<CampfireDestination>,
    private val updateBackStack: (SnapshotStateList<CampfireDestination>.() -> Unit) -> Unit,
    private val songTexts: StateFlow<Map<String, String>>,
    private val editorDraft: StateFlow<SongContent?>,
    private val hasUnsavedEditorText: () -> Boolean,
)
```

Members (moved verbatim with their KDoc unless said otherwise):

- `val search = SearchState(isInitiallyOpen = true)` (was `importReportSearch`; keep the old name if renaming touches
  more than the view model's delegation — the screen reads it as `viewModel.importReportSearch`).
- `private val _importReport` / `val importReport`, plus `var report: ImportReport?` (get/set `_importReport.value`)
  for `ImportController`'s writes, which stay exactly as they are, only spelled `reportState.report = …` instead of
  `_importReport.value = …` (every write left in the controller is a plain `.value =`; the two `.update { }` writers,
  `updateChoices` and `followReportedFileNames`, move). `awaitImportSettled` combines `reportState.importReport`.
- `var isOnBackStack` (private set; was `isImportReportOnBackStack`), `private var request` (was `importReportRequest`).
- `fun show(report: ImportReport)` (was `showImportReport`) and `fun close()` (was `closeImportReport`); fix the
  `[navigateBack]` link to `` `Navigator.navigateBack` ``.
- `setImportEntriesChosen`, `setImportConflictResolution`, `setImportGroupConflictResolution`, `private fun updateChoices`.
- `followReportedFileNames`.
- `fun startReportingInterruption(savedStateStore: SavedStateStore, messageSink: MessageSink)` (needs the sink for
  `Message.ImportInterrupted`; or take `messageSink` in the constructor).
- `fun onBackStackChanged(): Boolean` — updates `isOnBackStack`, clears the search field when the screen left
  (`search.textFieldState.clearText()`, the first line of today's `onImportReportLeft`), and **returns whether the
  screen has just left**. One-way: the state never calls the controller.

`ImportController` takes `reportState: ImportReportState` in place of `updateBackStack`, `songTexts`, `editorDraft` and
`hasUnsavedEditorText` (`showImportReport` was the only reader of the last three, and `showImportReport` and
`closeImportReport` the only callers of `updateBackStack`; it keeps `backStack`, which `applyImportPlan` watches for the
onboarding to leave). Every read of `isImportReportOnBackStack` left in it — `applyImportPlan`'s `isReportShown` and its
`catch` — becomes `reportState.isOnBackStack`; every `showImportReport(…)` / `closeImportReport()` becomes
`reportState.show(…)` / `reportState.close()`. It keeps:
`isImporting`, `isDeletingLibrary`, `importProgress`, `activeRequest`, the preparation, `importQueue`,
`queuedImportCount`, `importFiles` ×2, `importFromAddress` (unreachable today and kept on purpose: a planned shared-links
feature will use it), `enqueueImport`, `awaitImportSettled`, `import`, `prepare` ×2, `onPreparationCancelled`,
`cancelImportPreparation`, `retryImport`, `confirmImport`, `resolveImport`, `applyImportPlan`, `openImportReport` (it
checks `_isImporting`, then `reportState.show(…)`), `deleteLibrary`, `startQueue`, and:

```kotlin
/** Notes whether the import screen is still on the back stack, and lets go of what it asked once it is not. */
fun onBackStackChanged() {
    if (reportState.onBackStackChanged()) onImportReportLeft()
}
```

where `onImportReportLeft()` keeps today's `when` over the report (minus the search clearing, now done first by the
state, in the same order as today). The view model's listener `addOnBackStackChanged { _, _ -> importController.onBackStackChanged() }`
is unchanged, so its position among the listeners is unchanged.

**`deleteLibrary` stays in `ImportController`**, and the class KDoc says why in one sentence: deletion and import
exclude each other (the deletion refuses while `_isImporting` or `activeRequest` is set; the queue waits out
`isDeletingLibrary` before taking a batch), and both flags and both checks are only correct together, inside one class
whose state is touched on the main thread. The alternative, a `LibraryWriteGate` holding `isDeletingLibrary` and a
`tryClaimDeletion(isImportBusy: () -> Boolean)`, was considered and rejected: it would hold one flag, need the import's
own state passed back in as a lambda, and split one mutual-exclusion rule across two files.

In `CampfireViewModel.kt`: declare `importReportState` immediately before `importController` with the arguments
`importController` took for those parameters (`backStack = backStack`, `updateBackStack = { updateBackStack(update = it) }`,
`songTexts = songTexts`, `editorDraft = editorSession.editorDraft`, `hasUnsavedEditorText = { hasUnsavedEditorText() }`);
`importReport`, `importReportSearch`, `setImportEntriesChosen`, `setImportConflictResolution`,
`setImportGroupConflictResolution` and `followReportedFileNames` delegate to `importReportState`;
`importController.startReportingInterruption(savedStateStore)` in `init` becomes
`importReportState.startReportingInterruption(…)` **at the same position**. The navigator's `importReportSearch` lambda
reads `importReportState.search` (declared later than the navigator, read lazily — as today). `FirstRunController` reads
the report through a new `importReport: StateFlow<ImportReport?>` parameter (it uses `importController.importReport` in
`showWhatsNewOnVersionChange`), or keeps `importController` and the controller exposes no report — pick the parameter.

`ImportControllerQueueTest`: build an `ImportReportState` and pass it; the test's `updateBackStack` lambda moves from
the controller's constructor to the state's and keeps calling `controller.onBackStackChanged()` (which asks the state
first, so `reopenResult` still sees the screen pushed and `leaveImportReport` still drops the review), and its
`controller.importReport.value` assertions read `reportState.importReport.value`. If plan 01 has landed, `SongFilesController`'s `followReportedFileNames` lambda
points at `importReportState`.

Afterwards `ImportController.kt` is about 534 lines (644, less about 100 moved lines, the constructor's four
parameters and two `@param`s, and nine imports; plus the deletion sentence): still over 500, so run
`check_sizes.py --update`, which lowers its allowance to the new size rather than removing it. Do not reorder members to
chase the number. What is left is the import work proper; the next candidate, if the limit is ever wanted, is the
outcome routing at the end of `applyImportPlan` (the `when` and its `catch`, about 40 lines: where a written import's
result goes — the report, a snackbar, or the song it opens), which is a design change of its own and not part of this
plan. Check the move with `git diff --color-moved=dimmed-zebra`.

Docs: `screens/importReport/CLAUDE.md` and `dialogs/CLAUDE.md` mention the import's parking and the queue; update any
`ImportController.<member>` that moved (at HEAD, `screens/importReport/CLAUDE.md` names `showImportReport`, which becomes
`ImportReportState.show`; its `importReportSearch` is the view model's name and stays) (grep `ImportController\.` and `importReportSearch` in all `CLAUDE.md` files and
skills). No new CLAUDE.md.

## Tests

New `ImportReportStateTest` (desktop `runTest`, `DialogHost(backgroundScope)`, a `mutableStateListOf` back stack whose
`updateBackStack` also calls `state.onBackStackChanged()`, `MutableStateFlow`s for texts and draft):

- `a report is put up only once no dialog is in the way`: show a dialog, `show(ImportReport.Importing)`; the stack has
  no `ImportReport` until the dialog is dismissed, then it has one.
- `a report replaced before it could be put up is not pushed twice`: two `show` calls while a dialog is up; after the
  dismissal the stack holds one `ImportReport`.
- `leaving the screen clears its search and says so once`: push it, type into `search`, pop it; `onBackStackChanged()`
  returned `true` exactly once and the field is empty.
- `the review's group choice only touches the rows shown`: over an `ImportReport.Review`, `setImportGroupConflictResolution`
  with `isFiltered = true` changes only the given keys (`withGroupResolution` is tested already; this pins the wiring).
- `a finished report follows a rename and a deletion`: `followReportedFileNames` over `ImportReport.Finished(result)`.

`ImportControllerQueueTest`'s two tests must pass unchanged in substance.

Then the root CLAUDE.md test command, the four platform compile tasks named in the brief, `spotlessCheck`, and the size
check.
