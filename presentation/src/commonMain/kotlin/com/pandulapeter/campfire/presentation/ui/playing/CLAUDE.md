<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/playing

The values a song is played with and where each is kept.

- **Where a capo lives** (`playing/SongCapo.kt`, tested): the tempo's twin in every way, down to the
  pending writes — `Capos` (the same `SongOverrides<Int>`), a `PendingOverrides` of its own, `effectiveCapo` (the setlist's entry, then the library's override, then the file's
  `{capo}`, then no capo, an override equal to the song's own counting as none, within `Song.CAPO_RANGE`), `stepCapo`,
  `resetCapo` and `withCapo`, which is what puts an override on the page's capo line and in a setlist's PDF. Zero is a
  value rather than nothing there: a capo this setlist takes off a song whose file asks for one.
- **Where a tempo lives** (`playing/SongTempo.kt`, tested): `Tempos` is a `SongOverrides<Int>`
  (`playing/SongOverrides.kt`, tested, which `Capos` and `Transpositions` are too, keyed by a `SongPlace`) — a song opened
  from a setlist reads that setlist's entry, one opened from the library `UserPreferences.tempos`, never the other — and
  `effectiveTempo` is entry, then override, then the file's `{tempo}` (`Song.tempo`, held within 30–300), then 120,
  an override equal to the song's own counting as none. A screen that reads the transposition, the capo and the tempo
  together collects them as one `playingOverrides` and asks `songPlaybackOf` (`playing/SongPlayback.kt`, tested).
  `changeTempo` sets an absolute override in its `PendingOverrides`
  (`playing/PendingOverrides.kt`, tested, the pending, debounced, settled and flushed half of every such override),
  which overlays `tempos` for the stepper, the page and the click, and writes it once the stepper has held still for
  half a second (a setlist write is a sync run, and a held stepper steps every few frames); a pending value is let go of
  once its write has landed and the store says the same, and the writes still waiting are made on a scope of their own
  in `onCleared`, and awaited before the desktop process ends (`settleSynchronizationBeforeExit`, a macOS Quit included,
  which never clears the view model), as are the metronome settings, the text size and the export options. A reset (`resetTempo`) removes the override at once and cancels a waiting write. The settings
  (`metronomeSettings`) are saved like the print settings, once they have held still.

## Where a tempo lives

- **Where a tempo lives mirrors the transposition**: a song opened from a setlist keeps an override in that setlist's
  entry (`Setlist.Entry.tempo`, a `tempo` member of the `*.setlist.json` song, left out where null, so it travels
  through an export, an import and a sync run), one opened from the library in `UserPreferences.tempos`, never exported
  but synced (see Sync); neither reads the other, and the song file's `{tempo}` (`Song.tempo`, read at scan time with `{time}` and
  `{capo}`) is only changed in the editor and the Song defaults sheet. The capo is kept the same way (`Setlist.Entry.capo`, `UserPreferences.capos`,
  0 to 12 frets, a stored 0 being a capo this setlist takes off rather than no override at all), since one set is
  played capoed and the next in another key without. The first `{tempo}` and `{time}` are the song's own (the capo is the song's as a whole, so its first readable `{capo}` counts, as for any other field a song says once, a later one being marked as a contradiction in the editor); the tempo counts the
  clicks of the bar (6/8 at 120 is six clicks a bar at 120 a minute), within 30–300.
