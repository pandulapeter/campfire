<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/dialogs/forms

The form sheets that write a song or setlist file.

Root `CLAUDE.md` owns the rules that every typed modal is a `TextFieldBottomSheet` with its actions in the header.

- **First-field focus is per form** (`rememberFirstFieldFocusRequester`): new song, new and duplicate setlist, tag
  management, language management, and library deletion focus their first field; duplicate song, edit setlist, song
  metadata, links, and cover art do not. Both assignment pickers leave search unfocused at every height.
- Every typed field has a clear button (`rememberClearTextButton`), and every keyboard action does something (at least
  hiding the keyboard).
- **Every list that shares a sheet with a text field hides the keyboard as it starts scrolling down**
  (`HideKeyboardWhenScrolledDown`). The list screens' own instance stands down while a dialog covers them: in Firefox
  the keyboard shortens the page, the list scrolls to keep the opening card in view, and would put away the keyboard the
  dialog just raised.
- **New song** reuses Edit song details' fields and order (`SongMetadataField`); `CreateSongUseCase` writes nonblank
  optional directives into the initial template.
- **Duplicate song** (Songs screen cards only, `SongActions.isDuplicable`): the same sheet prefilled from the file,
  unfocused. `DuplicateSongUseCase` gets only changed fields, written over the file's current text, so the copy keeps
  every other directive; it is named like any new song (unchanged → original name `_2`) and opens in the editor.
- **Setlist details** (`SetlistDetailsDialog`: new, edit, duplicate, and the picker's naming sheet are one sheet with
  different labels): title, description, date (today unless the setlist has one; a copy starts today), and Countdown. A
  copy is named before it is made, since two setlists may share a title. The title is a `TextFieldValue` so the whole
  title is selected the first time it takes focus (and not before: a selection without a caret reads as a glitch); the
  description puts the caret at the end.

### Song defaults sheet

`SongPlayingDialog`: key (typed in the reader's notation, written in standard), capo, tempo, and time signature as the
file declares them. Save writes and validates only changed fields (`isValidSongPlayingDraft`): an out-of-range value
already in the file shows as an error without blocking the others, and a field a feature switch hides is never judged.
Its first line never says "this device", since library overrides are synced.
