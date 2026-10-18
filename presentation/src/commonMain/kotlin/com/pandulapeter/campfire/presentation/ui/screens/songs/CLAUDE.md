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
  collapsed first row as a scroll offset. `ListTopFade` treats leading collapsed header slots as zero scroll distance
  (no mask at the real top, grows over the first 24dp, also when the first card is item 1).

- An open search runs the sections together (`withoutSectionHeaders`), so cards fill the columns a section's last row
  left open; the Selection group loses its header too and keeps the divider (`SongGroup.isSelection`).
- **The top left card in sight stays where it is on screen** as the search opens and closes
  (`KeepCardsInPlaceAcrossSearch`). Opening, the results start with empty cells (`SongGroup.leadingGap`) that keep it in
  its column, kept while the search shows those songs; its row is asked for 48dp higher, by as much as the filling bar
  moves the list down, and `ListAnchor` narrates the jump on the bar's spring, so the rest slides while it stands still
  (headers vanish at once: a request makes the grid forget its item animation). `SearchCardHold` asks again while that
  runs, since the cards above it grow by their artist line; a card's height changes on the bar's spring too, and
  `ListAnchor` slides each item from its first layout, so growth and slide add up to one movement. Closing over the
  songs it opened over, neither scrolled nor shown other songs since (a query typed and cleared counts), puts it back
  where it was; otherwise the top left card then is kept the same way, its column the one its section gives it.
  A list arriving inside the narration ends the hold (`SearchCardHold.follow`): re-ranked results let go to the top and
  stop it, a tag tap's own newer narration is left running (`ListAnchor.transitionNumber`), and the full list arriving
  after closing holds the same card at its new index.

## The add songs hint

- While the whole library holds fewer than three songs (the demo library is two), the list ends in `AddSongsHintCard`:
  pasting a song copied off any web page, and importing another app's export. It continues the onboarding, so it has no
  dismiss button; the third song takes it away. Not under a placeholder, a search, the selection, or performance mode
  (`isAddSongsHintShown`).
- It is the last grid item, never the first, so `itemIndexOf` and `SongSectionIndex` need not count it.

## Selecting songs

- **Started only from a song's own menu** ("Select multiple", last of `SongActions`' `fileEditItems`), with that song
  ticked; long press and right click still open the menu, and the bar gets no button for it.
- State is `CampfireViewModel`'s `SongSelection` (`ui/state`), by file name, so search, sort, and filters keep working
  and keep the selection. A song leaving a library read whole leaves the selection (a rescan's batches do not prune).
- Ends on Done, Back, Escape (after closing an open search first), web Back, whenever Songs stops being on top (a tab,
  an import screen, a song opened), and when performance mode comes on. Never saved or restored. **No action ends it**,
  so one selection can be tagged, filed, then exported.
- Cards show a crossfaded box in place of star and menu; a tap ticks; a screen reader hears one checkbox. A tag or
  language tapped under a song still filters.
- **Ticked songs lead the list** (`ChecklistOrder`, as in Choose songs) under a counting header
  (`SongGroupHeader.Selection`, "✓" on the fast scroller), always followed by a divider (`hasSelectionDividerAfter`,
  counted as an item by `itemIndexOf` and `SongSectionIndex`).
  - At a refresh, ticked songs lead in list order, taken out of their sections (an emptied section goes). A refresh is a
    change of search, sort, or filter (`SongGroups.filterKey`), or Select all (`SongSelection.selectAllCount`); the
    order is keyed by session (`rememberSongGroupsWithSelection`).
  - A tick since then joins the end of the group **as a copy** (`SongGroup.copiedFileNames`, its own grid item key)
    while the tapped card stays put; an untick stays in the group until the next refresh.
- **Nothing on screen moves under a tick**: `KeepCardsInPlaceUnderTick` keeps the first visible card outside the group
  in place (like `KeepChecklistRowsInPlace`). That request is a jump the grid forgets its item animation over, so
  `ListAnchor` narrates it: cards above slide away; the copy, header, and divider fade in.
- The bar keeps search, sort, and filter; New gives way to `SongSelectionMenu`, which stays while search is open
  (`areClosedSearchActionsKeptInSearch`). Actions are disabled while nothing is ticked or a write runs
  (`isEditingSongs`, which also shows the import progress bar).
- Actions run on the selection in the screen's sort order (`inListOrder`): the sheets are in `ui/dialogs`; one
  song exports as the row's Export, several through the export screen. **There is no bulk delete.**
