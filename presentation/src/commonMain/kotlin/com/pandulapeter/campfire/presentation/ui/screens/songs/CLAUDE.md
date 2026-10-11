<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/screens/songs

The songs screen.

Before the first search interaction, Songs lays arriving section headers out fully expanded so loading cannot retain a
collapsed first row as a scroll offset. `ListTopFade` treats leading collapsed header slots as zero scroll distance: its
mask is absent at the real top and grows over the first 24dp of scrolling, including when the first card is item 1.

## Selecting songs

- **A selection mode, started from a song's own menu** ("Select multiple", right under "Edit song file", the first
  of `SongActions`' `fileEditItems`), which starts with that song ticked; a long press keeps opening the menu, and the
  bar gets no button of its own. It is `CampfireViewModel`'s `SongSelection` (`ui/state`): snapshot state, by file name, so the search,
  the sorting and the filters keep working and keep the selection; a song that leaves a library read whole leaves the
  selection (a rescan's batches do not prune). It ends with the floating **Done** button, Back, Escape (after an open
  search, which Back closes first) and the web's Back, and whenever the Songs screen stops being on top — a tab, an
  import's screen, a song opened — and as performance mode comes on; nothing of it is saved or restored. No action ends
  it, so one selection can be tagged, then filed, then exported.
- **The cards** carry a box in place of their star and menu (crossfaded), a tap ticks rather than opens, and a screen
  reader hears a card as one checkbox. A tag or a language tapped under a song still filters the list.
- **What is ticked leads the list** the way the Choose songs sheet's ticked rows lead it (`ChecklistOrder`), under a
  header that counts what is ticked ("3 selected", `SongGroupHeader.Selection`, a presentation type next to the
  domain's sections; "✓" on the fast scroller), and a divider always sets the group off from the rest, standing headers
  or collapsed (an open search) alike (`hasSelectionDividerAfter`, which `itemIndexOf` and `SongSectionIndex` count as an
  item of the section). The songs ticked at the last refresh lead in the list's order, taken out of their sections (a
  section that empties goes); every tick since joins the end of the group **as a copy** (`SongGroup.copiedFileNames`, a
  grid item of its own, `SongGroup.itemKey`) while the card that was tapped stays where it is, the song the mode was
  started from included; an untick stays in the group until the next refresh. A refresh is a change of the search, the
  sorting or a filter (`SongGroups.filterKey`) and Select all (`SongSelection.selectAllCount`, so that it leads with
  what it ticked rather than copying the whole list), and the order is keyed by the session
  (`rememberSongGroupsWithSelection`).
- **Nothing on screen moves under a tick**: `KeepCardsInPlaceUnderTick` asks the grid, in the composition the new order
  arrives in, to keep the first card in sight outside the group where it was, the group above it moving out of the way,
  as `KeepChecklistRowsInPlace` keeps the sheet's rows. That request is a jump the grid forgets its item animation over,
  so the tick is narrated by the list's `ListAnchor` instead: the cards above the held one slide out of the way, and the
  copy, the header and the divider fade in. A tick that needs no request is the grid's own item animation.
- **The bar** keeps the search, the sort and the filter; behind its divider New gives way to the selection menu
  (`SongSelectionMenu`), which stays while the search is open (`areClosedSearchActionsKeptInSearch`): Select all (what
  is listed now, added to what is ticked) and Deselect all, always in the menu, then Choose setlists, Manage tags,
  Manage languages and Export songs, let out as buttons where the list has two columns, disabled while nothing is
  ticked or a write is running (`isEditingSongs`, which also shows the import's progress bar).
- **The actions** open on the selection in the order the screen sorts by (`selectionInListOrder`): the three sheets
  are `ui/dialogs`' (`SelectionSetlistsSheet`, `SelectionTagsSheet`, `SelectionLanguagesSheet`), and one song exports
  as the row's own Export, several as the export screen's third kind of source (see `ui/screens/export`). There is no
  bulk delete.
