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

The song card the song lists and setlists draw (`SongListItem`).

- Beside the cover stand three lines: the title, the artist, and the details (key, tempo, and duration, each as the
  list's `UserPreferences.CardDetails` picks) in `labelMedium`; a missing artist or no details closes its line up.
  Labels go under all of it. With all three the title takes `bodyMedium`'s line height (its own leaves a wider gap under
  it than between the others) and the cover is 56dp; with two (under an artist header, typically) the cover is 44dp and
  the card lower. Both animate (`isThreeLine`).
- A song inside a setlist prefixes its one-based place, missing files included.
- The title carries its `{subtitle}` in parentheses, as everywhere else: a song's "Live" copy by the same artist is told
  apart by nothing else.
- `CenteredSongCardContent` lays the body out by hand rather than as a `ListItem`, which pins a three-line item to its
  top and grows it to 88dp.
- `DragHandle` is a plain icon, narrower than the overflow button, so the button lands exactly where it does on the
  songs screen; the caller makes it draggable.
- `SongListItem` names the **sounding** key first on the details line (never the title, which must not be pushed out): `{key}`
  moved by the file's `{transpose}`, the listing's transposition, and its capo (`SongRenderer.renderKey`), so one song
  can read differently inside a setlist. The tempo follows (`EffectiveTempo.bpm`, 120 where nothing names one), in the
  muted color so it doesn't compete with the key. Both come from scanned metadata, never by parsing the file. "Lyrics
  only" replaces the key for a chordless song — but with the Chords switch off the place is empty. Changes crossfade
  (`SongCardNote`). Tempo and duration are `IconLabel`s (metronome at rest, clock), as is a setlist's
  countdown; the tempo adds the time signature unless it is 4/4. Values are set apart by a gap only, since
  each starts with its own mark (`SongCardGap`).
- **Under an artist header the artist is left off** (`isArtistShown`, Songs sorted by artist, search closed): it fades
  and closes up, unless nothing else is under the title (the card keeps its height), and is still read out.
- **In a window under 480dp wide** card text uses 8dp padding (and headers follow, `songCardTextKeyline`) and titles get
  two lines; touch targets stay.
- **Column width** (`minSongColumnWidth`): a 336dp base, +68dp for the cover while covers are on (whether or not a song
  has one, so arriving covers never change the count), so the default card keeps 404dp and a phone on its side stays one
  column. The key, tempo, and duration stand on a line of their own and add nothing. Setlists are laid out by the same
  width (the slot number comes out of the title), so both screens fit the same columns. `CampfireContent` computes it
  and `CampfireScreens` hands it to `ListLayout.of`. Sorted by artist, the Songs grid (not its side panel or the
  expanded rail, so sorting never moves the chrome) takes 8dp off (`songGridColumnWidth`), the room the card's artist
  line no longer needs.
