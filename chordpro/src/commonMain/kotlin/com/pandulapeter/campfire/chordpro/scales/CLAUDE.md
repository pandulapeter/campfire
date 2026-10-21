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
  play order, the first group they still share a scale with (one with a section of the same chords, then their own
  kind's, then the last, then any), so a verse after a modulated solo goes back to the verses. Groups are decided on
  scales of at most seven notes, or an eight-note scale's passing tone would join a major verse and a Mixolydian chorus,
  and of seven only the common scales and the modes of the major, harmonic minor, and melodic minor scales: a double
  harmonic or harmonic major scale holding a chart whole never names it, the odd chord is its key's exception.
  **A riff of power chords comes first**: where half the chords have no third, a riff on the key's root (else the
  scored one) is read before any mode holding it. **Then the key decides the root**: the key in force (`{key}`, or the
  key a `{transpose}` names, or, where `{capo}` is set and that key holds nothing of the part whole, the key a capo's
  shapes play (`{key: G}{capo: 5}` over D shapes is D), only where it holds the part whole and the part is heard on its
  root (capo 5's shifted root is the V, which a II7 in C would otherwise move the part to)): the key's own scale holding the part, then another key on the part's best scored root
  holding it whole where the key's tonic chord is only ever followed by that root's, inside the part at least once,
  and the part does not start on the key's root (a chorus on the IV is the IV's major, never the key's Mixolydian; the
  relative key and the I IV ♭VII IV loop are the key's), its mode holding the part, a minor key's family, a dominant
  blues, then its own major or minor family as a home with exceptions, its mode too where its tonic chord is played and
  it holds two chords more than the key's scale, or the tonic is mostly a seventh (capo shapes in C under `{key: D}` play no D, so they
  never read as D Mixolydian). Only where none fits is the root scored (chords on
  it, a dominant seventh only as the first chord, else the most played, with its target unplayed or played only as a
  seventh, the first and last, cadences into it, never into another dominant), a mode only where the part starts on it,
  a guessed root weighed by the chords its home scale alone holds; a part of one chord is answered
  as the chord's sheet is (a `Dm7` vamp is Dorian). A tonic seventh passing once among plain tonics is no blues but the
  key's moment. **The song is read as it
  stands**: the transposer has already moved the chords after a `{transpose}`, so it is never applied here, and a
  `{transpose}` inside a section starts a part. A key change the chart writes out (`WrittenModulations`) is read as
  the `{transpose}` it stands for: a section replaying an earlier one of its kind at one shift (four chords or more, the
  copy at most as long, never a fourth or fifth, where the same loop is the neighbouring key's far more often), the
  shift holding until another copy moves it; a chart that directs its key changes is read as directed. A chorus recall counts again only where its chords differ, and recalls
  of one set of chords once between them, so modulated last choruses never outweigh the song.
- Where no scale holds a part, a **home** (major, the minor family as one, Dorian, Mixolydian, and Phrygian dominant
  where a major ♭II chord is played: the Andalusian cadence, under a major key or guessed, counting only the chords it
  holds) holding half its chords answers; it may leave out two distinct chords or a third of them, except on the key's root, where the author's word
  stands however many it leaves out; a chord leading into the home (a secondary dominant, a diminished chord a semitone
  under a home note, the ii before such a dominant; never a chord on a minor home's own root,
  nor one on Mixolydian's fifth) is the home's for that
  cap and for the two-thirds level, never for the half floor, and is still named as an exception; a rock riff counts no
  leaders, and neither does the key's root for a part that has left the key (its best scored root's major or minor
  scale holds more of it than the key's does: a bridge in the key of the V stays in that key). Each chord left out is a
  `ScaleException` (`ScaleExceptions`: a minor
  home's own minor scales, Phrygian for its ♭II, a Phrygian-dominant home's Phrygian for its ♭III, else the closest of a
  secondary dominant's target harmonic minor (the target a note of the key, and on a tie the minor chord played), a
  diminished chord's target a semitone up where a chord on it follows (else its own diminished scale, the one answer of
  eight notes), the chord's root, and the home's root, never a harmonic or melodic minor there, ties in that order). Below two thirds a
  part stands alone but never joins another; a home of two of three chords is a guess dropped beside any other group.
  A **rock riff** is power, suspended, or major chords on I, ♭III, IV, V, ♭VI, ♭VII (a thirdless one on ♭V too, the
  blues note) with a ♭III or ♭VI one chord in eight (♭VII alone is Mixolydian's), or any ♭III, ♭VI, or ♭VII with no
  third anywhere; it leads with its minor pentatonic and blues, has no exceptions, and a riff's group takes any part on
  its root in natural minor, Dorian, or Mixolydian, or on a home with exceptions, and, where the key names the root, a
  guess on the riff's steps or the ♭II; never a part in the parallel major. A vamp on one root (a sus intro) joins
  whatever the song puts on that root, taking its fit, where that fit names none of the vamp's chords as an exception.
- **`SongScaleSuggestions`** is what a song shows, groups offering one first scale on one root joined (never two rows
  saying the same): the home (`ScaleGroup.isHome`, the group playing half the chords), then at most three moments,
  the other keys (not a relative one, whose notes are the home's), parts on a centre of their own that hardly play the
  root and never cadence into it (a bridge on the IV, offered as the scale on it holding most of its chords; a chorus
  whose V→I lands home is the home's), the exceptions, and dominants whose
  third the group's first scale lacks (a blues's IV7 and V7). A section is one moment, a centre before a key, and
  chords whose scales are one set of notes are one (a V and its V7; a ii–V of another key, named by that key's major,
  or by the home's own scale where one of them is on the home root or the notes are the home root's major, natural
  minor, Dorian, or Mixolydian); a chord counts only where the row it is played under leaves it out. A section is weighed by its share of the chords, a chord by its share of the plays (it may be held),
  and it must be one chord in 32 or come back in two plays; a key or an exception counts double. Nothing fills an
  unused slot. With no home, every group is shown.
- **What is suggested is `PlayableScales`**, not bare containment: the seven-note home scale holding the chords, the
  pentatonic sharing its third (the major and the minor; the minor 6 and dominant pentatonics are offered only where
  they hold the chords, as any other scale) and the blues (a group's row is built on the group's home, every other holder of its
  third's opposite dropped, so a minor home over power chords never offers the parallel major; a part of power chords
  alone, read without a key, has no third to keep; natural minor's for a harmonic or melodic minor home; the minor pentatonic
  and blues also after a Mixolydian home's major one, over a dominant blues (every chord major on I, IV, V, or every
  chord on the I a seventh, the I the most played root, no major seventh on I, IV, V, and those steps more than half
  the plays; the I or the IV mostly a seventh, or a V–IV turnaround in a part half on its I with a seventh in it), a
  jazz blues's departures named against its Mixolydian, and ahead of the major scale where that holds the blues
  (triads and a V7), and over a rock riff), then the other common holders, other seven-note ones (the seven-note ones
  outside those families never lead), hexatonics and smaller, eight-note last. A song's chips drop the eight-note ones
  where anything smaller fits.
- A chord sheet puts its quality's textbook scales first (`ChordScaleChoices`), and an altered dominant (♭9, ♭5, ♯5) is
  never a blues.
