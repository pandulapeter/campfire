<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/components/menu

Overflow menus, their expansion into buttons, and the sort menus.

- **An overflow menu expands into buttons wherever there is room** (`ActionsMenu`), one at a time in menu order, the
  overflow button going last. Neighboring icon buttons overlap touch targets by 12dp (`ActionOverlap.kt`'s
  `overlappingAction`); an overflow button reaches 8dp further into the button before it, since its dots are a sliver
  of ink (`OVERFLOW_BUTTON_EXTRA_OVERLAP`; `ActionsMenu.isAfterButton` for a button the caller stands before it).
- **Some entries stay in the menu whatever the room** (`ActionsMenuItem.isAlwaysInMenu`): a setlist's Edit details,
  Reorder, Archive, Duplicate, Export, and Delete; a song's Update file name and Duplicate; the editor's Revert. A song
  card never expands (`isExpandable = false`).
- **Every overflow button opens a dropdown**, touch included. Long press (touch) and right click (`ContextClick.kt`,
  which beats the row's clickable since Android clickables answer a right click like a left) open the same dropdown from
  the row's button (`OverflowMenuState`); the button is always there, since a gesture announces itself to nobody.
- `OverflowMenu` counts open menus (the desktop Escape handler is outside the composition) and acts through
  `OverflowMenuState.select`, which runs an entry once per opening: a `DropdownMenu` stays tappable during its exit, and
  a double tap on Export would export twice.
- `SortMenu` (`SongSortMenu`, `SetlistSortMenu`) is shared by each screen and its picker sheet.
