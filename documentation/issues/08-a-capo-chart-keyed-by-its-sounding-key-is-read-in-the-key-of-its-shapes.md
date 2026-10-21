<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# A capo chart whose `{key}` is the sounding key is read in the key of its shapes (decision)

**Challenged:** amended — the shifted key is tried only on a part whose best scored root is the shifted root (with
capo 5 or 7 the shifted root is the shapes' V or IV, so `{key: C}{capo: 5}` `C Am D7 G`, C major with a II7, would read
as G major: G holds it whole); the `powerRiffFitOf` clause is dropped (Here Comes the Sun's bridge has no thirdless
chord, never reaches `powerRiffFitOf`, and A is a riff root of it anyway), so the bridge stays the riff on A as the
decision says (neither key holds it whole), and the test no longer expects roots `[D, D]`.

**Kind:** design  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/SongParts.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroups.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroupsTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`

HEAD at the time of writing: `f70a58f89`. Lane G, plan 8 of 9; assumes plans 01–07 have landed (plan 04 in
particular, which already moves Here Comes the Sun's verse and chorus to D).

## Problem

Both capo conventions exist in the wild. Many charts write `{key}` as the *sounding* key and the chords as shapes
(`{key: G}{capo: 5}` with `D G A Bm`: a capo-5 D shape sounds as G); others write the key of the shapes
(`{key: D}{capo: 5}` with the same chords). The scales engine reads the chords as written, which is right (the player
fingers D shapes and wants D scales under the capo), but it trusts the `{key}` as the shapes' key, so a chart of the
first convention is read in a key its chords never establish. At HEAD:

- `{key: G}{capo: 5}` `D G A Bm D G A D` → `[HOME] [Verse] G: major, major_pentatonic, lydian, harmonic_major (+3)` with
  `over A → A mixolydian, …`: G major holds six of eight (`A` has C♯) and names the only chord that would have told
  the key an exception.
- `{key: A}{capo: 7}` Here Comes the Sun, verse `D D G A7 ×2`, chorus `D G A7 D ×2`, bridge `F C G D A7 ×2` → at HEAD
  `A: mixolydian` for the verse and chorus (plan 04 reads them as D major, the key of the IV with the `A7` heard as a
  dominant) and `[Bridge] A: minor_pentatonic, …`, a rock riff on the key's root, where the bridge in D-shape terms is
  ♭III ♭VII IV I V, a riff on D.
- `{key: D}{capo: 5}` `D G A Bm D G A D` → `D: major, …`, right.

`ChordProMetadata.capo` (`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/model/ChordProMetadata.kt`,
`val capo: Int? = null`) is the capo fret, and `SongParts.of` reads only `song.metadata.key`.

## Decision for the user

Read the key shifted down by the capo as a second candidate: where the written key holds none of the part whole and
the shifted key does, the shifted key answers; where neither holds it whole, the written key keeps its word. Recommend
**yes**: a chart whose shapes establish the shifted key is of the first convention, and the only cost is one more
`keyFitOf` call on charts that have a capo. The alternative, always trusting the written key, keeps the HEAD output
above. A third option, preferring the shifted key outright wherever `{capo}` is set, would misread a chart of the
second convention with a borrowed chord (its written key then holds the part loosely and the shifted key not at all,
yet the shifted key's loose home would be tried first). The recommended rule never changes a part the written key
holds whole, and reaches for the shifted key only where it holds the part whole.

## Fix

- `SongParts.of`: read `song.metadata.capo` and give each `SongPart` a `capo: Int?` beside its `key` (a `{transpose}`
  renames the key; the capo stays the header's).
- `SongScaleGroups.Harmony`: add `val capoKey: SongKey?`, the key shifted down by the capo, only where the part is
  heard on it: `ranked.first().takeIf { it.pitchClass == (key.root.pitchClass - capo).mod(12) }` names the root,
  spelled as the chart spells its chord; `isMinor` as the written key's. Null where there is no capo, the capo is 0, or
  the part's best scored root ([rankedRoots]) is another. The shifted root of the common capos is a chord every song in
  the written key plays (capo 5: the V, capo 7: the IV), so "a chord sits on it" is no evidence of the convention: with
  it, `{key: C}{capo: 5}` `C Am D7 G` (C major, `D7` its V of V) would be held whole by G major and read in G, where
  the same chart without a capo reads C major with the `D7` as the moment. Its best scored root is C (plan 03's
  tie-break, the first chord), so it keeps the key; the charts of the first convention sit on the shapes' tonic (`D G A
  Bm D G A D`: D 7, G 2).
- `fitOf`: after the written key's `keyFitOf` and before `oneChordFitOf`, try the shifted key where the written key's
  answer is not TONAL:

  ```kotlin
  private fun fitOf(parts: List<SongPart>): Fit? {
      val harmony = Harmony(parts)
      powerRiffFitOf(harmony)?.let { return it }
      val written = harmony.key?.let { keyFitOf(harmony, it) }
      if (written?.level == Level.TONAL) return written
      harmony.capoKey?.let { keyFitOf(harmony, it) }?.takeIf { it.level == Level.TONAL }?.let { return it }
      return written ?: oneChordFitOf(harmony) ?: guessedFitOf(harmony)
  }
  ```

  `powerRiffFitOf` stays first and on the written key's root, unchanged: the riff steps are so many that a riff on the
  shapes' root is nearly always one on the written root too, and a riff's fit is never TONAL, so the decision's rule
  (the shifted key answers only where it holds the part whole) never moves one. Document the rule in the KDoc of
  `fitOf`.

### Expected probe output after the fix

- `{key: G}{capo: 5}` `D G A Bm D G A D` → `[HOME] [Verse] D: major, major_pentatonic, major_hexatonic, bebop_dominant (+1)`.
- `{key: D}{capo: 5}` the same chart → unchanged, `D: major, …`.
- Here Comes the Sun, `{key: A}{capo: 7}` → `[HOME] [Verse, Chorus] D: major, …` (as plan 04 already gives) and
  `[Bridge] A: minor_pentatonic, blues, …` unchanged: `F C G D A7` is held whole by neither A nor D, so the written key
  keeps its word (the decision), and its answer is `keyFitOf`'s rock riff on A (no chord of it is thirdless, so
  `powerRiffFitOf` never sees it), which the TONAL check leaves alone. Reading it on D would take a song-level notion of
  the convention (the other parts already read in the shifted key), which the decision did not ask for; left as is.
- `{key: C}{capo: 5}` `C Am D7 G C Am D7 G` → unchanged, `C: major, …` with `over D7 → D mixolydian, …` (C ranks first;
  the shifted key G is never tried).
- `{key: D}{capo: 2}` `C F G C Am F G C` (the corpus's "Capo shapes under the sounding key") → `C: major, …`, as today
  (the written key fails the floor and C is guessed; now also found as the shifted key, same answer).

## Tests

`SongScaleGroupsTest` (its `song(…)` helper takes only a key; add a `capo: Int? = null` parameter that writes
`{capo: N}` after `{key}`):

- `` `a capo chart keyed by its sounding key is read in the key of its shapes` ``: `song("D G A Bm D G A D", key = "G",
  capo = 5)` is `"D" to "major"`.
- `` `the written key keeps its word where it holds the part` ``: `song("D G A Bm D G A D", key = "D", capo = 5)` is
  `"D" to "major"`; `song("G C D G", key = "G", capo = 5)` is `"G" to "major"` (the shifted key D is never tried, the
  written one holding the part); `song("C F G C", key = "D", capo = 2)` is `"C" to "major"`; `song("C Am D7 G C Am D7 G",
  key = "C", capo = 5)` is `"C" to "major"` with exceptions `[D7]` (the shifted key G holds it whole, but the part is
  heard on C).
- `` `a capo key is named only where the part is heard on it` ``: `song("D G A Bm D G A D", key = "G", capo = 5)`'s
  group has `rootName == "D"` spelled as the chart spells its chord (write the chart with `[D]`; the root is never a
  made-up spelling), and `song("Eb Ab Bb Eb", key = "Ab", capo = 5)` (Ab major holds no `Bb`; the shapes are Eb, five
  frets under Ab) reads `"Eb" to "major"` with the root spelled `Eb`, not `D#`.

## CLAUDE.md

In the `SongScaleGroups` bullet of the scales `CLAUDE.md`, after "the key in force (`{key}`, or the key a `{transpose}`
names)": add ", or, where `{capo}` is set and that key holds nothing of the part whole, the key a capo's shapes play
(`{key: G}{capo: 5}` over D shapes is D), only where it holds the part whole and the part is heard on its root (capo 5's
shifted root is the V, which a II7 in C would otherwise move the part to)". The clause "(capo shapes in
C under `{key: D}` play no D, so they never read as D Mixolydian)" stays: it is still the reason the mode needs the
tonic chord, and that chart now also finds C as the shifted key.
