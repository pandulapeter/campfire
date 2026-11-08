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

## The screen

- Setlists are listed whenever any exist, an empty library included; the library's empty state belongs to the songs
  screen. A screen whose every setlist is archived says so, rather than claiming there are none.
- The include-archived toggle sits at the end of the list, only while one is archived, off by default.
- The summary under the header row: the countdown, the running time ("… running time", not to be read as the start
  time), then the description; the countdown turns over at midnight on a screen left open. In a short window (a
  search's keyboard counted) the description starts at two lines and opens on a tap, or four lines push the first song
  out of sight.
- The archived icon fades with the archiving.
- Ctrl / Cmd + S shares or exports the setlist the screen is about (`ListedSetlists.shortcutSetlist`): the one being
  reordered, else the only one listed; with several listed it does nothing, and only that one's menu entry names it.
- "New setlist" opens the song picker right after creating, only if nothing else was opened meanwhile and the library
  has songs.
- A search sends the list to the top when the list narrowed by it arrives (the folded query travels with the setlists),
  so the screen never reads the field's text.
- A shared link to a setlist the library holds as it is brings that setlist to the top (`SetlistRevealEffect`): the
  reorder mode and the search end and an archived one's toggle turns on first; a screen already on top scrolls, one
  arriving starts there.

## Rows are dragged only in reorder mode

- For a non-archived setlist of 2+ songs. While on, the screen shows that setlist alone (scrolled to its header first,
  with temporary room after the list so one near the end gets there).
- It ends on Done, Back / Escape / web Back (its own history entry there), leaving the screen, performance mode, the
  setlist dropping below two songs or being archived or deleted, and opening Edit, Duplicate, or Export: editing may
  rename the file the mode is keyed by.
- Remove from setlist uses a list-minus icon, not the bin (the song stays in the library).
- A song opened from a setlist keeps that setlist's box disabled in Choose setlists: unticking it would pull the screen
  out from under the reader.
- The screen keeps the dragged order until the library agrees, or the write fails, is refused, or the setlist is
  archived, so nothing snaps back mid-write. One write at the end deals the dragged songs back into the slots they held,
  so an entry that reached the file meanwhile keeps its place; a write per move would make quick drags jump. Move up /
  down are the same single write.
- Other setlists' rows are not drop targets: the reorderable state locks after each move until the list answers, and a
  cross-setlist move is never answered, freezing the drag. Rows are `ReorderableItem` only in reorder mode: it is too
  costly for every row.

## An archived setlist is read only

- Only unarchive, duplicate (the copy is not archived), export, and delete. The view model enforces it too, checking the
  archived state inside the serialized update; `updateSetlist` stays open, since unarchiving goes through it.
- Edit writes only the details the sheet changed; a setlist gone by the time Edit or Duplicate is saved is reported as
  failed, not recreated.
- A song opened from it reads like performance mode (only Edit and Export in its menu).

## Setlist files

- Archived, description, date, and countdown are fields of the file (not preferences), so they travel through export,
  import, and sync. Every setlist has a date (`data/repository/implementation/CLAUDE.md`).
- The countdown (off by default) is the only place the date shows outside the sheet, so the only way in performance
  mode. A tap on it opens the calendar alone (writing only the day), outside read only mode and on a setlist not
  archived; its pressed pill is drawn in an outset, so the label stands where it would without it. The running time
  sums the songs' `{duration}`s (`+` where some have none).
- Sorting by date puts the latest day first, same-day setlists by title.
