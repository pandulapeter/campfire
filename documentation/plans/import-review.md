<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Import review — implementation plan

Written 2026-10-13 against `25e6152ea`, with the twenty-eighth review's lane O (the onboarding footer, the web's
Forward into a finished onboarding) landed; re-check `OnboardingBottomBar` and `BrowserRoutes.validate` against `HEAD`
before starting section 6. Since then the onboarding is owed until it is finished or skipped
(`UserPreferences.isOnboardingPending`, set by the first run, cleared by `FirstRunController.onBackStackChanged` as it
leaves the back stack), and every start up that owes it opens it (`openOnboardingOnStartUp`, formerly
`openOnboardingOnFirstRun`); section 6 is written against that. Today an import is a black box: the user picks a file, opens one with the app,
or drops one on the window, and everything inside it goes into the library, with a screen only where a name was taken
(the conflicts question) or something was left out (the result). This plan turns that screen into **the import screen
proper**: every import the user starts opens it first, in a loading stage while the files are read and compared, then in
a review that lists everything the file holds — songs, setlists, what is already there, what cannot be read — each
importable entry with a box to tick (all ticked to start with), the taken names marked and given a choice each, and a
summary of what Import will do. The import is written only from that screen's Import button. The same screen is built
to take a file from a web address (the upcoming shared-setlist links), which is where its loading stage, its failure
stage, and the Retry button come from.

## 1. What is being built

1. **Five stages of one screen** (`CampfireDestination.ImportReport`, which keeps its name; `ImportReport` gains
   `Loading` and `Failed` before `Review`, `Importing`, and `Finished`): reading, then the review, then the writing,
   then the result, each crossfading into the next in place, as the three existing stages already do.
2. **The review**: a summary card, the search field that is already there, and the entries grouped — **Names already
   taken** first (each row ticked, with a per-row choice between Keep both and Replace, and a "for all" choice on the
   group), then **Songs**, **Setlists**, **Already in your library** (no boxes: nothing to do), and the three groups of
   what cannot be imported (unsupported, too large, no readable text), as the result stage already lists them. Every
   group with boxes has a tri-state box in its header. The floating button says how many files Import writes and is
   disabled at zero; Replace still asks the existing confirmation.
3. **Per-entry choices reach the writer**: `ImportFilesUseCase` takes an `ImportChoices` (which entries are left out,
   and which taken names are replaced rather than numbered) in place of the one `ImportConflictResolution` for the
   batch. Unticking a conflicting row is what "Skip" was.
4. **A source that is an address**: `ImportSource.Address(url)` next to `ImportSource.Files`, downloaded by a new
   remote source in `:data:source:remote` (the only module that may make a request) through a repository and a use
   case, with the same size ceiling as a picked archive. Nothing in the app calls it yet; the links feature will. The
   loading stage shows the download, the failure stage says why it failed, and Retry asks again.
5. **The onboarding goes first, and the import screen follows it**: a file the app was opened with on its first start
   no longer takes the onboarding away. The onboarding's last button reads Next while an import waits, and the import
   screen slides in as the onboarding closes.
6. **The progress dialog goes**: the screen shows the progress of every import it is about, which is now every import
   the user started. What the demo library's silent import and a single song opened with the app do is unchanged.

### What is already there

- **The screen, its stages, its list, and its search** (`ui/screens/importReport/`): `ImportReportScreen` crossfades
  `Review` / `Importing` / `Finished` by stage, `ReportList` is the centered lazy list with the fade that gives way to
  the pinned `searchField`, `sections` draws grouped rows (`ImportReportSection`, `ImportReportRow`,
  `importReportSections` and `matching` in `ImportReportSections.kt`, pure), `SectionTitle` and `ReportNote` are the
  headings and the notes, and the floating `ImportReportButton` is already an `AnimatedContent` over the stage. The
  search (`ImportController.importReportSearch`) is open for good and emptied as the screen is left.
- **The plan is the whole content**: `ImportPlan` carries every song's text and planned name, every setlist as parsed,
  each entry's `status` (`NEW` / `IDENTICAL` / `CONFLICTING`), the library file a conflict would replace
  (`replacesFileName`), which entry a repeat follows (`repeatedEntryIndex`), whether a song was converted, and the three
  lists of what is left out. The review needs nothing the preparation does not already compute, except each song's
  title and artist, which `PrepareImportUseCaseImpl` reads the header for anyway (`songRepository.importFileName`).
