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

`ui/screens/importReport/` — **the import screen** (`CampfireDestination.ImportReport`), the one place an import that
went anywhere but the happy way reports on itself: a question about names that are taken, the import its answer decides
on being written, and what it came to, as one screen going through those stages in place (the title, the content and the
floating button crossfading) rather than dialogs replacing one another. The view model holds what it shows
(`importReport`: `Review`, `Importing`, `Finished`) and puts it on the back stack once no dialog is up and no editor
with unsaved text is on the stack (`showImportReport`); a clean import ends in the snackbar instead, offering
**Details** (`openImportReport`) for a batch of more than one file. The question is the three answers above the full
list of the names, and Import with Replace selected asks `DialogType.ConfirmImportReplace` over the screen first. The
result lists what went wrong first (failed, not processed, skipped by the answer, unsupported, too large, unreadable)
and what arrived after it, each group under a heading with its count (`ImportReportSections.kt`, pure and tested): songs
and setlists named as the library names them, a song opening in a pager over its group (`openReportedSong`) that Back
returns from, a converted one marked and openable in the editor outside performance mode.

The search is a field that is always there, a sticky item right above the files it narrows that pins at the top of the
list once they scroll under it — the list's own fade at the bar giving way as the field arrives, and the field drawing
the fade the rows pass under from then on — so the bar holds nothing but Close and the title. It matches a row's file
name, title and artist folded the way the library's searches fold (`ymca` finds `Y.M.C.A.`). It never takes the focus by
itself, since a keyboard over the result is not what anybody opened it for; Ctrl / Cmd + F gives it the caret
(`openCurrentSearch`, through `importReportSearch`, which stays open and is emptied as the screen is left), and Escape
and Back go straight to leaving the screen. Leaving the screen while it asks cancels the import, which needs no
confirmation since nothing has been written; leaving it while the import is written lets that finish, reported by a
snackbar with Details; leaving a result lets the next batch of the queue start, which waits for the report as it waits
for an import (`awaitImportSettled`). The single song a system "open with" handed over is opened in place of the screen
once its question is answered.
