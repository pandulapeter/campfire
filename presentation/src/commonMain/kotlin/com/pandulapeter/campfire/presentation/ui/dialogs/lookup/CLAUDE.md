<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/dialogs/lookup

The online cover search and song details lookup; both only fill the form's draft, and Save is the only write.

### Cover search (`CoverArtSearchSheet.kt`)

Opened from the cover in the About the song sheet; absent in performance mode or with Cover art off.

- The search is asked as the sheet enters the stack (`addOpenDialogsChange`), so its first frame already says
  "searching", and afterwards **only from the search key or button, never per keystroke**: MusicBrainz allows one
  request a second.
- The Web address tab previews after a 500 ms typing pause; an address that does not load can still be saved, since a
  host may refuse only the web build.
- A tile whose thumbnail fails is dropped (that is how a cover-less release group is found out); if any dropped tile
  was unreachable and none is left, the search shows as failed with Retry. A new search restores them all, and the
  repository asks unreachable ones again.
- **The sheet opens at full height**, since its content grows after opening and resizing while sliding stuttered.
- The search lives in the view model (`coverArtSearch`) to survive activity recreation, and is cancelled once the sheet
  is nowhere in the stack (the Remove question over it keeps it).

### Song details lookup (`SongDetailsLookupSheet.kt`)

**Writes nothing.** Put over the Edit song details form (which stays open under it) with the form's offered values and
draft (`DialogType.SongDetailsLookup`).

- Fill in (Ctrl / Cmd + S) hands a new draft to the form (`fillSongMetadata`) and closes the lookup. Each Fill in bumps
  `fillCount`, which keys the form's fields, so they reset even to an equal draft. A restored process reopens the form,
  not the lookup.
- Search runs the same way; its state is `SongDetailsLookupController`'s, cleared with the sheet.
- A row's review (`SongDetailsReview.kt`, pure and tested): one box per field it would change, ticked only where the
  form is empty and never for the title; identical values omitted, durations compared as durations.
- MusicBrainz songwriters are fetched when a row opens (one request; the controller does not re-ask for the row it
  already holds, so activity recreation asks nothing). iTunes names none.
