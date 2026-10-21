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

**Challenged:** amended — the guessed roots are sorted by the chords the home scale *holds*, leaders left out (as
written, `C F G E` ties F major with C major at four and reads `F: major`, failing `ScaleExceptionsTest`'s `a diatonic
song with one outside chord keeps its group`); a tonic seventh is the first chord where that is a dominant seventh,
else the most played, and only where its target root plays nothing but dominant sevenths (as written, `D7 G C D7`
read D Mixolydian and `E7 A7 B7 A7` an A blues); plan 01 (amended) no longer resolves Fly Me to the Moon by itself.

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
C major's 15, so A is tried first and A natural minor, holding 15 and leaving `E7` and `A7` as exceptions, wins. Plan
01 keeps this sort on held chords, the minor family as one home (with leaders in it, `C F G E` would read F major), so
after plan 01 Fly Me to the Moon still reads A; this plan fixes it.

## Fix

In `SongScaleGroups.rankedRoots`:

1. **A tonic seventh scores.** A part's candidate is its first chord where that is a dominant seventh, else its most
   played dominant seventh(s) (played at least as often as every other chord); a candidate is a *tonic seventh* where
   every chord on its target root (a fourth above it) is itself a dominant seventh, or none is played: a dominant that
   leaves to nowhere, or only to another seventh (the blues's IV7), is the tonic. `F7` in the jazz blues is the first
   chord and its `Bb7` is a seventh; `E7` in All of Me is the most played chord but `Am7` sits on A, so it stays a
   secondary dominant. Compute the set once per part (by chord) and treat its chords as non-dominant in both `scores`
   and `counts`:
   ```kotlin
   val plays = part.chords.groupingBy { it }.eachCount()
   val candidates = if (part.chords.first().isDominantSeventh) setOf(part.chords.first())
       else plays.filter { (chord, count) -> chord.isDominantSeventh && count == plays.values.max() }.keys
   val tonicSevenths = candidates.filter { seventh -> part.chords.none { it.root == (seventh.root + FOURTH).mod(12) && !it.isDominantSeventh } }.toSet()
   ```
   (`FOURTH = 5`, added by plan 01.) Both restrictions are needed: with "the first chord" taken whatever its target,
   the chorus `D7 G C D7` (a V7 pickup, G major at HEAD) scores D 4 against G 3 and reads D Mixolydian; with the
   most-played chord taken beside a first-chord seventh, the shuffle `E7 A7 B7 A7` (an E blues at HEAD) scores A 5 (two
   plays, the cadence, the last chord) against E 4 and reads as an A blues.
2. **A cadence into a dominant seventh that is no tonic seventh scores nothing.** In the cadence branch, add
   `&& (!next.isDominantSeventh || next in tonicSevenths)`. `C7 → F7` in the blues still scores for F (a tonic seventh);
   `E7 → A7` in All of Me no longer scores for A. `E7 → Am7` and `A7 → Dm7` are untouched.
3. **Ties go to the first chord's root before the count of non-dominant chords.** The `sortedWith` becomes score, then
   first chord, then count, then last chord. After 1 and 2 All of Me has C and D at 5; the chord a part starts on
   says more than one more minor chord. (Check the test `a key is read from the chords, not from the first chord`: its
   four charts are decided by score, not by the tie-break, so it stays true.)

In `guessedFitOf`, replace the sort plan 01 left (held chords, the minor family as one home) with what the home scale
itself holds, the minor family counting only for the floor in `looseFitOf`, the outside count, and the exceptions,
and leaders counting nowhere in the sort:

```kotlin
.map { it to looseCountsOn(harmony, it) }
.sortedByDescending { (root, _) -> looseHomes.maxOf { home -> harmony.chords.count { holdsChord(home, root.pitchClass, it) } } }
```

(A small private helper `scaleCountOn(harmony, root, home)` reads better than the inline lambda; `looseCountsOn`'s
family and leaders stay for the counts passed on to `looseFitOf`.) Leaders must stay out: `C F G E` without a key has
F ranked first (the `C → F` cadence), and with leaders F major accounts for four (C, F held; G leading into C, E into A)
as C major does (C, F, G held; E leading), the tie goes to F, and the settled deceptive cadence reads `F: major` with
`G` as the exception. Held alone, C major's three beat F major's two. A relative major and minor hold the same chords
(Fly Me to the Moon: 15 and 15), so the scores decide, and after rules 1–3 C ranks first.

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
- `C F G E` without a key: unchanged, `C: major` with `over E → A harmonic_minor`. `D7 G C D7` and `E7 A7 B7 A7`
  without a key: unchanged (`G: major`; `E: minor_pentatonic`).
- A side effect to expect, not a goal: a circle of dominants with nothing else, the rhythm-changes bridge
  `D7 D7 G7 G7 C7 C7 F7 F7` (`(nothing)` at HEAD, with or without `{key: Bb}`), now ranks D first (its first chord; the
  cadences into the next dominants score nothing) and gets the passing fit on D, a row `D: minor_pentatonic, blues`
  with `G7` and `C7` as dominant moments. The README already set circle-of-fifths bridges aside as low; plan 09 would
  otherwise have named it as a part no row holds.

## Tests

`SongScaleGroupsTest`:

- `` `a blues is heard on its tonic seventh, not on the chord its V leads to` ``: `song(F jazz blues)` without a key is
  `"F" to "minor_pentatonic"`; `song("E7 A7 E7 B7 A7 E7")` is `"E" to "minor_pentatonic"` (pins the first-chord rule).
- `` `a tonic seventh needs its target unplayed or played as a seventh, and the first chord's seventh comes first` ``:
  `song("D7 G C D7")` is `"G" to "major"`; `song("E7 A7 B7 A7")` is `"E" to "minor_pentatonic"`.
- `` `a cadence into a secondary dominant is no cadence home` ``: `song(All of Me, first sixteen bars)` without a key
  is `"C" to "major"` with exceptions `E7, A7, D7`.
- `` `a guessed root is weighed by its home scale, not by the minor family` ``: `song(Fly Me to the Moon)` without a key
  is `"C" to "major"` with exceptions `E7, A7`.
- Keep: `ScaleExceptionsTest`'s `a diatonic song with one outside chord keeps its group` (`C F G E`), `a key is read
  from the chords, not from the first chord`, `a mode is the answer only on a root that outweighs
  the home key`, `a mode needs the part to start on its root or the key to name it`, `a vamp on one chord…`.

## CLAUDE.md

In the `SongScaleGroups` bullet of the scales `CLAUDE.md`, the sentence "Only where none fits is the root scored
(chords on it, the first and last, cadences into it), a mode only where the part starts on it": change the parenthesis
to "(chords on it, a dominant seventh only as the first chord, else the most played, with its target unplayed or played
only as a seventh, the first
and last, cadences into it, never into another dominant)" and add ", a guessed root weighed by the chords its home scale alone holds"
after "where the part starts on it".
