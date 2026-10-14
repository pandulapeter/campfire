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

### The screen

- Setlists are listed whenever any exist, an empty library included; the library's empty state belongs to the songs
  screen. A screen whose every setlist is archived says so, rather than claiming there are none.
- The include-archived toggle sits at the end of the list, only while at least one is archived, off by default.
- The countdown subtitle turns over at midnight on a screen left open (`rememberToday`).
- The description is the first thing under the header row. In a short window (under 480dp, a search's keyboard counted)
  it starts at two lines and opens on a tap, or four lines push the first song out of sight.
- The archived icon fades with the archiving (the menu that archives hangs from the same row).
- "New setlist" opens the song picker right after creating (`CampfireViewModel.createSetlist`), only if nothing else
  was opened meanwhile and the library has songs.

### A setlist's rows are dragged only in its reorder mode

- `CampfireViewModel.reorderingSetlistFileName`, for a non-archived setlist of 2+ songs. While on, the screen shows
  that setlist alone (scrolled to its header first, with temporary room after the list so one near the end gets there).
- It ends on Done, Back / Escape / web Back (its own history entry there), leaving the screen, performance mode, the
  setlist dropping below two songs or being archived / deleted, and opening Edit, Duplicate, or Export — editing may
  rename the file the mode is keyed by.
- Outside it, a row's menu is `SongActions` headed by `setlistRowActions`. Remove from setlist uses a list-minus icon,
  not the bin (the song stays in the library). A missing song's row gets a bare `ActionsMenu` with the row's entries.
- A song details screen opened from a setlist keeps that setlist's box disabled in Choose setlists: unticking it would
  pull the screen out from under the reader.

### An archived setlist is read only

- Only unarchive, duplicate (the copy is not archived), export, and delete. The view model enforces it too:
  `editSetlist` and `updateEditableSetlist` (checks the archived state inside the serialized update). `updateSetlist`
  stays open, since unarchiving goes through it.
- `editSetlist` writes only the details the sheet changed (`mergedSetlistDetails`); a setlist gone by the time Edit or
  Duplicate is saved is reported as failed, not recreated.
- A song opened from it reads like performance mode (only Edit and Export in its menu).

### A drag is answered in the frame it is reported and written once when it ends

- The screen keeps the dragged order (`DraggedSetlist`, via `SetlistWithSongs.rows`) until the library agrees, or the
  write fails / is refused / the setlist is archived, so nothing snaps back mid-write.
- `reorderSetlist` writes once at the end, dealing the dragged songs back into the slots they held (`withSongOrder`,
  `ui/state/setlists/SetlistSongOrder.kt`) so an entry that reached the file meanwhile keeps its place. Writing per move made
  quick drags jump.
- Other setlists' rows are not drop targets (`ReorderableItem`'s `enabled`): the reorderable state locks after each move
  until the list answers, and a cross-setlist move is never answered, freezing the drag.
- Move up / down (menu entries and accessibility actions) are the same single write.
- Rows go through `ReorderableItem` only while reorder mode is on (all setlists' rows while scrolling to the top, then
  only the narrowed one's). Outside it a row uses plain `listItemAnimation`: the per-row coroutine of
  `draggedListItemContainerColor` is too costly for every row. The grip's `MutableTransitionState` and the row key are
  remembered above the switch so the grip still slides.

## Setlist files

### A setlist shows every song it names

- Whatever the Songs screen is filtered to: filters narrow a view of the library, a setlist is the list somebody wrote.
- Archived, description, date, and countdown are fields of the `*.setlist.json` file (not preferences), so they travel
  through export, import, and sync.
- **Every setlist has a date**: an import dates a dateless setlist with today (the demo's included) unless it replaces a
  library setlist, whose day it keeps; a library file without one gets the day it is first read on and is saved right
  after that read, through the repository's locks, announcing nothing, so the next sync carries it.
- Countdown (off by default) shows how far the day is in the sticky header's subtitle — the only place the date shows
  outside the sheet, so the only way in performance mode. The subtitle also carries the running time from the songs'
  `{duration}`s (`SetlistTotalDuration.kt`; label after the number so a narrow header cuts the label, not the time; `+` where some songs have none).
- Sorting by date puts the latest day first, same-day setlists by title.
