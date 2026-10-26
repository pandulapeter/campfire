<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# chordpro/chords

- **Every file and the model are in `STANDARD`** (`C D E F G A B`, `#`, `b`); German and Latin are ways of showing and
  typing, Nashville and Roman of showing only. Conversion happens only at two boundaries: `toNotation` (last rendering
  step) and `convertText` (the editor's field). An import writes standard; a file arriving otherwise is normalized the
  next time the editor saves it.
- **A text is read in the notation it is declared in**, except that `H`/`h` makes a standard text German, and Latin
  needs no declaring (no Latin name is a standard chord). `ChordProNotation.read` is the one reading path. A lowercase
  root is minor, a lowercase bass a note.
- **Shown chords draw `♯` and `♭`** (`withAccidentalSigns`, after `toNotation`); anything typed, stored, copied, or
  exported keeps `#` and `b`, and every reader takes both.
- Tab string names follow the notation; a system that could not come back exactly is left as written.
- **Numberings are display only**: counted from the song's key per modulated stretch, no key = letters, and nothing is
  ever read from numbers (`[1]` is as often an ending). Definitions follow the chords but do not vote on notation.

- `ChordProTransposer`: **a bracket moves only if
  `ChordProChordNames.isChordName` accepts all of it** (`[Chorus 2x]` stays). The text transposition leaves
  `{transpose}` alone, since each is relative to the song as written. A move's `capo` (a file's capo counted in to name
  the chords that sound) moves every chord name but no tab fret, tabs being counted from the capo.
- `ChordProChords.parse` reads a name through the same walk as `isChordName`, so a name is read exactly where it is
  recognized. **`Chord.id` (`C#:0.3.7.10`) is what player shape choices are stored under**, inside a `voicingKey`
  (the instrument id where it `drawsDefinitions`, else the tuning id); both are synced and stored, so never change
  either format.
