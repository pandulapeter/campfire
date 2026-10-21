<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/screens/importReport

The import screen (`CampfireDestination.ImportReport`). Every import the user starts goes through five stages on it in
place (`importReport`: `Loading`, `Failed`, `Review`, `Importing`, `Finished`), crossfading rather than replacing
dialogs. What the review decides is `domain/implementation/CLAUDE.md`'s.

## When it shows

- The view model puts it on the back stack only once no dialog is up, no editor with unsaved text is on the stack, and
  the onboarding is not (`ImportReportState.show`); reading runs meanwhile.
- Not reviewed: the demo library (`shouldReview = false`) and, while the songs are switched on, a single song file
  opened with, shared to, or dropped on the app, which is written and opened directly. Each shows the screen only for a taken name, as a one-row review. A
  song picked from the app's own Import files *is* reviewed.

## Stages

- **Loading**: `ImportProgressContent`, Cancel only while the phase only reads (`isCancellable`); Close does the same.
- **Failed**: one sentence (`ImportFailure`) and **Try again** (`retryImport`: a failed download is downloaded again;
  once downloaded, the same bytes are read again). A shared link has its own wording: a 4xx, or anything but a zip (a
  page in its place), is "no longer works" (`toSharedLinkDownload`); only no answer or a 5xx is unreachable.
- **Review** (`ImportReviewSections.kt`, pure and tested): every entry starts ticked. Group header boxes and the taken
  names' group Keep both / Replace choice act on the rows shown, the search's matches only while it narrows them. The
  button reads **Import N files**, inert at zero, absent where nothing can be ticked; a Replace is confirmed first
  (`DialogType.ConfirmImportReplace`). A shared link's review starts on **Replace** only the taken names that an
  earlier import of the same link (by its Dropbox file id, which another link cannot borrow) wrote under its own names
  on this device, with a line under the counts saying so (`sharedLinkChoices`); every other taken name starts on Keep
  both, since it may be the recipient's own song. The record is `UserPreferences.sharedLinkImports`, never synced or
  exported, and bounded (100 links, 1,000 names each). The review is subtitled by the setlist or the
  one song the zip holds, never the download's random name (`Review.subtitleName`). It steps aside while the keyboard is up. Choices live in the view model's report
  (survive activity recreation); each row reads its tick through a derived state, so a tap recomposes that row and the
  summary only.
- **Importing**: progress, nothing to press.
- **Finished**: a clean reviewed import closes into a snackbar with **Details** (`openImportReport`); one that left
  anything out (the user's unticks included), stopped, or failed stays here, failures first
  (`ImportReportSections.kt`, pure and tested). A song opens in a pager over its group (`openReportedSong`).

## Search and leaving

- The search field is a sticky item above the files it narrows, so the bar holds only Close and the title. It never
  takes focus by itself; Ctrl / Cmd + F does (`importReportSearch`). Escape and Back leave the screen directly.
- Leaving in `Loading` cancels; in `Failed` or `Review` drops the import without confirmation (nothing written); while
  `Importing` lets it finish, reported by a snackbar; leaving a result lets the queue's next batch start
  (`awaitImportSettled` waits for the report too, including one reopened from Details).
- **Never restored**: the plan is too large for saved state. A process ended behind an unwritten import says so
  (`Message.ImportInterrupted`, from `isUnfinished` saved beside the back stack); behind a result it says nothing. The
  web's reload at `/import` says nothing either way.
