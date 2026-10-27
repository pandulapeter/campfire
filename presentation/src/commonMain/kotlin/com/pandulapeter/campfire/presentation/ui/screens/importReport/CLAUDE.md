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
- Not reviewed: the demo library (`shouldReview = false`), shown only for a taken name. While the songs are switched on,
  a batch that is one song and nothing else (`isPreviewed`, from any source) is previewed instead, and one the library
  holds as it is is opened, saying so in one line rather than in counts (`librarySong`, `Message.SongAlreadyInLibrary`);
  see `ui/screens/songDetails/CLAUDE.md`. A single song file is read behind no screen (`isReportedWhileRead`); anything
  else shows Loading here, which a preview then takes the place of.
- **One review at a time**: a batch arriving while a review is up (dropped, opened with, shared, picked, a shared
  link) is read on its own and appended to it (`withAdded`), under a progress bar that holds Import back, with the
  import screen brought to the front at once (`bringReviewToFront`: a preview over it closes, the preview of one song
  becomes this screen in its place, so the batch never lands under a screen showing nothing of it); ticks and
  choices made stay. What the review lists already is left out (a song by its text, a setlist by its fields, a skipped
  name), so the same zip twice changes nothing; a taken name stays one question. Joined batches settle with the review.
  One arriving during Loading joins once the review is up. Not joined, waiting in the queue instead: the demo library,
  a pasted or shared text (it may be the editor's), and anything over a Failed or Finished stage.

## Stages

- **Loading**: `ImportProgressContent`, Cancel only while the phase only reads (`isCancellable`); Close does the same.
- **Failed**: one sentence (`ImportFailure`) and **Try again** (`retryImport`: a failed download is downloaded again;
  once downloaded, the same bytes are read again). A shared link has its own wording: a 4xx, or anything but a zip (a
  page in its place), is "no longer works" (`toSharedLinkDownload`); only no answer or a 5xx is unreachable.
- **Review** (`ImportReviewSections.kt`, pure and tested): every entry starts ticked. A song's row (one already there
  included) opens the preview of the songs listed, the search's matches while it narrows them, at its own
  (`openReviewPreview`, `previewSongIndices`); its box ticks it on its own, from the row's whole height up to where the
  text begins (never the preview), and a chevron ends the row. A group's heading counts it ("12 songs") and is only a
  heading; under it a **Select all** row laid out as the rows are (and in the taken names' group with the Keep
  both / Replace choice for all under its label, as each taken name's row has its own under its text, so a title keeps
  the row's width) acts on the rows shown, the search's matches only while it narrows them. The button reads **Import N files**, inert at zero,
  absent where nothing can be ticked; a Replace is confirmed first
  (`DialogType.ConfirmImportReplace`); a batch arriving while it is open joins only once it is answered, and is
  imported after the review if Replace wrote it. A shared link's review starts on **Replace** only the taken names that an
  earlier import of the same link (by its Dropbox file id, which another link cannot borrow) wrote under its own names
  on this device, with a line under the counts saying so (`sharedLinkChoices`); every other taken name starts on Keep
  both, since it may be the recipient's own song. The record is `UserPreferences.sharedLinkImports`, never synced or
  exported, and bounded (100 links, 1,000 names each). The review is subtitled by the setlist or the
  one song the zip holds, never the download's random name (`Review.subtitleName`); several picked files (a joined
  review too) are counted, skipped ones included, never listed by name (`sourceCount`). It steps aside while the keyboard is up. Choices live in the view model's report
  (survive activity recreation); each row reads its tick through a derived state, so a tap recomposes that row and the
  summary only.
- **Spacing**: 16 dp between any two parts of the list, measured from their edges (the parts above the rows, the
  search field, a heading's text and the row before it); the search field has no padding under it, so the first heading
  sits as far from it as from a row. A part that comes or goes folds with its gap.
- **The summary card above the rows never changes height with a tick** (`ImportReviewSummaryCard`): a line the ticks
  move (the taken names' Replace / Keep both counts, those left out) is there at zero too wherever the review has
  something it counts (`ImportReviewSummary.hasNameTaken`, `hasEntriesToChoose`), so a box ticked never moves.
- **Tags** (`ImportReviewTags`, under the counts while any song could be written): one set for the whole batch, picked
  in the ordinary tag sheet (`ImportTagsSheet`, Done, not Save) and kept in `ImportChoices.tags`, so it starts empty on
  every import and is gone with it. Written into each song's text as it is written (only added, never removing one the
  file carries); a song already in the library is not written, so it keeps its own.
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