- **The question is already parked behind the screen** (`ImportController.pendingImport`, `showImportReport`,
  `onImportReportLeft`): the plan outlives the screen it was started from, waits for a dialog or an unsaved editor to
  clear before the screen is pushed, and is dropped however the screen leaves. The queue (`importQueue`,
  `awaitImportSettled`) already makes the next batch wait for the screen.
- **The writer already decides per entry** (`ImportFilesUseCaseImpl.Action`: `WRITE` / `REPLACE` / `DISREGARD` /
  `LEAVE_ALONE`) and already handles a setlist whose song was not written (`withSongFileNames` leaves an unmapped
  name as it is, which the setlist then shows as a missing song). Only the function that turns a status into an action
  reads the batch-wide answer.
- **The download has a model to copy**: `CoverArtRemoteSourceImpl` over `HttpClientHolder` follows redirects, checks
  the status and the length, reads one byte past its ceiling, and never throws but for a cancellation;
  `CoverArtDownload` is the answer shape, `CoverArtRemoteSourceTest` the test harness with a mock engine.
- **The onboarding is one screen with a footer** (`OnboardingScreen`, `OnboardingBottomBar(steps, currentStep, isLast,
  isOpenedFromSettings, onBack, onClose, onNext)`, whose `lastLabel` is already Done or Start playing by
  `isOpenedFromSettings` and whose `AnimatedContent` over `isLast` already crossfades the label;
  `Navigator.openOnboarding` / `closeOnboarding`), and `canShowOnboarding` in `ui/firstRun/WhatsNewGate.kt` is the one
  place it is held back for an import.
- **The web's address** `/import` exists (`BrowserRoutes.IMPORT`), is never restored without a report
  (`validate(hasImportReport)`), and the onboarding is an entry at the address under it.

### Decisions — veto any of them before the work starts

1. **The import screen follows the onboarding rather than being a page of it.** The review has its own list, search,
   floating button, and stages, and the onboarding's pager takes no swipes and draws one footer under every page; a
   page holding a whole screen would fight both. So: the onboarding opens on a first run whether or not a file is
   waiting, the import's reading runs behind it, `showImportReport` waits for the onboarding to leave the back stack
   as it waits for a dialog, and the onboarding's last button reads **Next** instead of Start playing while an import
   waits (Skip stays Skip, and leads to the same place). The onboarding opened again from Settings is waited for the
   same way. Today's third option — skipping the onboarding for the import — is dropped: it was the one way a new user
   did not see it on their first start (today it stays owed and opens on the next start instead).
2. **The download is built now, reached by nothing.** `ImportSource.Address`, the remote source, the repository, the
   use case, and `CampfireViewModel.importFromAddress(url)` land with their tests, so the loading and failure stages
   are driven by real code rather than by a placeholder, and the links feature only has to call one function. The
   alternative — the sealed type alone, the download later — leaves Retry with nothing to retry but a re-read of the
   same bytes.
3. **Conflicts: a choice for all, and a choice per row.** The group header of Names already taken has a dropdown that
   sets every conflicting row (Keep both, the default, or Replace); each row has the same dropdown, and a row set by
   hand keeps its choice when the header's changes. Unticking a row is what Skip was, so `ImportConflictResolution`
   loses `SKIP`. Three hundred questions is still not a choice: the header answers them all, and the rows are there for
   the two or three the user actually looked at.
4. **A single song opened with the app still opens** (`shouldOpenSong`, `songToOpen`): a batch the system handed over
   that is one plain song file — not an archive, not a document — is written without a review and opened, as today,
   and reviewed only where its name is taken (one row, the two choices). Everything else the user starts — the
   picker, a drop, an archive, several files, a document — is reviewed. A song picked from the picker's own Import
   files is reviewed too: one row and an Import, which is what the user asked to see.
5. **The demo library is never reviewed**: the first run plants it silently as today, and the Settings offer imports it
   as today (ending in the snackbar), since what it holds is known and asked for by name.
6. **A reviewed import that went clean closes the screen and says so in the snackbar** (with Details, which reopens
   `Finished`): the user has just read the list, and reading it again as a result is one tap more than it is worth.
   One that left something out, stopped, or failed stays on the screen in `Finished`, as today.
7. **The progress dialog is removed** (`ImportProgressDialogHost`, and the desktop Escape line that knew about it).
   While the screen cannot be pushed yet — a dialog up, an editor with unsaved text on top, the onboarding — the
   reading runs unseen and the screen arrives once the way is clear; the list screens' `isImporting` bar is unchanged.
   The one thing lost is the progress of a file dropped onto an editor holding unsaved text, which is the question
   that already waits there.
