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

- A song inside a setlist prefixes its one-based place, missing files included.
- `CenteredSongCardContent` lays the body out by hand rather than as a `ListItem`, which pins a three-line item to its
  top and grows it to 88dp.
- `DragHandle` is a plain icon, narrower than the overflow button, so the button lands exactly where it does on the
  songs screen; the caller makes it draggable.
- `SongListItem` names the **sounding** key next to the artist (never the title, which must not be pushed out): `{key}`
  moved by the file's `{transpose}`, the listing's transposition, and its capo (`SongRenderer.renderKey`), so one song
  can read differently inside a setlist. The tempo follows (`EffectiveTempo.bpm`, 120 where nothing names one), in the
  muted color so it doesn't compete with the key. Both come from scanned metadata, never by parsing the file. "Lyrics
  only" replaces the key for a chordless song — but with the Chords switch off the place is empty. Changes crossfade
  (`SongCardNote`).
- **In a window under 480dp wide** card text uses 8dp padding (and headers follow, `songCardTextKeyline`) and titles get
  two lines; touch targets stay.
- **Column width** (`minSongColumnWidth`, from `UserPreferences.CardDetails` and the chords/metronome switches): 368dp
  base, +16dp each for key and tempo, +32dp for duration, so the default Songs card keeps 400dp and a phone on its side
  stays one column. Setlists use the same widths (the slot number comes out of the title), so the same details flow
  into the same columns on both screens. `CampfireScreens` computes both into `ListLayout.of`.
