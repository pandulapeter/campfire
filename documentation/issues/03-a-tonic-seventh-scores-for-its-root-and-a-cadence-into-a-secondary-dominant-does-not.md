<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# A tonic seventh scores for its root, a cadence into a secondary dominant scores nothing, and a guessed root is weighed by its home scale

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroups.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroupsTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`

HEAD at the time of writing: `f70a58f89`. Lane G, plan 3 of 9; assumes plans 01 and 02 have landed.

## Problem

Three charts without a `{key}` are heard on the wrong root. At HEAD the probe prints:

- Jazz blues in F, `F7 Bb7 F7 Cm7 F7 Bb7 Bdim7 F7 Am7 D7 Gm7 C7 F7 D7 Gm7 C7` → `[HOME] [Chorus] G: natural_minor, …`.
- Fly Me to the Moon, `Am7 Dm7 G7 Cmaj7 Fmaj7 Bm7b5 E7 Am7 A7 Dm7 G7 Cmaj7 Am7 Dm7 G7 Cmaj7 Bm7b5 E7` →
  `[HOME] [A] A: natural_minor, …` with `over E7 → A harmonic_minor`, `over A7 → D harmonic_minor`.
- All of Me, first sixteen bars, `Cmaj7 Cmaj7 E7 E7 A7 A7 Dm7 Dm7 E7 E7 Am7 Am7 D7 D7 Dm7 G7` → `[HOME] [A] A:
  natural_minor, …` with `E7`, `A7`, `D7` moments.

Two rules of `rankedRoots` are at fault:

```kotlin
parts.forEach { part ->
    part.chords.forEachIndexed { index, chord ->
        if (!chord.isDominantSeventh) {
            scores[chord.root] = (scores[chord.root] ?: 0) + 1
            counts[chord.root] = (counts[chord.root] ?: 0) + 1
        }
        val next = part.chords[(index + 1) % part.chords.size]
        if (MAJOR_THIRD in chord.intervals && (chord.root - next.root).mod(12) == FIFTH) {
            scores[next.root] = (scores[next.root] ?: 0) + CADENCE_SCORE
        }
    }
}
```

A dominant seventh never scores for its root, which is right for a V7 and wrong for a blues's I7: in the F jazz blues
`F7`, played five times, gives F nothing, so F has 5 (the first chord, two `C7 → F7` cadences) against G's 6 (`Gm7`
twice, `D7 → Gm7` twice). And a cadence scores whatever it lands on, so in All of Me the `E7 → A7` of bars 3–4, a
dominant leading into another dominant, counts as a cadence into A: A has 6 (`Am7` twice, `E7 → A7`, `E7 → Am7`), C 5
(`Cmaj7` twice, the first chord, `G7 → Cmaj7` around the loop), D 5 (`Dm7` thrice, `A7 → Dm7`).

Then `guessedFitOf` sorts the candidate roots (the best scored and the first chord's) by what a home accounts for,
the minor family counting as one home:

```kotlin
val roots = listOfNotNull(harmony.ranked.first(), harmony.roots.first())
    .distinctBy { it.pitchClass }
    .map { it to looseCountsOn(harmony, it) }
    .sortedByDescending { (_, counts) -> counts.max() }
