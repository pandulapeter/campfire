<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# chordpro/scales

- **12-TET only**: quarter-tone maqamat and ragas cannot be drawn on a fret or a key; their tempered relatives are in
  under an alias. **One entry per set of notes**: a second name is an alias, never a second scale (`ScalesTest`).
- **`Scale.id` keys the string resources and `DialogType.Scale`**, so it never changes; a scale is added, never renamed.
- Spelling is the chords' rule (`ChordProChords.spelledNoteNames`): one letter per degree, no double accidentals and no
  `Cb`, `Fb`, `E#`, `B#`, those falling back to the root's accidental, so a note reads the same in a chord sheet and a
  scale. Theory-correct spelling (C♯ major's E♯) would be a decision, not a fix.
- `ScaleChords.scalesContaining` is **root-anchored** (a C triad is in F major too, which is no help over a C), ignores
  a slash chord's bass, and never offers the chromatic scale, which holds every chord.
- **`SongScaleGroups`** splits a song only where it must: one group when a scale fits it whole, else sections join, in
  play order, the first group they still share a scale with (their own kind's, then the last, then any), so a verse
  after a modulated solo goes back to the verses. Groups are decided on scales of at most seven notes: an eight-note
  scale's passing tone would let a major verse and a Mixolydian chorus share one. Roots: the key in force (`{key}`, or
  the key a `{transpose}` names) where its scale holds the chords, else the best scored root (chords on it, the first
  and last, cadences into it) a major or minor scale holds them on, a Mixolydian or Dorian centre only where the part
  starts on it or the key names it. **The song is read as it stands**:
  the transposer has moved the chords after a `{transpose}` and renamed its key before a page shows them, so a
  `{transpose}` is never applied here, or a modulated verse would read two steps up. A chorus recall is counted again
  only where its chords differ from the chorus's (one after a `{transpose}`). Where no scale holds a part, a home scale (major, the minor family as one, Dorian, Mixolydian) holding two
  thirds of its chords answers, each chord it leaves out a `ScaleException` with seven-note scales holding it (a minor
  home's own harmonic minor, Dorian, or melodic minor where one does); below that floor the part is left out.
- **What is suggested is `PlayableScales`**, not bare containment: the seven-note home scale holding the chords, the
  pentatonic sharing its third and the blues (natural minor's for a harmonic or melodic minor home; the minor pentatonic
  and blues also after a Mixolydian home's major one, over a dominant blues with the I or IV a seventh, and over a rock
  riff of major chords on I, ♭III, IV, V, ♭VII), then the other common holders, other seven-note ones, hexatonics and
  smaller, eight-note last. A song's chips drop the eight-note ones where anything smaller fits.
- A chord sheet puts its quality's textbook scales first (`ChordScaleChoices`), and an altered dominant (♭9, ♭5, ♯5) is
  never a blues.
