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

- **First-field focus is per form**: new song, new and duplicate setlist, tag and language management, and library
  deletion focus their first field; duplicate song, edit setlist, song metadata, links, and cover art do not. Both
  assignment pickers leave search unfocused at every height.
- Every typed field has a clear button, and every keyboard action does something (at least hiding the keyboard).
- **Every list sharing a sheet with a text field hides the keyboard as it starts scrolling down**
  (`HideKeyboardWhenScrolledDown`). The list screens' own instance stands down under a dialog: in Firefox the keyboard
  shortens the page, the list scrolls to keep the opening card in view, and would put away the keyboard the dialog just
  raised.
- **New song** reuses Edit song details' fields and order; nonblank optional directives go into the template.
- **Duplicate song**: the same sheet prefilled, unfocused; only changed fields are written over the file's current
  text, so the copy keeps every other directive; named like any new song (unchanged → `_2`), opens in the editor.
- **Setlist details** (new, edit, duplicate, and the picker's naming sheet): the date is today unless the setlist has
  one (a copy starts today). A copy is named before it is made, since two setlists may share a title. The whole title is
  selected the first time it takes focus, not before (a selection without a caret reads as a glitch); the description
  puts the caret at the end.
- **Song defaults** (`SongPlayingDialog`): the key is typed in the reader's notation, written in standard. Save writes
  and validates only changed fields: an out-of-range value already in the file shows as an error without blocking the
  others, and a field a feature switch hides is never judged. Never says "this device", since library overrides sync.
