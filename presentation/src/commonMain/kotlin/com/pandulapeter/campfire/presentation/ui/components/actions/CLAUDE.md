<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/components/actions

The actions of a song and a setlist, shared by their rows, menus, and details screens.

- `SongActions`: edit, Update file name (only where `Song.canUpdateFileName`), Duplicate song (songs screen cards only,
  `isDuplicable`), Export song, delete — **delete never from a setlist row or a details screen opened from a setlist**
  (`isDeletable`). Its entries, `leadingItems`, and `fileEditItems` are builders run only while the menu is open, since
  every card in a list composes one; a caller passes a lambda, never a list built while composing the card.
- Choose setlists is `SetlistAssignmentsButton` in front of the overflow on song rows and the details bar (moved into
  the menu as `setlistAssignmentsAction` when out of room); in a setlist row's menu after "Remove from setlist" (not on
  an archived setlist). From a details screen opened from a setlist, that setlist's box is disabled
  (`DialogType.SetlistPicker.setlistFileName`), since unticking it would pull the screen from under the reader.
- `SetlistActions`: only Choose songs ever becomes a header button.
