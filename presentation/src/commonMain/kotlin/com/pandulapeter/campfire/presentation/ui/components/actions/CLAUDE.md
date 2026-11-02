<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->


# :presentation — ui/components/actions

- **A song is never deleted from a setlist row or a details screen opened from a setlist** (`isDeletable`); Duplicate
  only on the songs screen's cards.
- `SongActions`' entries are builders run only while the menu is open, since every card composes one: pass a lambda,
  never a list built while composing the card.
- Choose setlists sits before the overflow on song rows and the details bar (into the menu when out of room), in a
  setlist row's menu after "Remove from setlist" (not on an archived setlist). From a details screen opened from a
  setlist, that setlist's box is disabled, since unticking it would pull the screen from under the reader.
- Of `SetlistActions`, only Choose songs ever becomes a header button.
