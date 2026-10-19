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
  the key a `{transpose}` names), then the first, last, and most played chord roots. **The song is read as it stands**:
  the transposer has moved the chords after a `{transpose}` and renamed its key before a page shows them, so a
  `{transpose}` is never applied here, or a modulated verse would read two steps up. Chorus recalls are not counted
  again; a section no scale holds is left out.
- **What is suggested is `PlayableScales`**, not bare containment: the scales holding the chords, the major and minor
  pentatonics and hexatonics inside the seven-note home scale (the safe choice, though five notes never hold a key's
  chords), the blues scale wherever the minor pentatonic is, and over a dominant blues (major chords on I, IV, V, the I
  a dominant seventh) the minor pentatonic, blues, and major ones too, its flat third being the style. Common holding
  scales first, then these, then the rest, so a capped list keeps them.