```

In Fly Me to the Moon C ranks first (9 against A's 8), but A's family holds 17 of 18 (`E7` by harmonic minor) against
C major's 15, so A is tried first and A natural minor, holding 15 and leaving `E7` and `A7` as exceptions, wins. With
plan 01 landed, `E7` and `A7` lead into both C major and A minor and are accounted for by both (18 against 18), so the
tie already goes to C by rank; this plan makes the rule explicit so that it holds without the coincidence.

## Fix

In `SongScaleGroups.rankedRoots`:

1. **A tonic seventh scores.** A dominant seventh scores for its root like any chord where it is the part's first
   chord, or where it is played at least as often as every other chord and no chord sits on its target root (a fourth
   above it): a dominant that leaves to nowhere is the tonic. `F7` in the jazz blues is the first chord; `E7` in All
   of Me is the most played chord but `A7` and `Am7` are played, so it stays a secondary dominant. Compute the set of
   such *tonic sevenths* once per part (by chord, `part.chords.first()` plus the most-played rule) and treat them as
   non-dominant in both `scores` and `counts`.
2. **A cadence into a dominant seventh that is no tonic seventh scores nothing.** In the cadence branch, add
   `&& (!next.isDominantSeventh || next in tonicSevenths)`. `C7 → F7` in the blues still scores for F (a tonic seventh);
   `E7 → A7` in All of Me no longer scores for A. `E7 → Am7` and `A7 → Dm7` are untouched.
3. **Ties go to the first chord's root before the count of non-dominant chords.** The `sortedWith` becomes score, then
   first chord, then count, then last chord. After 1 and 2 All of Me has C and D at 5; the chord a part starts on
   says more than one more minor chord. (Check the test `a key is read from the chords, not from the first chord`: its
   four charts are decided by score, not by the tie-break, so it stays true.)

In `guessedFitOf`, sort the candidate roots by what the home scale itself accounts for, held or leading, the minor
family counting only for the floor in `looseFitOf`, the outside count, and the exceptions:

```kotlin
.map { it to looseCountsOn(harmony, it) }
.sortedByDescending { (root, _) -> looseHomes.maxOf { home -> harmony.chords.count { holdsChord(home, root.pitchClass, it) || leadsInto(home, root.pitchClass, it) } } }
```

(`leadsInto` is plan 01's predicate. A small private helper `scaleCountOn(harmony, root, home)` reads better than the
inline lambda; `looseCountsOn`'s family rule stays for the counts passed on to `looseFitOf`.)

Update the KDoc of `rankedRoots` (the paragraph "A root scores one for each chord on it but a dominant seventh…") and
the "A home with exceptions" bullet of `guessedFitOf`'s KDoc.

### Before and after

- Jazz blues in F without a key: HEAD `G: natural_minor` (with plan 02 alone it already reads `F: minor_pentatonic`,
  because G is no blues and the `ranked` loop in `tonalFitOf` reaches F). After this plan F ranks first outright
  (`F7` scores 5, the first chord 1, two cadences 4: 10 against G's 6) → `[HOME] [Chorus] F: minor_pentatonic, blues,
  major_pentatonic, mixolydian (+1)` with the `Bb7`, `D7`, `C7` moments of plan 02.
- Fly Me to the Moon without a key: HEAD `A: natural_minor`; after → `[HOME] [A] C: major, major_pentatonic,
  major_hexatonic, bebop_dominant (+1)` with `over E7 → A harmonic_minor` and `over A7 → D harmonic_minor`, the same
  row as the chart with `{key: C}` gets today.
- All of Me without a key: HEAD `A: natural_minor`; after → `[HOME] [A] C: major, …` with `over E7 → A harmonic_minor`,
  `over A7 → D harmonic_minor`, `over D7 → D mixolydian, …`, the same as plan 01 gives the keyed chart.
- Gospel without a key (plan 01): unchanged, C ranks first with or without this plan.

## Tests

`SongScaleGroupsTest`:

- `` `a blues is heard on its tonic seventh, not on the chord its V leads to` ``: `song(F jazz blues)` without a key is
  `"F" to "minor_pentatonic"`; `song("E7 A7 E7 B7 A7 E7")` is `"E" to "minor_pentatonic"` (pins the first-chord rule).
- `` `a cadence into a secondary dominant is no cadence home` ``: `song(All of Me, first sixteen bars)` without a key
  is `"C" to "major"` with exceptions `E7, A7, D7`.
- `` `a guessed root is weighed by its home scale, not by the minor family` ``: `song(Fly Me to the Moon)` without a key
  is `"C" to "major"` with exceptions `E7, A7`.
- Keep: `a key is read from the chords, not from the first chord`, `a mode is the answer only on a root that outweighs
  the home key`, `a mode needs the part to start on its root or the key to name it`, `a vamp on one chord…`.

## CLAUDE.md

In the `SongScaleGroups` bullet of the scales `CLAUDE.md`, the sentence "Only where none fits is the root scored
(chords on it, the first and last, cadences into it), a mode only where the part starts on it": change the parenthesis
to "(chords on it, a dominant seventh only as the first chord or the most played with its target unplayed, the first
and last, cadences into it, never into another dominant)" and add ", a guessed root weighed by its home scale alone"
after "where the part starts on it".
