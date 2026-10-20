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
- `SongScaleGroups.Harmony`: add `val capoKey: SongKey?`, the key shifted down by the capo, spelled by a chord of the
  part on that pitch class: `roots.firstOrNull { it.pitchClass == (key.root.pitchClass - capo).mod(12) }` names the
  root (a part that never plays the shifted key's tonic chord gives no evidence of the convention, so none is tried);
  `isMinor` as the written key's. Null where there is no capo, the capo is 0, or no chord sits on the shifted root.
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

  `powerRiffFitOf` stays first and on the written key's root today; with a capo it should also prefer the shifted key's
  root where the written key's root is on no riff step of the chords (`harmony.capoKey?.root` when
  `!PlayableScales.isRockRiff(writtenRoot, chords)` and the shifted root is a riff root), so a bridge of power chords in
  the shapes' key reads on the shapes' root. Document both in the KDoc of `fitOf`.

### Expected probe output after the fix

- `{key: G}{capo: 5}` `D G A Bm D G A D` → `[HOME] [Verse] D: major, major_pentatonic, major_hexatonic, bebop_dominant (+1)`.
- `{key: D}{capo: 5}` the same chart → unchanged, `D: major, …`.
- Here Comes the Sun, `{key: A}{capo: 7}` → `[HOME] [Verse, Chorus] D: major, …` (as plan 04 already gives) and
  `[Bridge] D: minor_pentatonic, blues, mixolydian, …` (a riff on D under the shifted key; its scales as a riff's:
  `rockRiffScales + home + over(D, held)`); the bridge stays a row of its own, shown as another key of the home, since
  a riff never joins a major group (plan 05 keeps that).
- `{key: D}{capo: 2}` `C F G C Am F G C` (the corpus's "Capo shapes under the sounding key") → `C: major, …`, as today
  (the written key fails the floor and C is guessed; now also found as the shifted key, same answer).

## Tests

`SongScaleGroupsTest` (its `song(…)` helper takes only a key; add a `capo: Int? = null` parameter that writes
`{capo: N}` after `{key}`):

- `` `a capo chart keyed by its sounding key is read in the key of its shapes` ``: `song("D G A Bm D G A D", key = "G",
  capo = 5)` is `"D" to "major"`; `song("D D G A7 D D G A7", "F C G D A7 F C G D A7", key = "A", capo = 7)` has roots
  `[D, D]`.
- `` `the written key keeps its word where it holds the part` ``: `song("D G A Bm D G A D", key = "D", capo = 5)` is
  `"D" to "major"`; `song("G C D G", key = "G", capo = 5)` is `"G" to "major"` (the shifted key D is never tried, the
  written one holding the part); `song("C F G C", key = "D", capo = 2)` is `"C" to "major"`.
- `` `a capo key is named only by a chord of the part on it` ``: `song("D G A Bm D G A D", key = "G", capo = 5)`'s
  group has `rootName == "D"` spelled as the chart spells its chord (write the chart with `[D]`; the root is never a
  made-up spelling), and `song("Eb Ab Bb Eb", key = "Ab", capo = 5)` (Ab major holds no `Bb`; the shapes are Eb, five
  frets under Ab) reads `"Eb" to "major"` with the root spelled `Eb`, not `D#`.

## CLAUDE.md

In the `SongScaleGroups` bullet of the scales `CLAUDE.md`, after "the key in force (`{key}`, or the key a `{transpose}`
names)": add ", or, where `{capo}` is set and that key holds nothing of the part whole, the key a capo's shapes play
(`{key: G}{capo: 5}` over D shapes is D), named only where a chord of the part sits on it". The clause "(capo shapes in
C under `{key: D}` play no D, so they never read as D Mixolydian)" stays: it is still the reason the mode needs the
tonic chord, and that chart now also finds C as the shifted key.