8. **Nothing is restored**: a review is lost with the process (the bytes are in memory), as the question is today;
   `ImportReport` is still filtered out of a restored back stack. The choices live in the view model, so an Android
   activity recreated under the review keeps every tick.
9. **No new feature switch, and no new line in the root `CLAUDE.md`'s network sentence yet**: the download is reached
   by nothing until the links feature, which adds the line when it adds the entry point.

## 2. `:data:model`

- `ImportPlan.SongEntry` gains `title: String` and `artist: String`, what the header says (the fallback title where the
  header says nothing, which is what names the file). `ImportPlan` gains `sourceNames: List<String>`, the names of
  the picked files in the order `origin` indexes them, for the screen's subtitle ("From campfire_library.zip"). Its
  `Summary` goes: the review's counts are a pure function in `:presentation` (section 5.3).
- `ImportConflictResolution` keeps `KEEP_BOTH` and `REPLACE`; `SKIP` is removed.
- `ImportChoices` (new file `domain/ImportChoices.kt`): what the review answered.

  ```kotlin
  data class ImportEntryKey(val kind: Kind, val index: Int) { enum class Kind { SONG, SETLIST } }

  data class ImportChoices(
      /** Entries the user unticked; a repeat of one of them follows it out. */
      val leftOut: Set<ImportEntryKey> = emptySet(),
      /** What every conflicting entry without a choice of its own gets. */
      val conflictResolution: ImportConflictResolution = ImportConflictResolution.KEEP_BOTH,
      /** The rows the user set by hand, kept when [conflictResolution] changes. */
      val conflictResolutions: Map<ImportEntryKey, ImportConflictResolution> = emptyMap(),
  ) {
      fun resolutionOf(key: ImportEntryKey) = conflictResolutions[key] ?: conflictResolution
      fun isChosen(key: ImportEntryKey) = key !in leftOut
  }
  ```

  Tested in `commonTest` (`ImportChoicesTest`): the default, a per-row choice surviving a change of the default, a
  per-row choice equal to the new default still kept.
- `ImportProgress.Phase` gains `DOWNLOADING` first; `ImportResult.skippedConflictingFileNames` becomes
  `leftOutFileNames`, holding every entry the choices left out, a conflict or not.
- `data/model/CLAUDE.md`: the `ImportPlan` paragraph names the two new fields and `ImportChoices`.

## 3. `:domain`

### 3.1 `PrepareImportUseCaseImpl`

Fills `title` and `artist` on each `ImportPlanner.IncomingSong` → `SongEntry` from the same header read the file name is
derived from (through `:chordpro`'s metadata reader, which the use case may call directly, as it calls the prettifier
and the splitter), the fallback title standing in where the header names none, and `sourceNames = files.map { it.name }`.
`PrepareImportUseCaseImplTest` checks both on an archive of two songs and a loose file.

### 3.2 `ImportFilesUseCase` takes the choices

```kotlin
suspend operator fun invoke(plan: ImportPlan, choices: ImportChoices, onProgress: (ImportProgress) -> Unit = {}): ImportResult
```

In `ImportFilesUseCaseImpl`, `action(status, resolution)` becomes `action(key, status)` over the choices:

- `key in choices.leftOut` → `LEAVE_ALONE`, whatever the status (a `NEW` song left out is written nowhere; an
  `IDENTICAL` one cannot be left out, since the review offers it no box — the function treats it as `DISREGARD`
  regardless);
- `NEW` → `WRITE`; `IDENTICAL` → `DISREGARD`;
- `CONFLICTING` → `WRITE` for `KEEP_BOTH`, `REPLACE` for `REPLACE`, from `choices.resolutionOf(key)`.

The repeat rule is already there (`leftAloneIndices`: a repeat of a left-out entry is left out with it); the setlist
rule is already there (a setlist written while its song was left out keeps the source name, which the setlist shows as
a missing song); the replan rule is already there (a setlist the question was not about goes in numbered). `result()`
fills `leftOutFileNames` from `skippedConflicts`, renamed. `ImportFilesUseCaseImplTest` gains: a `NEW` song left out is
not written and is named in `leftOutFileNames`; a setlist whose one song was left out is written naming the source
file (a missing entry); one conflicting row replaced and the other kept both in one batch; a repeat of a left-out song
is left out with it.

### 3.3 The download

- `:data:source:remote:api`: `ImportDownloadRemoteSource.download(url: String, maxSizeBytes: Long): ImportDownload`,
  with `ImportDownload` in `model/`: `File(name: String, bytes: ByteArray)`, `TooLarge`, `Missing` (a non-2xx that
  is not transient, or no body), `Unreachable` (no connection, a timeout, 408, 429, 5xx, a refused CORS check). Only a
  cancellation is thrown.
