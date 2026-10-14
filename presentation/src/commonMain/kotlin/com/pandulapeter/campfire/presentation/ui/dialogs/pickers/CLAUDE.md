<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/dialogs/pickers

The pickers: songs into a setlist, a song into setlists, a selection into setlists, and the app language.

The setlist picker (every setlist, one song) and the song picker (every song, one setlist) share parts:

- The header's sort button is the list screen's own preference, so the sheet orders as the screen does. A new sort or
  tag/language order sends the list to its first row (`ScrollToStartWhenChanged`, held until the reordered list
  arrives).
- `PickerList` takes the height the sheet has left and **never gets shorter while open**, so narrowing by search does
  not shrink the sheet and move the field being typed into. Its gap under the search field is content padding.
- The song picker lists the setlist's own songs first (held while open), then the rest in the songs screen's order. Its
  tag and language chips (`SortableChipRow`) are a plain `Row` animated by `animateBounds`, since a lazy row sent to its
  start drops its placement animation; their selection is the sheet's own, empty each opening. Rows are built off the
  main thread once per library (`SongPickerIndex.kt`); chips are `ScreenData.libraryTags`/`libraryLanguages`.
- **One write per tick** (`CampfireViewModel.setSetlistSong`, serialized by a `Mutex`, each applied to the setlist as
  the library has it then): ticks come faster than a write round-trips, and boxes following `setlists` would flick back.
  A gone setlist is not recreated from the sheet's copy. A ticked song goes to the end; an entry whose file is missing
  is kept.
- The setlist picker's "New setlist" renders the naming sheet over the picker from its own state, not via the stack,
  since the song goes into the setlist as soon as it exists (`createSetlistWithSongs`). It opens named after a search
  that found nothing. A song with no listable setlist (`hasListableSetlist`) skips straight to the naming sheet.

### The selection mode's Choose setlists sheet

For the songs screen's selection mode (see `ui/screens/songs`). Each holds the selection as it was when opened; none has
a `SavedDialog`, since the mode is not restored either.

- **Choose setlists** (`SelectionSetlistsSheet`): a box per setlist, ticked / empty / half ticked by how many of the
  songs it holds (`selectionBoxState`, following the library until the sheet is first tapped on it, then the sheet's own
  record, since the library lags its writes). Tapping an empty or half box appends the missing songs in one write
  (`Setlist.withSongsAdded`); tapping a box the sheet filled removes exactly those (`withSongsTakenOut`). **A song the
  setlist already held is never removed from here**, since that loses its place and key. A library with nothing to list
  skips to the naming sheet (`createSetlistWithSongs`).
