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

- The header's sort is the list screen's own preference, so the sheet orders as the screen does. A new sort or label
  order sends the list to its first row, held until the reordered list arrives.
- `PickerList` **never gets shorter while open**, so narrowing by search does not shrink the sheet and move the field
  being typed into.
- The song picker lists the setlist's own songs first (held while open). Its chips are a plain `Row` animated by
  `animateBounds`, since a lazy row sent to its start drops its placement animation; their selection is the sheet's
  own, empty each opening. Rows are built off the main thread once per library.
- **One write per tick** (serialized by `SetlistRepositoryImpl`'s write lock, each on the setlist as the library has it
  then): ticks come faster than a write round-trips, and boxes following `setlists` would flick back. A gone setlist is
  not recreated from the sheet's copy. A ticked song goes to the end; an entry whose file is missing is kept.
- "New setlist" renders the naming sheet over the picker from its own state, not via the stack, since the song goes
  into the setlist as soon as it exists. It opens named after a search that found nothing; a song with no listable
  setlist skips straight to it.
- **Selection mode's Choose setlists** holds the selection as opened and has no `SavedDialog` (the mode is not restored
  either). A box is ticked / empty / half by how many of the songs the setlist holds, following the library until first
  tapped, then the sheet's own record, since the library lags its writes. Tapping an empty or half box appends the
  missing songs in one write; tapping a box the sheet filled removes exactly those. **A song the setlist already held is
  never removed from here**, since that loses its place and key.