- `:data:source:remote:implementation`: `ImportDownloadRemoteSourceImpl`, a copy of the cover download's shape:
  `prepareGet(url).execute { … }`, redirects followed by the client, a `Content-Length` over the ceiling answered
  `TooLarge` before the body is read, otherwise `readRemaining(max + 1)` and `TooLarge` past it. The name is from
  `Content-Disposition` (`filename*=` decoded, then `filename=`, in `importDownloadFileName(header, url)`, a pure
  function in a file of its own), else the last segment of the address's path, else `download` — the import then
  triages it by its name or, for a name it does not know, by its bytes (`LibraryFiles.isZipArchive`), which is what
  makes a Dropbox link with no extension work. No content type check: a share link's type is whatever the host says.
  `ImportDownloadRemoteSourceTest` beside the cover's: each outcome, the name from each of the three places, the
  ceiling from the header and from the body.
- `:data:repository:api` / `:implementation`: `ImportDownloadRepository.download(url): ImportDownload`, a line over the
  source with `ImportLimits.MAX_IMPORT_SIZE`, since use cases see repositories only.
- `:domain:api` / `:implementation`: `DownloadImportFileUseCase(url): ImportDownload`, `@Factory`, a line over the
  repository.
- Koin: `@Single` on both implementations; `:app:di`'s compile-time check covers the wiring. `CLAUDE.md` of all four
  modules gain one line each; `data/source/remote/implementation/CLAUDE.md` says the download is asked for one address
  the user opened, once, and keeps nothing.

### 3.4 Docs

`domain/implementation/CLAUDE.md`: the "An import decides before it writes" section now says every import the user
starts is reviewed first, that the review's choices are per entry (`ImportChoices`), and that replacing takes a choice
on the screen and a confirmation after it. The root `CLAUDE.md`'s product rule line becomes: *An import is reviewed
before it writes: everything the files hold is listed to be ticked, what is already there is disregarded, what collides
is numbered or, by choice and after a confirmation, replaced.*

## 4. The controller (`ui/state/ImportController.kt`)

### 4.1 Sources and requests

```kotlin
sealed interface ImportSource {
    data class Files(val files: List<ImportedFile>) : ImportSource
    data class Address(val url: String) : ImportSource
}
```

`ImportRequest.files` becomes `source: ImportSource`, still emptied — `Files(emptyList())` — once a preparation has
succeeded, and kept while the screen is in `Failed`, so that Retry has something to retry. `enqueueImport` gains
`shouldReview: Boolean = true`, false for the demo library (both ways), and `importFromAddress(url)` enqueues an
`Address` source with `shouldOpenSong = true` (a link to one song opens it, like a file). `CampfireViewModel` gets the
facade line.

### 4.2 The stages

```kotlin
sealed interface ImportReport {
    /** The files being read, or the address being downloaded; [sourceName] titles the screen's subtitle. */
    data class Loading(val sourceName: String?) : ImportReport
    data class Failed(val reason: ImportFailure, val sourceName: String?) : ImportReport
    data class Review(val plan: ImportPlan, val choices: ImportChoices) : ImportReport
    data object Importing : ImportReport
    data class Finished(val result: ImportResult?) : ImportReport
}

enum class ImportFailure { UNREACHABLE, NOT_FOUND, TOO_LARGE, UNREADABLE }
```

`import(request)`:

1. Decide whether this batch is reviewed: `request.shouldReview && !(request.shouldOpenSong && source is Files &&
   files.size == 1 && !LibraryFiles.isArchiveFileName(name) && name is a song's extension)`. A reviewed batch sets
   `_importReport = Loading(sourceName)` **at once** — which is what keeps the queue and What's new waiting, as the
   question did — and calls `showImportReport`, whose gate gains `stack.none { it is CampfireDestination.Onboarding }`.
2. For an `Address`: `_importProgress = ImportProgress(DOWNLOADING)`, `downloadImportFile(url)`; `TooLarge` →
   `Failed(TOO_LARGE)`, `Missing` → `Failed(NOT_FOUND)`, `Unreachable` → `Failed(UNREACHABLE)`; a `File` becomes the one
   `ImportedFile` of a `Files` source and goes on. The download runs as the preparation does — a sibling `async` in
   `scope`, cancellable from `cancelImportPreparation`, which the loading stage's Cancel and the screen's leaving call.
