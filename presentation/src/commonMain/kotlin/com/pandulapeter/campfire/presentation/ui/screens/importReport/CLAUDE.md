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

The import screen. Every import the user starts goes through its five stages in place (Loading, Failed, Review,
Importing, Finished), crossfading rather than replacing dialogs. What the review decides is
`domain/implementation/CLAUDE.md`'s.

## When it shows

- Pushed only once no dialog is up, no editor with unsaved text is on the stack, and the onboarding is not; reading runs
  meanwhile.
- Not reviewed: the demo library, shown only for a taken name. While the songs are on, a batch that is one song is
  previewed instead (`ui/screens/songDetails/CLAUDE.md`), and one the library holds as it is is opened, saying so in one
  line rather than in counts. A single song file is read behind no screen; anything else shows Loading here first.
- **One review at a time**: a batch arriving while a review is up (dropped, opened with, shared, picked, a shared link)
  is read on its own and appended to it under a progress bar that holds Import back, the import screen brought to the
  front at once (a preview over it closes, a one-song preview becomes this screen), so the batch never lands under a
  screen showing nothing of it; ticks and choices made stay. What the review lists already is left out (a song by its
  text, a setlist by its fields, a skipped name), so the same zip twice changes nothing; a taken name stays one
  question. One arriving during Loading joins once the review is up. Waiting in the queue instead: the demo library, a
  pasted or shared text (it may be the editor's), and anything over Failed or Finished.

## Stages

- **Loading**: Cancel only while the phase only reads; Close does the same.
- **Failed**: one sentence and **Try again** (a failed download is downloaded again; once downloaded, the same bytes are
  read again). A shared link answering 4xx, or anything but a zip (a page in its place), "no longer works"; only no
  answer or a 5xx is unreachable.
- **Review** (sections tested): every entry starts ticked. A song's row opens the preview of the songs listed (the
  search's matches while it narrows them) at its own; its box ticks it alone, from the row's whole height up to where
  the text begins, never the preview. A group's heading counts it and is only a heading, ending in a **Select all** /
  **Deselect all** text button (never a row with a box, which reads as one more file); the taken names' Keep both /
  Replace for all sits under it. Both act on the rows shown and appear only while two or more are. **Import N files**
  is inert at zero, absent where nothing can be ticked; a Replace is confirmed first, and a batch arriving meanwhile
  joins only once it is answered (imported after the review if Replace wrote it).
- A shared link's review starts on **Replace** only the taken names an earlier import of the same link (by its Dropbox
  file id, which another link cannot borrow) wrote under its own names on this device, with a line saying so; every
  other taken name starts on Keep both, since it may be the recipient's own song. The record is never synced or
  exported, and bounded (100 links, 1,000 names each).
- The review is subtitled by the setlist or the one song the zip holds, never the download's random name; several
  picked files are counted (skipped ones included), never listed by name. It steps aside while the keyboard is up.
  Choices live in the view model (survive recreation); each row reads its tick through a derived state, so a tap
  recomposes that row and the summary only.
- **Spacing**: 16 dp between any two parts of the list, edge to edge; the search field has no padding under it, so the
  first heading sits as far from it as from a row. A part that comes or goes folds with its gap.
- **The summary card never changes height with a tick**: a line the ticks move is there at zero too wherever the review
  has something it counts, so a box ticked never moves.
- **Tags**: one set for the whole batch, picked in the ordinary tag sheet (Done, not Save), starting empty on every
  import and gone with it. Only added to each song's text as it is written, never removing one; a song already in the
  library is not written, so it keeps its own.
- **Finished**: a clean reviewed import closes into a snackbar with **Details**; one that left anything out (the user's
  unticks included), stopped, or failed stays here, failures first (tested).

## Search and leaving

- The search field is a sticky item above the files it narrows, so the bar holds only Close and the title. It never
  takes focus by itself; Ctrl / Cmd + F does. Escape and Back leave the screen directly.
- Leaving in Loading cancels; in Failed or Review drops the import without confirmation (nothing written); while
  Importing lets it finish, reported by a snackbar; leaving a result lets the queue's next batch start.
- **Never restored**: the plan is too large for saved state. A process ended behind an unwritten import says so; behind
  a result it says nothing. The web's reload at `/import` says nothing either way.
