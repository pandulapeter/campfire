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

- **The transposition wraps around the octave** (−5 to +6), when stepped and when read, so its buttons are never
  disabled.

### Tempo and capo overrides

- **Tempo and capo overrides mirror the transposition** (one `SongOverrides`): a song opened from a setlist reads that
  setlist's entry (left out of the file where null, so it travels through export, import, and sync), one opened from the
  library `UserPreferences` (never exported, but synced). **Neither reads the other.** A song put into a setlist starts
  from the library's three overrides, copied once into its new entry, so it keeps its key, capo, and speed; with none
  the entry stays null and follows the file. The file's values change only in the editor and Song defaults.
- Effective value: entry, then override, then the file, then 120 (tempo) or none (capo, within `Song.CAPO_RANGE`). An
  override equal to the song's own counts as none. The tempo counts the bar's clicks (6/8 at 120 is six clicks a bar at
  120 a minute).
- The first `{tempo}` and `{time}` are the song's own (later ones are changes, `ui/metronome/`); for `{capo}` the first
  readable one counts, a later one flagged as a contradiction in the editor.
- A capo is per setlist entry because one set plays a song capoed and the next in another key without. **Zero is a
  value, not nothing**: a capo this setlist takes off a song whose file asks for one.
- A stepped tempo or capo is held pending and written once the stepper has held still for half a second: a setlist write
  is a sync run, and a held stepper steps every few frames. A pending value is dropped once its write landed and the
  store agrees; a reset removes the override at once and cancels a waiting write.
- Writes still waiting are made in `onCleared` on a scope of their own, and awaited before the desktop process ends (a
  macOS Quit never clears the view model), as are the metronome settings, the text size, and the export options.

### What sounds and what is fingered

- **Every capo keeps the shapes and raises the pitch** (unless the reader keeps the key, below), the file's `{capo}` and
  one a setlist or the device puts on alike: the file's chords are the shapes fingered above the capo played, so the
  shapes are the written chords moved by the transposition alone, and a capo change never moves a shape, a `{define}`,
  or a tab's fret.
- **What sounds** (`soundingTransposition`) is the file's chords moved by the transposition and the effective capo: the
  header's key, the cards and rows, the scales, and the Chords section's notes and degrees read it. A `{capo: 2}` file
  in Em sounds in F#m, at capo 3 in Gm; a setlist's capo 0 plays it open, two semitones lower.
- **A song's chords are named by the reader's choice** (`UserPreferences.chordNames`): `SHAPES` (default) names the
  shapes fingered above the capo, `SOUNDING` what sounds; the keyboard always sounds. `namingTransposition` moves every
  name on the page (lyrics, comments, key-change lines, the Transposition control's key, the Chords section, the chords
  the Scales section names, a staff's chord symbols, the chord sheet's title, the PDF); under `SHAPES` a capo renames
  none of them. The page reads the song twice (once sounding, for the Chords and Scales sections), and the key line
  names the sounding key then the shapes' ("Capo 2 (Em shapes)"; the PDF's capo value the same, so the importer still
  reads it).
- **The diagrams are the shapes**: the sounding chords moved down by the effective capo; on the keyboard what sounds.
  Since a capo raises the pitch for everybody, the keyboard has the capo stepper, the capo in the read only line and the
  PDF, and Song defaults, so a pianist sees why the key moved and can set the band's capo. Where the reader keeps the
  key, all but Song defaults (the file's `{capo}`, still pitch) leave it out for the keyboard, the overrides card's
  reset included.
- **Capo keeps the key** (`capoKeepsKey`): the reverse reading, a capo changing only the fingering. The pitch counts the
  file's `{capo}` alone, so a capo a setlist or the device puts on keeps what sounds, and the shapes, `{define}`s (held
  only where a hand can), and tabs' frets move down by it instead. A reading, not a write: switching it rereads every
  song at once, the transposition stepper keeps showing only the reader's own transposition, and one setlist capo sounds
  differently on two devices that read capos differently.
- **Tabs move with the shapes**: counted from the capo they are played above, they move by the transposition alone.
- The scales read the song with the file's capo line, which says how its `{key}` is meant; the page's capo line is the
  effective one, on every instrument.
