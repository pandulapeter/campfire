<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/screens/setlists

The setlists screen.

- **A setlist's rows are dragged only in its reorder mode** (`CampfireViewModel.reorderingSetlistFileName`), started from the header menu's **Reorder songs** — offered for a setlist of at least two songs that is not archived — and ended by the same entry, now **Done reordering**, or by a floating Done button. While it is on, the screen shows that setlist alone, from the moment it starts — a setlist already at the top of the list stays where it is on screen, since the grid keeps its first visible item as the other setlists leave, and one anywhere else is scrolled to its header first, with room added after the end of the list for as long as that takes so a setlist near the end gets there too, and narrowed once it is there — every row carries its grip and drags from it (`draggableHandle`, which starts on the press) and from a long press anywhere on it (`longPressDraggableHandle`), and the search, the sort, the New menu and the archive toggle stand aside. Back, Escape and the web's Back end it first (it is a history entry of its own there); so does leaving the screen, performance mode, the setlist dropping below two songs or being archived or deleted, and opening its Edit, Duplicate or Export, since editing may rename the file the mode is keyed by. Outside it a song leaves the setlist through the row's overflow menu. That menu is the song list's own, behind the same `SongActions` every song row carries, headed by the entries that are about the row rather than the song (`setlistRowActions`): Move up, Move down and **Remove from setlist**, then **Choose setlists**, the sheet of every setlist, where unticking this one takes the row away behind the sheet; Remove from setlist carrying a list with a minus rather than the bin, since the song stays in the library and must not read as Delete, and asking first (`RemoveSongFromSetlist`), naming the song. A song details screen opened from a setlist keeps that setlist's box disabled in the same sheet, since unticking it would pull the screen out from under the reader. An entry whose file has gone missing has no song to build the song's actions from, so it gets a bare `ActionsMenu` holding the row's entries alone.
- **An archived setlist is read only**: it can be unarchived, duplicated — a set that has been played is the likeliest start for the next one, and the copy is not archived — exported and deleted, and nothing else. Its header menu has no Edit, Choose songs or Reorder, its rows no Move or Remove and no "Choose songs" row, and the view model refuses those writes for it (`editSetlist`, and `updateEditableSetlist` for every write to its entries, which reads the archived state inside the serialized update; `updateSetlist` itself stays open to it, since unarchiving goes through it). `editSetlist` writes only the details the sheet changed from what it offered, leaving the rest as the library has them now (`mergedSetlistDetails`), and a setlist gone by the time Edit or Duplicate is saved is reported as a failed operation, not recreated. A song opened from it is read the way performance mode reads it, keeping only Edit and Export in its one overflow menu.
- **A drag is answered in the frame it is reported and written once when it ends.** The screen keeps the order the finger has put the rows in (`DraggedSetlist`, drawn through `SetlistWithSongs.rows`, each row numbered by the slot it sits in) and gives it up only once the library agrees — or once the write fails, is refused or the setlist is archived — so nothing snaps back while the write is round tripping. `CampfireViewModel.reorderSetlist` then writes that order in one go, dealing the songs the drag saw back into the slots they occupied, so that an entry that reached the file meanwhile keeps its place. Writing per move instead worked each one out from a `setlists` value the previous move had not reached yet, which is what made a quick drag jump. The rows of the other setlists are not drop targets while a drag runs (`ReorderableItem`'s `enabled`, from the setlist the drag started in): the reorderable state locks itself after every move it hands over until the list answers it, and a move into another setlist, which is never answered, froze the drag for a second. A row can also be moved one place at a time, for whoever cannot drag: Move up / Move down entries in its overflow menu and the same two as custom accessibility actions on the row, each one the same single `reorderSetlist` write, worked out from the order on screen and offered only in a direction the row can go.

A setlist row only goes through `ReorderableItem` while reorder mode is on (`SetlistList.kt`) — every row of every
setlist while the setlist is being brought to the top, which keeps the other setlists' rows out of the drag's drop
targets for that scroll, then only the narrowed setlist's — so `isReordering` says whether this row's `ReorderableItem`
wrapper, its `longPressDraggableHandle` / `draggableHandle` and the elevation and color it animates while a row is
lifted are needed at all. The switch composes the row anew, so the grip's `MutableTransitionState` (and the row's
rendered key) is remembered above it, and the grip still slides in and out. Outside the mode a row is placed with a plain `listItemAnimation` instead, with
none of that — the per-row coroutine `draggedListItemContainerColor`'s own documentation warns a list this size
cannot afford is exactly what every setlist row was paying for, reordered or not.

## Setlist files

- **A setlist shows every song it names**, whatever the Songs screen is filtered to: the filters narrow a view of the
  library, while a setlist is the list somebody wrote down. What the Setlists screen's own controls ask is the order
  the setlists come in and whether the archived ones are among them. Archiving is how a setlist that has been played
  is put away without the songs in it being lost; it is a field of the `*.setlist.json` file rather than a
  preference, so it travels through an export, an import or a sync run the way a tag does. The **description** — an
  optional sentence about what a setlist is for, shown under its header and read by the screen's search — lives in
  the file for the same reason, and so does the **date**: the day the setlist is for, an ISO date that starts as the
  day it was created here and is moved with a calendar sheet opened from the sheet that names the setlist. **Every
  setlist has one**: an import dates a setlist that carries none the same way (the bundled demo one included), unless
  it replaces a library setlist, whose day it keeps, and a file in the library that names none — written before there
  were dates, by hand, or by an older version on another device — is given the day it is first read on and saved with
  it right after that read, through the repository's locks but announcing nothing, so the next sync run carries it.
  The setlist details sheet's **Countdown** checkbox, off by default and in the file too, puts a subtitle under the setlist's
  sticky header that says how far that day is ("In 5 days", "Today", "Yesterday") — the only place the date shows
  outside the sheet, so the one way it can be seen in performance mode. The same subtitle carries how long the setlist's songs take ("42:30 running time", the label after the number so that a narrow header cuts off the label rather than the time), after the countdown or in its place,
  added up from their `{duration}`s and marked with a `+` where some songs have none that can be read. Sorting by date puts the latest day on top, the setlists
  of one day by their title.
