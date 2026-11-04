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

- Before the first search interaction, arriving section headers are laid out expanded, so loading cannot keep a
  collapsed first row as a scroll offset. `ListTopFade` treats leading collapsed header slots as zero scroll distance.
- An open search runs the sections together, so cards fill the columns a section's last row left open; the Selection
  group loses its header too and keeps the divider.
- **The top left card in sight stays where it is on screen** as the search opens and closes; closing unscrolled over the
  same songs puts it back where it was. A random pick there gives way to the first card after the picks. Cards coming
  into sight past the screen's edges travel in with their neighbours, as from a list really scrolled.

## Random picks

- Over `RANDOM_PICKS_MIN_SONGS` songs (after the filters), both sorts are led by five random ones, as copies with keys of
  their own, then a divider. Gone while a search is open; in selection mode they follow the Selection group, which takes
  no songs out of them. Drawn only from a library read whole,
  never from a first read's partial batches, which would swap them under the reader; the launch screen waits meanwhile.
- The seed is device-local, never synced: stored with its draw time in the preferences (`randomPicksSeed`), so a launch
  within the hour shows the same picks, and in a restored process's saved state with the stop time, so its first start
  is judged as a return. New picks once an hour has gone by on the wall clock, taken only as the screen is opened (the
  launch, the app back in front on it after at least five minutes away, or another tab left for it), never under a
  reader; Refresh draws at once. Each song is ranked on its own, so a sync run's change moves one pick.
- Under the first picks shown a card asks whether to keep them, saying the switch is in Settings; either answer, or the
  switch flipped, answers it for good.
- A sort picked from the menu ends the list on the new order's first section after the picks, since the picks in sight
  do not show the change: glided down to from the very top, put straight onto from anywhere else. The sort already
  applied, picked again while the picks are in sight, glides there too; once the first section's header sticks, nothing.

## Back resets the filters

- With Songs the whole back stack, a back (gesture, system button, the web's Escape) resets the tag and language filters
  instead of leaving the app, once no search is open and no selection is on; the desktop's Escape does it in its key
  handler. A predictive gesture fades the filter action's badge and fill with the finger.
- The web's browser Back is not taken: the filters get no history entry, since one pushed again on returning from
  another tab has no user gesture behind it, and Chrome's Back skips such entries.

## The add songs hint

- Under three songs in the whole library (the demo library is two) the list ends in a hint: pasting a song copied off
  any web page, and importing another app's export. It continues the onboarding, so it has no dismiss button; the third
  song takes it away. Not under a placeholder, a search, the selection, or performance mode.
- It is the last grid item, never the first, so the item and section indices need not count it.

## Selecting songs

- **Started only from a song's own menu** ("Select multiple", last), with that song ticked; long press and right click
  still open the menu, and the bar gets no button for it.
- Kept by file name, so search, sort, and filters keep the selection. A song leaving a library read whole leaves it (a
  rescan's batches do not prune).
- Ends on Done, Back, Escape (after closing an open search first), web Back, whenever Songs stops being on top, and when
  performance mode comes on. Never saved or restored. **No action ends it**, so one selection can be tagged, filed,
  then exported.
- Cards show a crossfaded box in place of star and menu; a screen reader hears one checkbox. A tag or language tapped
  under a song still filters.
- **Ticked songs lead the list** (as in Choose songs), the random picks after them, under a counting header ("✓" on the fast scroller), always
  followed by a divider, counted as an item by the indices.
  - At a refresh (a change of search, sort, or filter, or Select all), ticked songs lead in list order, taken out of
    their sections (an emptied section goes).
  - A tick since then joins the end of the group **as a copy** (its own item key) while the tapped card stays put; an
    untick stays in the group until the next refresh.
- **Nothing on screen moves under a tick**: the first visible card outside the group is kept in place.
- The bar keeps search, sort, and filter; New gives way to the selection menu, which stays while search is open. Actions
  are disabled while nothing is ticked or a write runs.
- Actions run in the screen's sort order; one song is shared as the row's Share song, several through the export
  screen. **There is no bulk delete.**