3. Prepare as today. A failure (today's "Import failed" snackbar) → `Failed(UNREADABLE)` on a reviewed batch, the
   snackbar on an unreviewed one. A cancelled preparation clears the report, as it clears the progress today.
4. A plan on a reviewed batch → `Review(plan, ImportChoices())`; `_isImporting = false` (nothing happens while the
   review is up, as with the question). On an unreviewed batch: a conflict → `Review` as today (the screen pushed now),
   otherwise `applyImportPlan(plan, ImportChoices(), …)` as today.

`retryImport()`: from `Failed`, back to `Loading` and step 2 or 3 again. `cancelImportPreparation` is unchanged in
what it does and is also what `onImportReportLeft` calls for a `Loading` report; a `Failed` report left drops the
request (and settles it). `onImportReportLeft` for `Review` is as today for the question.

### 4.3 The choices

`setImportEntryChosen(key, isChosen)`, `setImportEntriesChosen(keys, isChosen)` (a group's box, over the rows the
screen shows — the filtered ones while the search narrows them), `setImportConflictResolution(key: ImportEntryKey?,
resolution)` (null for the header's "for all", which also clears every per-row choice equal to it? No: a per-row choice
is kept whatever the header says, see decision 3 — the header writes `conflictResolution` only). Each is one `update`
of `_importReport` while it is a `Review`, ignored otherwise.

`confirmImport()`: from `Review`, counts the ticked conflicting entries whose resolution is `REPLACE`; more than zero →
`dialogHost.show(DialogType.ConfirmImportReplace(count))`, whose confirm calls `resolveImport()`; zero → `resolveImport()`
directly. `resolveImport()` (no parameter now) is today's: `_isImporting = true`, `Importing`, `applyImportPlan(plan,
choices, request, isReported = true)`. `pendingImport` goes: the plan and the request are the `Review` report and the
request kept by the controller (`activeRequest`), which `deleteLibrary` refuses on while the report is anything before
`Finished`.

`applyImportPlan`'s outcome table gains decision 6: `isReportShown && result.isClean && !result.isFailed` → let go of
the report, `closeImportReport()`, `Message.ImportFinished(result, hasDetails = true)`; `isReportShown` otherwise →
`Finished(result)` as today. `isClean` counts `leftOutFileNames` as not clean, so an import the user trimmed stays on
the screen with the left-out group listed — which is the one group the user wrote themselves, so this is a decision to
revisit if it reads as noise.

### 4.4 What goes

`ImportProgressDialogHost.kt` and its call in `CampfireDialogs.kt` (`ImportProgressDialogHost(progress = importProgress
.takeIf { importReport == null }, …)`); `isCancellable` moves next to `ImportProgressContent` (the loading stage uses
it to show Cancel); the desktop's `CampfireDesktopApp.kt` line `if (importProgress.value != null && importReport.value
== null) return true` and its comment; `ImportPlan.Summary`; `ImportConflictResolution.SKIP` and the three Skip strings.

## 5. The screen (`ui/screens/importReport/`)

### 5.1 Stages

- **Loading**: title "Reading the files" ("Downloading" for an address, from the phase), the subtitle naming the
  source, `ImportProgressContent` centered as `Importing` draws it, and a Cancel text button under it while the phase
  `isCancellable` (every phase here is). The bar's Close does the same. No floating button.
- **Failed**: title "Import failed", a line per reason (`import_failed_unreachable` naming the host through
  `textResource`, `import_failed_not_found`, `import_failed_too_large` naming 24 MB as the export's message does,
  `import_failed_unreadable`), and the floating button **Try again** (`ic_refresh`); Close leaves.
- **Review**: title "Review the import" ("Nothing to import" where the plan has no entry with a box), the subtitle
  naming the source, the list of 5.2, the floating button **Import N files** (a plural, `import_review_confirm`),
  disabled while nothing is ticked and absent where there is nothing to tick; `onImport` calls `confirmImport`.
- **Importing** and **Finished**: as today; `Finished`'s section for `leftOutFileNames` is titled "Left out by your
  choice" (`ImportReportSection.Kind.LEFT_OUT`, replacing `SKIPPED_CONFLICTS`).

The `AnimatedContent` keeps `contentKey = { it::class }`, so a tick never crossfades the list.

### 5.2 The review list

One `ReportList` with the header items before the pinned search field (`REVIEW_HEADER_ITEM_COUNT` counts them), then
`reviewSections`:

1. **The summary card** (`ImportReviewSummaryCard`, a `Card` of `bodyLarge` lines built from `ImportReviewSummary`,
   5.3): what Import will do with the ticks as they stand — "Songs: 120 · Setlists: 3", "Replacing: 2", "Numbered next
   to the ones you have: 5", "Already in your library: 40", "Left out by you: 7", and the three left-out counts as
   `ReportNote`s where they are not zero. It recomposes per tick; the rows do not (5.4).
2. **Names already taken** (`Kind.CONFLICTS`): `SectionTitle` with the count, a tri-state `Checkbox` at its end, and a
   second line holding the "for all" dropdown (`ConflictResolutionDropdown`: an `AssistChip`-like outlined button
   reading the current choice with a chevron, opening a `DropdownMenu` of Keep both / Replace, each with its one-line
   description from the existing strings). Each row: a leading `Checkbox`, the headline the incoming song's
   `Artist - Title` (the setlist's title), the supporting line what will happen ("Will be numbered next to the one you
   have" / "Will replace the one you have: *Library title*", the library's song or setlist looked up by
   `replacesFileName ?: fileName` in `allSongs` / `setlists`), and the same dropdown trailing, with a third entry **Show
   the one you have** for a song, which opens the library's song through `openReportedSong` and comes back. A tap on
   the row toggles its box.
3. **Songs** (`Kind.SONGS`) and **Setlists** (`Kind.SETLISTS`): the header's tri-state box, rows with a leading box,
   `Artist - Title` (a converted one adds "Converted, worth a look in the editor" as today; the editor button is not
   offered here, since the song is not in the library yet) and, for a setlist, "N songs" (a plural) with ", K of them in
   this import" where that number differs. A tap toggles.
4. **Already in your library** (`Kind.DUPLICATES`): rows named as the library names them (looked up by `fileName`),
   no box, a tap opening the library's song as the result stage does; a repeat inside the batch
   (`repeatedEntryIndex != null`) is not listed at all — it follows the entry it repeats, and the row of that entry
   gets "Appears twice in the files" (a plural) as a supporting line.
5. **Unsupported or invalid files**, **Files over the import size limit**, **Documents without readable text**: the
   result stage's three info groups, unchanged.

Rows are keyed `"${kind}_${key.kind}_${key.index}"`, so a key is unique however many entries share a name, and the
existing `toRows` numbering is not needed for them. `matching` filters the review's rows by file name, title, and
artist as it does the result's. Ctrl / Cmd + F, Escape, and Back behave as today.

### 5.3 Pure and tested (`ImportReviewSections.kt`, beside `ImportReportSections.kt`)

- `ImportReviewRow(key: ImportEntryKey?, fileName, title, subtitle, isSong, isConverted, libraryFileName: String?,
  repeatCount: Int)` — `key` null for the rows without a box.
- `importReviewSections(plan, songs, setlists): List<ImportReportSection>` over a row type the two share, or a
  `ImportReviewSection` of its own holding `ImportReviewRow`s — the latter, so that a review row cannot be handed to the
  result's `sections`.
- `ImportReviewSummary(songCount, setlistCount, replacedCount, numberedCount, alreadyThereCount, leftOutCount,
  skippedCount, oversizedCount, unreadableCount)` from `importReviewSummary(plan, choices)`.
- `replaceCount(plan, choices)` — the ticked conflicting entries resolving to `REPLACE`, which the confirmation names.
- `groupBoxState(rows, choices): ToggleableState` — all, none, or some of a group's shown rows ticked.

`ImportReviewSectionsTest`: a plan of two new songs, one conflicting, one identical, one repeat, one setlist naming
one of them; the summary for every tick combination that matters (all ticked, a conflict unticked, a conflict replaced,
the setlist alone); the group state; the search narrowing the group and the box acting on what is shown.

### 5.4 Performance

The sections are `remember(plan, songs, setlists)`, the match `remember(sections, query)`; neither reads the choices.
Each row reads `choices.isChosen(key)` and `choices.resolutionOf(key)` from the report the screen collects, so a tick
recomposes that row and the summary card and nothing else. A plan of three thousand songs is a list the lazy column
never lays out whole, as the result stage's is today.

### 5.5 Strings (`values/strings.xml` and `values-hu/strings.xml`, both)

New: `import_loading_title`, `import_progress_downloading`, `import_failed_unreachable` (`%1$s` the host),
`import_failed_not_found`, `import_failed_too_large`, `import_failed_unreadable`, `import_retry`, `import_review_title`,
`import_review_title_empty`, `import_review_from` (`%1$s`, read with `textResource`), `import_review_confirm`
(plurals), `import_review_summary_counts` (`%1$d` songs, `%2$d` setlists), `import_review_replacing`,
`import_review_numbered`, `import_review_already_there`, `import_review_left_out` (all plurals), `import_review_songs`,
`import_review_setlists`, `import_review_conflicts`, `import_review_for_all`, `import_review_will_be_numbered`,
`import_review_will_replace` (`%1$s`, `textResource`), `import_review_show_library_song`, `import_review_setlist_songs`
(plurals), `import_review_setlist_songs_in_import` (`%1$d`), `import_review_repeated` (plurals), `import_status_left_out`.
Removed: `import_conflicts`, `import_conflicts_summary`, `import_conflicts_new`, `import_conflicts_duplicates`,
`import_conflicts_skipped`, `import_conflicts_question`, `import_conflicts_apply_all`, `import_conflicts_skip`,
`import_conflicts_skip_description`, `import_conflicts_files`, `import_conflicts_confirm`, `import_status_skipped_conflicts`,
`import_progress_wait` if nothing reads it after the dialog goes. Kept: `import_conflicts_keep_both*`,
`import_conflicts_replace*`, `import_replace_title`, every `import_status_*` and `import_report_*` the result still uses.

## 6. The onboarding and the first run

- `canShowOnboarding(hasDialog)` loses `hasImportReport` and `hasQueuedImport`; `openOnboardingOnStartUp`'s KDoc and
  `ui/firstRun/CLAUDE.md`'s "only where no file the app was opened with is waiting" sentence turn around, and so does
  its "a first run that had to skip it for an import" case of an owed onboarding, which no longer happens: the
  onboarding opens, the queue starts after it (as it already does, on `demoLibraryDecision`), the import's reading runs
  behind it, and the screen is pushed as the onboarding leaves. That holds on any start up that owes the onboarding,
  not only the first: one opened with a file after the app was ended in the middle of the pages opens the owed
  onboarding again and pushes the import screen after it. `isOnboardingPending` itself is unchanged — the onboarding
  leaving the back stack before the import screen is pushed is what clears it, so an app ended on the review does not
  bring the onboarding back.
- `OnboardingScreen` collects `viewModel.importReport` and hands `isImportWaiting = report != null` to
  `OnboardingBottomBar`, whose `lastLabel` is `onboarding_next` while it is true, before the Done / Start playing
  choice (the `AnimatedContent` over `isLast` is keyed on the label's resource, so the swap crossfades too).
  `ui/screens/onboarding/CLAUDE.md`'s "There is no summary page" paragraph says so.
- `BrowserRoutes.validate` already keeps an onboarding only while it is the open one (`openOnboarding`) and drops the
  import screen without a report; a Forward into `/import` after the review was left is refused by what is there.
- `showWhatsNewOnVersionChange` is unchanged: it waits for every import, a first run has no What's new, and a start up
  that owes the onboarding records the version without showing it.
- The web: the onboarding is one history entry at `/`; `/import` is pushed after it. `BrowserHistory` already passes
  `hasImportReport`, so Forward into a left review is refused as today.

## 7. The platforms

Nothing changes in how files arrive: every shell's `filesToImport` flow, the pickers, the drops, and the Android intent
channel hand `ImportedFile`s over as today, and `CampfireApp` collects them into `importFiles`. What changes per
platform:

- **Android**: a rotation under the review keeps the choices (the controller's); the Replace confirmation is a
  `ConfirmationDialog` and is not a `SavedDialog`, as today. A process restored under the review comes back on the
  screen under it, as today.
- **Desktop**: `app/desktop/CLAUDE.md`'s exit paragraph — "a conflicts question that is up is dropped" — becomes "an
  import that has not started writing (reading, failed, or under review) is dropped". The Escape special case for the
  progress dialog goes (4.4).
- **Web**: a `Failed(UNREACHABLE)` is also what a host without CORS headers comes to; the message is the same, and the
  links feature decides whether to say more. `beforeunload` is not registered for a review (nothing typed is lost).
- **iOS**: nothing of its own.

## 8. Corner cases, and what each one does

- **A second batch while the screen is up** (another file opened with the app, a drop) waits in the queue until the
  screen is left in any stage, as today. A file opened with the app while the onboarding is up on a first run waits
  behind it with the first one, and the two screens follow one another.
- **A plan with nothing to tick** (everything already there, or nothing importable): the review opens titled "Nothing
  to import" with its info groups and no floating button; Close leaves; nothing is written and nothing is announced.
- **A setlist whose songs are unticked** is still written and shows them as missing songs, which its row's supporting
  line warned about with "K of them in this import"; a setlist unticked while its songs are ticked leaves the songs
  alone. Neither is prevented: both are things somebody may want.
- **A conflicting setlist** has the same two choices as a song; a setlist that becomes a conflict only once the songs'
  names are known (`replanSetlists`) goes in numbered whatever was chosen, as today.
- **The library changes under the review** (a sync run, a rescan on the desktop or iOS): the plan is held against the
  library as it was; an identical song whose file went is written after all, a replacement writes over the name the
  user confirmed, and a row naming a library song that is gone falls back to its file name. Delete library is refused
  while the review is up (`deleteLibrary` checks the report's stage).
- **Retry after a failed download** asks the same address again; after a failed read it prepares the same bytes again,
  which fails the same way unless the failure was transient (the web's storage worker, an out-of-memory). Both are
  honest: the button says Try again, not Fix it.
- **Cancel during a download** cancels the request; the host may have sent part of the body, which is dropped.
- **Leaving the screen in `Loading`** cancels; in `Failed` drops the request; in `Review` drops the plan; in
  `Importing` lets it finish and ends in the snackbar; in `Finished` is done — `onImportReportLeft`, stage by stage.
- **Performance mode**: files the system handed over are still reviewed and imported; the review offers no editor.
- **The import's size**: the review holds the plan's texts (what the question held already); the picked bytes are let
  go of once the plan exists, as today, and only a `Failed` stage keeps them.
- **A very long list**: the group's tri-state box acts on the rows the search shows, so "untick all" under a search for
  "live" unticks the live versions only; the summary says what is ticked overall.

## 9. Order of work

1. `:data:model`: `ImportChoices`, the entry fields, `sourceNames`, `DOWNLOADING`, the renames; tests.
2. `:domain`: `PrepareImportUseCaseImpl` fills the fields; `ImportFilesUseCase` takes the choices; tests. Every caller
   passes `ImportChoices()` for now, so the app builds and behaves as before.
3. The download: source, model, repository, use case, Koin, tests.
4. `ImportController`: sources, stages, retry, choices, `confirmImport`, the gate, the clean-close rule; the dialog
   host, the desktop line, and `pendingImport` removed; `CampfireViewModel`'s facade lines.
5. The screen: `ImportReviewSections.kt` with its test, then the three new stages, the dropdowns, the summary card,
   the floating button, `LEFT_OUT`; the strings in both languages.
6. The onboarding handover; `canShowOnboarding` and its test.
7. Every `CLAUDE.md` named above (`ui/screens/importReport/CLAUDE.md` rewritten around the five stages, `ui/CLAUDE.md`'s
   "parked behind the import screen" paragraph, `ui/dialogs/CLAUDE.md`'s progress dialog section removed and its
   `ConfirmImportReplace` sentence updated, `presentation/CLAUDE.md`'s first paragraph), `spotlessApply`, the unit
   tests, and every platform's compile.

## 10. Checks owed by hand

- A zip of a whole library on each platform: the loading stage, the review's groups and counts, unticking a group,
  Import, the snackbar with Details, the library.
- The same zip again: everything under Already in your library, "Nothing to import", Close.
- Two edited songs in a zip whose names are taken: both choices, the confirmation's count, the result; one of them
  unticked instead, listed under Left out by your choice.
- A single `.cho` opened with the app (Android, iOS, desktop) opens the song without a review; the same file with its
  name taken shows the one-row review.
- A fresh installation (Android emulator at 360 × 640 dp, iOS simulator) opened with a zip: the onboarding, Next on its
  last page, the review sliding in; the same with Skip. Then the app ended in the middle of the onboarding and opened
  with a zip again: the onboarding first, the review after it.
- A rotation under the review keeps the ticks; a dialog over it (the confirmation) and Back behave.
- `importFromAddress` from a debug hook or the desktop driver: a Dropbox `?dl=1` link to a zip, a 404, airplane mode,
  a link to a 30 MB file; Retry after each.
- The web build: a dropped folder, the review, Back on the browser, Forward refused.
- Ctrl / Cmd + F into the review's search; Escape leaving it.

## 11. Not in this plan

- Any way to reach `importFromAddress` from the interface or a link: the links feature's (`song-links.md`, the
  Dropbox shared setlists of `TO_DO.md`), together with the root `CLAUDE.md` line about the request it makes.
- Previewing an incoming song's text or diffing it against the library's copy from the review.
- Restoring a review across a process death, or keeping the picked bytes anywhere but memory.
- Renaming `CampfireDestination.ImportReport` and the `importReport` package to "import": a move with no behaviour in
  it, for a refactor sweep.
- Changing what the demo library's import and the result stage's rows look like.
