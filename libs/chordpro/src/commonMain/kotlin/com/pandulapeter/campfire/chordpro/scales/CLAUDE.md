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
  `Cb`, `Fb`, `E#`, `B#`, so a note reads the same in a chord sheet and a scale. Theory-correct spelling (C♯ major's
  E♯) would be a decision, not a fix.
- `ScaleChords.scalesContaining` is **root-anchored** (a C triad is in F major too, which is no help over a C), ignores
  a slash chord's bass, and never offers the chromatic scale, which holds every chord.
- A chord sheet puts its quality's textbook scales first (`ChordScaleChoices`), and an altered dominant (♭9, ♭5, ♯5) is
  never a blues.

## Reading a song (`SongScaleGroups`, `SongScaleGroupsTest`)

Every rule below is pinned by a test named for the song or progression it was made for; read those before changing a
weight. What the tests do not say is the order of the decisions and why each comes where it does.

- **A song is split only where it must**: one group when a scale fits it whole, else sections join, in play order, the
  first group they still share a scale with, so a verse after a modulated solo goes back to the verses. Groups on one
  root where one's chords hold the other's are one (an intro's ♭VII reads as Mixolydian by its plays, but its chords
  are the chorus's).
- Groups are decided on scales of at most seven notes (an eight-note scale's passing tone would join a major verse and
  a Mixolydian chorus), and of seven only the common scales and the modes of the major, harmonic minor, and melodic
  minor scales: an exotic scale holding a chart whole never names it; the odd chord is its key's exception.
- **The decisions, in order**: a riff of power chords (half the chords without a third) first; **then the key decides
  the root** — the key in force (`{key}`, a `{transpose}`'s key, or, under `{capo}`, the key the capo's shapes play
  where that holds more of the plays, a tie keeping the written key; `Harmony.shapesKey`), its own scale holding the
  part, then another key on the part's best scored root where the key's tonic only leads there (a chorus on the IV is
  the IV's major, never the key's Mixolydian), then a home with exceptions. **Only where nothing fits is the root
  scored** (chords on it, first and last, cadences into it), a mode only where the part starts on it.
- **The song is read as it stands**: the transposer has already moved the chords after a `{transpose}`, so it is never
  applied here, and a `{transpose}` inside a section starts a part. A key change the chart writes out
  (`WrittenModulations`: a section replaying an earlier one at one shift, never a fourth or fifth, where the same loop
  is the neighbouring key's far more often) is read as the `{transpose}` it stands for.
- A chorus recall counts again only where its chords differ, so modulated last choruses never outweigh the song.
  Nothing is worked out for a song playing more than `MAX_PLAYED_CHORDS` chords: grouping grows with sections times
  chords.
- Where no scale holds a part, a **home** (major, the minor family, Dorian, Mixolydian, or Phrygian dominant where a
  major ♭II is played) holding half its chords answers, each chord left out a `ScaleException` (`ScaleExceptions`). On
  the key's root the author's word stands however many it leaves out. A chord leading into the home (a secondary
  dominant and the like) counts as the home's for its caps but never for the half floor, and is still named. Below two
  thirds a part stands alone but never joins another.
- A **rock riff** (power, suspended, or major chords on the blues steps with a ♭III or ♭VI) leads with its minor
  pentatonic and blues, has no exceptions, and never takes a part in the parallel major. A vamp on one root joins
  whatever the song puts on that root.

## What a song shows

- **`SongScaleSuggestions`**: the home (the group playing half the chords), then at most three moments: other keys
  (not a relative one, whose notes are the home's), parts on a centre of their own that never cadence home, the
  exceptions, and dominants whose third the group's scale lacks. A section is one moment, and chords whose scales are
  one set of notes are one; a part in another key comes first, then sections and chords by their share of the plays.
  Nothing fills an unused slot. With no home, every group is shown.
- **What is suggested is `PlayableScales`**, not bare containment: the seven-note home scale, the pentatonic sharing
  its third and the blues, then the other common holders, eight-note ones last (a song's chips drop them where
  anything smaller fits). Every holder of the opposite third is dropped, so a minor home over power chords never
  offers the parallel major. Over a dominant blues the minor pentatonic and blues follow a Mixolydian home's major one.
- **`SongKeys` is the one reading of `{key}`** everything naming a song's key goes through (header, lists,
  Transposition control, degrees, scales): a capo chart's written key is its chords' own unless the capo's shapes' key
  holds more of the plays. Shift-invariant, so the sounding song reads as the file does. `keysOfChords` is the key
  each chord's first play is in after a key change, which the degrees count from.
