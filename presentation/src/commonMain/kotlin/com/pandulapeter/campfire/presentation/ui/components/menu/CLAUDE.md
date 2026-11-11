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

- **An overflow menu expands into buttons wherever there is room** (`ActionsMenu`), one at a time in menu order, the
  overflow button last. Neighboring icon buttons overlap touch targets by 12dp; an overflow button 8dp more, since its
  dots are a sliver of ink.
- **Some entries stay in the menu whatever the room** (`isAlwaysInMenu`): a setlist's every entry but Choose songs; a
  song's Update file name, Duplicate, Export, and Delete; every editor entry but Find and Find and replace. A song card
  never expands: its builder overload builds entries only once the menu opens, so it assumes there is always one.
- **Every overflow button opens a dropdown**, touch included. Long press and right click (`ContextClick.kt`, which beats
  the row's clickable, since Android clickables answer a right click like a left) open the same dropdown from the row's
  button, which is always there, since a gesture announces itself to nobody.
- `OverflowMenu` counts open menus (for the desktop Escape handler outside the composition), and
  `OverflowMenuState.select` runs an entry once per opening: a `DropdownMenu` stays tappable during its exit, so a
  double tap on Export would export twice.
- A dropdown is bounded by the window less the system bars and keyboard (`PopupRoom`), resolved once in `CampfireApp` as
  its own local (`OverlayState` outlives an Android activity; the insets belong to its view), read only while measured,
  so no closed menu resolves insets and a resize recomposes nothing.
