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

The import screen.

### `ui/screens/importReport/`

`ui/screens/importReport/` — **the import screen** (`CampfireDestination.ImportReport`): every import the user starts
opens it, and goes through five stages on it in place (the title, the subtitle naming the source, the content and the
floating button crossfading) rather than dialogs replacing one another. The view model holds what it shows
(`importReport`: `Loading`, `Failed`, `Review`, `Importing`, `Finished`) and puts it on the back stack once no
dialog is up, no editor with unsaved text is on the stack and the onboarding is not (`showImportReport`); the reading
runs behind any of those meanwhile. Two imports are not reviewed: the demo library (`shouldReview = false`), and a single
song file the system handed over, which is written and opened as before — each of them puts the screen up only for a
name it finds taken, as a one-row review. A song picked from the app's own Import files is reviewed.

**Loading** shows the download (an address, `importFromAddress`, reached by nothing yet) or the reading, through
`ImportProgressContent`, with Cancel under it while the phase only reads (`isCancellable`); Close does the same.
**Failed** says why in one sentence (`ImportFailure`: the host unreachable, nothing at the address, larger than the
24 MB an import reads, unreadable) with **Try again** as the floating button (`retryImport`: the same address asked
again, or the same bytes read again, since a downloaded address becomes the file it downloaded).

### The review

**Review** lists everything the files hold (`ImportReviewSections.kt`, pure and tested): a summary card of what Import
would do with the ticks as they stand (`importReviewSummary`), the search field, then **Names already taken**, **Songs**,
**Setlists**, **Already in the library** (no boxes; a repeat inside the batch is not listed, its entry saying how often it
appears), and the three groups of what cannot be imported. Every entry is ticked to start with; a group with boxes has a
tri-state box in its header acting on the rows it shows, the search's matches only while it narrows them. A taken name
has its own Keep both / Replace choice, a song's with **Show the one you have**, and the group's header the choice for
all of them, which reads what the rows it shows come to (Mixed where they differ) and sets each of them, the search's
matches only while it narrows them, as its box does. The floating button reads **Import N files**, dimmed and inert at
zero and absent where nothing can be ticked (titled "Nothing to import"); where the choices replace anything,
`DialogType.ConfirmImportReplace` asks first (`confirmImport`). It steps aside while the keyboard is up, which is when
the rows it would cover are being searched. The choices live in the view model's report, so a recreated activity keeps
every tick, and each row reads its own tick through a derived state, so a tap recomposes that row and the summary only.

### The result

**Importing** is the progress again, with nothing to press. A reviewed import that went clean closes the screen into
the snackbar with **Details** (`openImportReport`, which reopens `Finished`); one that left anything out, the user's own
unticked entries included, stopped, or failed stays on **Finished**, which lists what went wrong first (failed, not
processed, left out by your choice, unsupported, too large, unreadable) and what arrived after it, each group under a
heading with its count (`ImportReportSections.kt`, pure and tested): songs and setlists named as the library names them,
a song opening in a pager over its group (`openReportedSong`) that Back returns from, a converted one marked and openable
in the editor outside performance mode.

### Search and leaving

The search is a field that is always there, a sticky item right above the files it narrows that pins at the top of the
list once they scroll under it — the list's own fade at the bar giving way as the field arrives, and the field drawing
the fade the rows pass under from then on — so the bar holds nothing but Close and the title. It matches a row's file
name, title and artist folded the way the library's searches fold (`ymca` finds `Y.M.C.A.`). It never takes the focus by
itself; Ctrl / Cmd + F gives it the caret (`openCurrentSearch`, through `importReportSearch`, which stays open and is
emptied as the screen is left), and Escape and Back go straight to leaving the screen. Leaving it in `Loading` cancels,
in `Failed` or `Review` drops the import, which needs no confirmation since nothing has been written; leaving it while
the import is written lets that finish, reported by a snackbar with Details; leaving a result lets the next batch of
the queue start, which waits for the report, a result reopened from **Details** included, as it waits for an import
(`awaitImportSettled`). The single song a system "open with" handed over is opened in place of the screen once its
review is answered. The screen is never restored, since its plan is far too large for the saved state; a process ended
behind it says so with a snackbar where the import had not been written yet (`Message.ImportInterrupted`, from a flag
saved beside the back stack, `isUnfinished`), and says nothing behind a result. The web's reload at `/import` says
nothing either way, since its address cannot tell a review from a result.
