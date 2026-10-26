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

- **The transposition wraps around the octave** (`wrapTransposition`, −5 to +6), when stepped and when read, so its
  buttons are never disabled.

## Where a tempo lives

- **Overrides mirror the transposition** (`SongOverrides<Int>`, keyed by a `SongPlace`; `Tempos`, `Capos`, and
  `Transpositions` are all one): a song opened from a setlist reads that setlist's entry (`Setlist.Entry.tempo`, left out
  of the `*.setlist.json` where null, so it travels through export, import, and sync), one opened from the library
  `UserPreferences.tempos` (never exported, but synced, see `data/sync/implementation/.../preferences/CLAUDE.md`). **Neither reads the
  other.** The file's `{tempo}` is changed only in the editor and the Song defaults sheet.
- `effectiveTempo` (`SongTempo.kt`): entry, then override, then `Song.tempo` (30–300), then 120. An override equal to the
  song's own counts as none. The tempo counts the bar's clicks (6/8 at 120 is six clicks a bar at 120 a minute).
- The first `{tempo}` and `{time}` are the song's own (later ones are changes, see `ui/metronome/`); for `{capo}` the
  first readable one counts, a later one being flagged as a contradiction in the editor.
- A screen needing transposition, capo, and tempo together collects one `playingOverrides` and asks `songPlaybackOf`
  (`SongPlayback.kt`).

### Pending writes

- `changeTempo` sets an absolute override in a `PendingOverrides` (`PendingOverrides.kt`), which overlays `tempos` for
  the stepper, the page, and the click, and writes once the stepper has held still for half a second: a setlist write is
  a sync run, and a held stepper steps every few frames. A pending value is dropped once its write has landed and the
  store agrees.
- Writes still waiting are made on a scope of their own in `onCleared`, and awaited before the desktop process ends
  (`settleSynchronizationBeforeExit`; a macOS Quit never clears the view model), as are the metronome settings, the text
  size, and the export options.
- `resetTempo` removes the override at once and cancels a waiting write.

## Where a capo lives

- The tempo's twin in every way (`SongCapo.kt`): `Capos`, its own `PendingOverrides`, `effectiveCapo` (entry, override,
  file's `{capo}`, none; within `Song.CAPO_RANGE`), `stepCapo`, `resetCapo`, and `withCapo` (the page's capo line and a
  setlist's PDF). Stored in `Setlist.Entry.capo` and `UserPreferences.capos`, since one set plays a song capoed and the
  next in another key without.
- **Zero is a value, not nothing**: a capo this setlist takes off a song whose file asks for one.
- The Chords and Scales tabs have one capo of their own (`UserPreferences.toolCapo`, `ui/instrument`), read the same
  way (names sound, shapes are fingered above it) and never a song's.

### What sounds and what is fingered

- **The page names what sounds** (`SongPlayback.soundingTransposition`): the file's chords moved by the transposition and
  the **file's** `{capo}`, which only says which shapes the file is written in. Chords, comments' chords, the chord
  sheet, the Transposition control's key, key-change lines, the header, the cards, the scales, and the PDF all read it.
- **A capo put on keeps the pitch**: the shapes are the sounding chords moved down by the effective capo
  (`shapeCapoFor`, none on the keyboard, `playsWithoutCapo`). Capo 2 on a song in A draws G shapes; a setlist's capo 0
  on a `{capo: 2}` G-shape file plays open A shapes. Saved overrides changed meaning with this; nothing was migrated.
- Tabs stay as written, counted from the capo: `soundingCapo` is held out of their frets (`ChordProTransposer`'s `capo`).
- The scales read the song with the file's capo, which says how its `{key}` is meant (`SongKeys`); the page's capo line
  is the effective one (`withCapo`), and `withoutCapo` takes it off the keyboard's PDF.
