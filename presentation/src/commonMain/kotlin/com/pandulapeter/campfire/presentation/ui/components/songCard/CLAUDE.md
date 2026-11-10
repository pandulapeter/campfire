<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->


# :presentation — ui/components/songCard

- Beside the cover: title, artist, and details (as the list's `SongCardOptions` say); a missing line closes up, labels go
  under all of it, 4dp under any last line, a title alone included, and 8dp under a cover. The title's line height is
  22sp with two lines and three alike, so a wrapped title is spaced the same in both; the cover is the text's height,
  58dp with three lines and 42dp with two (the card then lower), and animates.
- A song inside a setlist prefixes its one-based place, missing files included.
- The title carries its `{subtitle}` in parentheses, as everywhere: a "Live" copy by the same artist is told apart by
  nothing else.
- `CenteredSongCardContent` is laid out by hand, since a `ListItem` pins a three-line item to its top and grows it to
  88dp.
- `DragHandle` is narrower than the overflow button, so the button lands exactly where it does on the songs screen.
- The details line names the **sounding** key first (never in the title, which must not be pushed out): `{key}` moved
  by the file's `{transpose}`, the listing's transposition, and the capo played (`SongRenderer.renderKey`), so one song
  can read differently inside a setlist. The tempo follows in the muted color (120 where nothing names one), with the
  time signature unless 4/4. Both come from scanned metadata, never by parsing the file. "Lyrics only" replaces the key
  for a chordless song, but with Chords off the place is empty. Changes crossfade. Values are set apart by a gap only,
  since each starts with its own mark.
- **Under an artist header the artist is left off** (Songs sorted by artist, search closed): it fades and closes up,
  unless nothing else is under the title, and is still read out.
- Card text (or the cover) starts 8dp in, as far as a three-line card's cover stands from its top and bottom; headers
  follow (`SONG_CARD_TEXT_KEYLINE`). **In a window under 480dp wide** titles get two lines; touch targets stay.
- **Column width** (`minSongColumnWidth`): 292dp, +68dp while covers are on (whether or not a song has one, so arriving
  covers never change the count), so an unfolded iPhone Duo or Galaxy Z Fold8 on its side fits two columns beside the
  rail (the Duo's 84pt trailing inset included), and so does a large phone on its side. The details line adds nothing. Setlists use
  the same width, so both screens fit the same columns. Either sort keeps it too, though one sorted by artist leaves the
  artist off its cards: a change of sort never reflows the grid.
