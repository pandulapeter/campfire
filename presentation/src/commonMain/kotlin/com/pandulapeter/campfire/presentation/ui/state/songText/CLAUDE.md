<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/state/songText

The texts of the songs on screen (`SongTextStore`) and the edits written into them (`SongMetadataEditing`).

- **Song texts**: `songTexts` holds only the texts of screens on the back stack, pruned once a navigation transition
  ends (a popped screen is still drawn while sliding, `pruneSongTexts`). A sync run or rescan re-reads them all in one
  update (`rereadSongTexts`). An untouched editor follows its file (`FollowFileWhileUntouched`, so saving it never
  writes the old version back); an editor with its own text whose file changed or vanished has unsaved changes, keeps
  its text, and saving writes it back under its own name (`Message.EditedSongFileGone`). `onEditorClosed` leaves the
  draft alone while the editor is still on the stack, so a configuration change never loses it.
- **Every write to a song file holds `songWriteMutex`** from the read it is built on until `songTexts` has the result,
  and passes `expectedText`: a file changed before the view model heard of it is re-read and the edit applied to it.
