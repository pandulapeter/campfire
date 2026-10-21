<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# A song with a `{key}` is always answered on the key's own scale, its chords leading into the key counted as the key's

**Challenged:** amended — leaders never count on a minor home's own root or for a rock riff (the plan as written broke
four existing tests: `E G A E` and the `{key: A}` rock verse read natural minor, `C7 F7 C7 Dm7 G7 C7` lost its group);
leaders and the lifted cap stay off on the key's root where the part has left the key (a D-major bridge under
`{key: G}` was swallowed into G with three exceptions); `guessedFitOf` keeps sorting its roots by held chords
(`C F G E` read F major); Ipanema's bridge is still dropped (its own chords are 4 of 16), so that claim is removed.

**Kind:** bug  ·  **Severity:** high  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroups.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroupsTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`

HEAD at the time of writing: `f70a58f89`. Lane G, plan 1 of 9; the later plans of the lane assume this one has landed.

## Problem

Standard repertoire with a `{key}` gets no Scales section at all, or one on another root, because the key's home is
dropped by a cap meant for guesses. `SongScaleGroups.keyFitOf` ends in

```kotlin
val homes = setOfNotNull(if (key.isMinor) NATURAL_MINOR_ID else MAJOR_ID, mode.takeIf { playsTonic })
return looseFitOf(harmony, listOf(root to looseCountsOn(harmony, root) { isRockRiff || it.id in homes }))
```

and `looseFitOf` refuses the home where it leaves out more than two distinct chords and a third of them:

```kotlin
val distinct = harmony.chords.distinct()
val holders = if (home.id == NATURAL_MINOR_ID && level == Level.LOOSE) minorFamily else listOf(home)
val outside = distinct.count { chord -> holders.none { holdsChord(it, root.pitchClass, chord) } }
if (outside > MAX_LOOSE_EXCEPTIONS && outside * 3 > distinct.size) return@firstNotNullOfOrNull null
```

That cap is right for a guessed root (a home nobody named that explains a third of the chords is no answer), but on the
key's root the author has named the home, and jazz, gospel, and country charts routinely play three or more chords
outside it (secondary dominants, a borrowed iv, a passing diminished). What the probe prints at HEAD:

- Blue Bossa, `{key: Cm}`, `Cm7 Cm7 Fm7 Fm7 Dm7b5 G7 Cm7 Cm7 Ebm7 Ab7 Dbmaj7 Dbmaj7 Dm7b5 G7 Cm7 G7` → `(nothing)`. The
  minor family holds 12 of 16, but `Ebm7`, `Ab7`, `Dbmaj7` are three distinct chords of seven.
- Gospel, `{key: C}`, `C E7 Am C7 F F#dim C/G A7 D7 G7 C G7` → `(nothing)`: C major holds 7 of 12 (THIN), five
  distinct chords of ten outside. Without a key the same chart is also `(nothing)`.
- Country, `{key: G}`, `G G C G G A7 D7 D7 G G7 C Cm G D7 G G` → `(nothing)`: 13 of 16 held, but `A7`, `G7`, `Cm` are
  three of six.
- All of Me, `{key: C}`, 32 bars (`Cmaj7 Cmaj7 E7 E7 A7 A7 Dm7 Dm7 E7 E7 Am7 Am7 D7 D7 Dm7 G7` and
  `Cmaj7 Cmaj7 E7 E7 A7 A7 Dm7 Dm7 F Fm Cmaj7 A7 Dm7 G7 C6 G7`) → `[HOME] [A] A: natural_minor …` with `E7`, `A7`, `D7`
  moments and the B section dropped: the key path fails on both parts, the A section is then guessed on A (the minor
  family absorbs `E7` and `D7`), and the B section fits nothing.
- All of Me written once per chord, `{key: C}`, `C E7 A7 Dm E7 Am D7 Dm G7 C E7 A7 Dm F Fm C A7 Dm G7 C` → `(nothing)`.
- Girl from Ipanema, `{key: F}`: the A sections read `F: major` with `G7` and `Gb7` moments, the bridge
  (`Gbmaj7 Gbmaj7 B7 B7 F#m7 F#m7 D7 D7 Gm7 Gm7 Eb7 Eb7 Am7 D7b9 Gm7 C7b9`) is silently dropped, since F major holds
  4 of its 16 chords and no guessed root clears the floor either.

A second, smaller fault in the same function: with the key given, the mode is tried beside the key's scale whenever the
tonic chord is played, and `looseFitOf` takes whichever holds one chord more (`counts.indexOf(counts.max())`), so
`{key: C}` `C Gm7 C7 F C Gm7 C7 F G7 C` reads `C: mixolydian` with `G7` as the moment (Mixolydian holds 9, major 6),
`{key: C}` `C F C F C F Ab Bb C C` reads `C: mixolydian` with `Ab` as the moment (9 against 8), and the line cliché
`{key: C}` `C Caug C6 C7 F F C C` reads `C: mixolydian` with `Caug` as the moment (7 against 6). A player given a key
of C and a chart of mostly C, F, and G wants C major; the ii–V to the IV, the ♭VI–♭VII cadence, and the passing `C7` are
moments, not a change of mode.

## Fix

All in `SongScaleGroups`.

**(a) The cap does not apply on the key's root.** In `looseFitOf`, the distinct-exception cap is skipped where the root
is the key in force's root:

```kotlin
val withLeaders = harmony.countsLeadersOn(root)
val isKeyRoot = withLeaders && harmony.key?.root?.pitchClass == root.pitchClass
if (!isKeyRoot && outside > MAX_LOOSE_EXCEPTIONS && outside * 3 > distinct.size) return@firstNotNullOfOrNull null
```

(`countsLeadersOn` is defined under (b); where it is false, (a) and (b) leave the root exactly as today.) The half floor (`if (counts[best] * 2 < total) return@firstNotNullOfOrNull null`) and the LOOSE/THIN levels stay as they
are. (`looseFitOf` is reached on the key's root from `keyFitOf` and, for a riff on it, from `powerRiffFitOf`; a guessed
root never equals the key's, since `guessedFitOf` runs only when the key's floor has failed, and then fails again.)

**(b) A chord leading into the home counts as the home's.** Add a private predicate next to `holdsChord`'s users:

```kotlin
/**
 * Whether [chord] leads into [home] on [root] as the home's own harmony would: a major triad or a dominant seventh
 * (altered or not) whose target, the root a fourth above it, is a note of the home (the E7 and A7 of a song in C), or a
 * diminished chord a semitone under one (its F#dim). Such a chord is the key's, not against it, though the home leaves
 * its notes out.
 */
private fun leadsInto(home: Scale, root: Int, chord: Chord): Boolean {
    // A major chord or a seventh on a minor home's own root is its parallel major, never a secondary dominant.
    if (chord.root == root && MINOR_THIRD in home.intervals) return false
    val notes = Scales.pitchClasses(home, root)
    val intervals = chord.intervals.map { it.mod(12) }
    val isDominant = chord.isMajorOrDominant || chord.isDominantSeventh
    val isDiminished = MINOR_THIRD in intervals && FLAT_FIFTH in intervals && FIFTH !in intervals
    return isDominant && (chord.root + FOURTH).mod(12) in notes || isDiminished && (chord.root + 1).mod(12) in notes
}
```

(`FOURTH = 5`, `FLAT_FIFTH = 6` are new constants of the object; `isMajorOrDominant` and `isDominantSeventh` are in
`ChordNotes.kt`.) The first line matters: without it the tonic `C7` of `C7 F7 C7 Dm7 G7 C7` "leads into" F for C
natural minor and Dorian, the minor family wins the loose count at 6 of 6 while natural minor itself holds none of the
chords, `minorHomeFitOn` returns null and the part loses its group (`ScaleExceptionsTest`'s `a jazz blues is Mixolydian
with the IV and V named` fails with an empty list); and the E of an `E G A E` riff, the C of Smoke's chorus, and the A
of the sus-intro riff lift natural minor over Mixolydian, so the riff rows change their third chip (`a rock riff is
played over the minor pentatonic and the blues on its root…` and `a rock verse joins a riff of other chords…` fail).
A chord on a major-third home's root still leads: `C7` in C major is the V7 of the IV.

Leaders count only where `Harmony.countsLeadersOn(root)` says so, a small function on `Harmony` used by both
`looseCountsOn` and `looseFitOf` (so neither signature changes):

```kotlin
/**
 * Whether a home on [root] counts the chords leading into it: never for a rock riff, whose major chords are the
 * riff's, not secondary dominants; and on the key's root only while the part is still in the key ([leavesKey]).
 */
fun countsLeadersOn(root: SpelledNote) = !PlayableScales.isRockRiff(root.pitchClass, chords) &&
    (key?.root?.pitchClass != root.pitchClass || !leavesKey(key))

/**
 * Whether the part has left [key] for another: its best scored root is another, and the major or natural minor scale
 * there, not sharing the key's notes, holds more of its chords than the key's own scale does. A bridge in the key of
 * the V (`D A Bm F#m G D E A` under `{key: G}`) is then read as today, in D: its A and E lead into notes of G major
 * too, and counted as the key's they would make it G with three exceptions.
 */
fun leavesKey(key: SongKey): Boolean {
    val centre = ranked.first().takeIf { it.pitchClass != key.root.pitchClass } ?: return false
    val keyScale = if (key.isMinor) naturalMinor else major
    val keyNotes = Scales.pitchClasses(keyScale, key.root.pitchClass)
    val held = chords.count { holdsChord(keyScale, key.root.pitchClass, it) }
    return listOf(major, naturalMinor).any { scale ->
        Scales.pitchClasses(scale, centre.pitchClass) != keyNotes && chords.count { holdsChord(scale, centre.pitchClass, it) } > held
    }
}
```

(`major` is a new `private val major = Scales.fromId(MAJOR_ID)!!` of the object; plan 04 uses the same one.) Its relative
key is never "another" (All of Me's A section ranks A first before plan 03, but A natural minor has C major's notes).
The F jazz blues under `{key: F}` does leave the key by this rule before plan 03 (G ranks first and G natural minor
holds `F7` ×5, `Cm7`, `Gm7` ×2, more than F major's five), so it keeps HEAD's G reading until plans 02 and 03 land.

A minor chord right before a leader and a fourth below it is the ii of a secondary ii–V (`Gm7 C7`
into F in a song in C) and counts with the leader; this is what keeps the ii–V to the IV in the key under (c) below.
Then `looseCountsOn` counts what a home *accounts for*, held, leading, or the ii of a leader, by index since the ii
needs the chord after it:

```kotlin
val withLeaders = harmony.countsLeadersOn(root)
…
if (!isAllowed(home)) -1 else harmony.chords.indices.count { index ->
    val chord = harmony.chords[index]
    holders.any { holdsChord(it, root.pitchClass, chord) } ||
        withLeaders && (leadsInto(home, root.pitchClass, chord) || harmony.isTwoOfLeaderInto(home, root.pitchClass, index))
}
```

with, on `Harmony`, `isTwoOfLeaderInto(home, root, index)`: the chord has a minor third and no major third, the next
chord in the list (not cyclic; a ii is never the last chord before its V) is a leader into the home, and its root is a
fourth above the chord's.

and in `looseFitOf` the half floor is checked on the chords the home (the family for natural minor, as today) itself
holds, recomputed for the chosen home, while `counts[best]` (now held plus leading) decides LOOSE against THIN, and the
cap's `outside` leaves leaders out:

```kotlin
val holdersOfBest = if (home.id == NATURAL_MINOR_ID) minorFamily else listOf(home)
val own = harmony.chords.count { chord -> holdersOfBest.any { holdsChord(it, root.pitchClass, chord) } }
if (own * 2 < total) return@firstNotNullOfOrNull null
val level = if (counts[best] * 3 < total * 2) Level.THIN else Level.LOOSE
…
val outside = distinct.count { chord -> holders.none { holdsChord(it, root.pitchClass, chord) } && !(withLeaders && leadsInto(home, root.pitchClass, chord)) }
```

**The roots a guess tries keep today's order.** `guessedFitOf` sorts its two candidate roots by `counts.max()` of
`looseCountsOn`, which now counts leaders; sort them by what a home *holds* instead, the minor family as one home, as
today, and pass the counts with leaders on to `looseFitOf`:

```kotlin
.map { it to looseCountsOn(harmony, it) }
.sortedByDescending { (root, _) ->
    looseHomes.maxOf { home ->
        val holders = if (home.id == NATURAL_MINOR_ID) minorFamily else listOf(home)
        harmony.chords.count { chord -> holders.any { holdsChord(it, root.pitchClass, chord) } }
    }
}
```

With leaders in the sort, `C F G E` without a key ties F major (C, F held; G leading into C, E into A) with C major
(C, F, G held; E leading) at four, the tie goes to F, the scores' favourite, and the chart reads `F: major` with `G` as
the exception (`ScaleExceptionsTest`'s `a diatonic song with one outside chord keeps its group` fails); the settled
deceptive `C F G E → A harmonic minor` must stay. (Plan 03 changes this sort again, to the home scale alone.)

The floor must stay the home's own chords: with leaders on the floor, `{key: C}` `E A B E` would be C major at 4 of 4
(E leads to A, A to D, B to E), and the existing test
`the key names the centre wherever its own scale holds half the section, the chords deciding otherwise` expects
`E major`. A key must hold half the chords by itself; what leads into it then stops counting against it. (An
alternative that would also allow leaders on the floor, a leader counting only where its target chord is played and
held, was considered and rejected as more rule for no chart in the corpus.)

The exceptions are untouched: `harmony.exceptionsTo(home, root)` still names every chord outside the home's notes, so
`E7` still reads "A harmonic minor" and `F#dim` still gets its answer from `ScaleExceptions` (lane S's file, unchanged).
`Group.isGuess` is untouched too.

**(c) The key's scale beats its mode unless the mode holds clearly more.** In `keyFitOf`, after the counts are built,
the mode's count is set to -1 (ruled out, as `isAllowed` does) unless it accounts for at least `MIN_MODE_LEAD = 2`
chords more than the key's scale, or the tonic is played as a dominant seventh more often than plain (then its flat
seventh is the song's, `C7 Bb F G7`):

```kotlin
val counts = looseCountsOn(harmony, root) { isRockRiff || it.id in homes }
val keyScale = looseHomes.indexOfFirst { it.id == (if (key.isMinor) NATURAL_MINOR_ID else MAJOR_ID) }
val modeIndex = looseHomes.indexOfFirst { it.id == mode }
val onTonic = harmony.chords.filter { it.root == root.pitchClass }
val tonicIsSeventh = onTonic.count { it.isDominantSeventh } > onTonic.count { !it.isDominantSeventh }
val preferred = if (isRockRiff || tonicIsSeventh || counts[modeIndex] >= counts[keyScale] + MIN_MODE_LEAD) counts
    else counts.mapIndexed { index, count -> if (index == modeIndex) -1 else count }
return looseFitOf(harmony, listOf(root to preferred))
```

Both counts are what the home accounts for, so a leader counts for the mode too (`G7` leads into C Mixolydian's C as
it leads into C major's): `C Gm7 C7 F C Gm7 C7 F G7 C` gives major 10 (`C` ×3, `F` ×2, `G7`, the leader `C7` ×2, its
ii `Gm7` ×2) against Mixolydian 10 (nine held, `G7` leading), a tie, and the key wins; `C F C F C F Ab Bb C C` gives
8 against 9; `C Caug C6 C7 F F C C` gives 7 against 7. The "Mixolydian with its V" chart keeps its mode at 11 against
14, and `SongScaleSuggestionsTest`'s `a Mixolydian verse ending on the major V…` verse at 16 against 24.

A rock riff keeps today's rule (any home; `looseFitOf` picks Mixolydian or natural minor for it; it counts no leaders,
see `countsLeadersOn`). The TONAL readings
earlier in `keyFitOf` (`harmony.holding(root, majorKeyScaleIds + MIXOLYDIAN_ID)`) are not touched: a part the key's
Mixolydian holds whole is still read as it (plan 04 narrows that separately). "Mixolydian with its V",
`{key: A}` `A G D A A G D A A G D A E A`, keeps its mode: Mixolydian accounts for 13, major for 11.

Update the KDoc of `looseFitOf`, `looseCountsOn`, and `keyFitOf` to say so, in the files' voice.

### Expected probe output after the fix

- Blue Bossa → `[HOME] [A] C: natural_minor, minor_pentatonic, blues, …` with the moments `over G7 → C harmonic_minor,
  melodic_minor`, `over Dbmaj7 → C phrygian`, `over Ebm7 → Eb dorian, …` (the fourth exception, `Ab7 → Ab mixolydian`,
  loses the third slot to `Ebm7`, played as often but earlier).
- Gospel with the key → `[HOME] [Verse] C: major, major_pentatonic, major_hexatonic, bebop_dominant (+1)` with
  `over E7 → A harmonic_minor`, `over C7 → C mixolydian, …`, and `F#dim` as the third moment, answered as
  `ScaleExceptions` answers it today (an altered/Locrian list on F#; lane S may improve it). Gospel without the key:
  the same (the guessed root is C, and (b) counts its leaders).
- Country → `[HOME] [Verse] G: major, …` with `over A7 → A mixolydian, …`, `over G7 → G mixolydian, …`,
  `over Cm → C melodic_minor, dorian, …` (as Creep's `Cm` reads today).
- All of Me, 32 bars → one group, `[HOME] [A, B] C: major, …` with `over E7 → A harmonic_minor`, `over A7 →
  D harmonic_minor`, `over D7 → D mixolydian, …`; `Fm` is the fourth exception and gets no slot.
- All of Me written once → `[HOME] [A] C: major, …`, the same three moments.
- Girl from Ipanema → unchanged on the page: `[HOME] [A, A2] F: major, …` with the `G7` and `Gb7` moments, the bridge
  still dropped. F major *holds* only `Gm7` ×3 and `Am7` of the bridge's sixteen (the leaders `D7`, `D7b9`, `C7b9`, `B7`
  count for the level, never for the half floor), so the floor refuses it, and no guessed root clears it either. The
  bridge is a chain of ii–Vs in four keys; plan 09 (lane U) names it on the page as a part no row holds. (The A
  sections rise from THIN to LOOSE, `G7` now leading into C.)
- `{key: C}` `C Gm7 C7 F C Gm7 C7 F G7 C` → `[HOME] [Verse] C: major, …` with `over Gm7 → G dorian, …` and
  `over C7 → C mixolydian, …`.
- `{key: C}` `C F C F C F Ab Bb C C` → `C: major, …` with `over Ab → C natural_minor` and `over Bb → C mixolydian, …`.
- `{key: C}` `C Caug C6 C7 F F C C` → `C: major, …` with `over Caug → C ionian_sharp_5, harmonic_major, …` and
  `over C7 → C mixolydian, …`.
- `{key: C}` `C7 Bb F G7 C7 Bb F G7` → `C: mixolydian, …` with `over G7 → G mixolydian, …` (the tonic is a seventh).
- `{key: Bb}` rhythm changes A section `Bb G7 Cm7 F7 Bb G7 Cm7 F7 Bb Bb7 Eb Edim Bb F7 Bb F7` (`(nothing)` at HEAD) →
  `[HOME] [A] Bb: major, …` with `over G7 → C harmonic_minor`, `over Bb7 → Bb mixolydian, …`, `over Edim → …`.
- Unchanged (the guards): `{key: G}` verse `G C D G Em C D G`, bridge `D A Bm F#m G D E A` → the verse `G: major` and
  the bridge `D: major` with `over E`; Hey Joe, Smoke on the Water, the sus-intro riff, and every rock-riff row keep
  their chips; `C F G E` without a key stays `C: major` with `over E → A harmonic_minor`; Fly Me to the Moon and All of
  Me's first sixteen bars without a key stay `A: natural_minor` until plan 03.

Re-run the whole corpus (`corpus-all.txt`) after the change and read the diff: the only intended changes are songs that
were `(nothing)`, songs whose key root changed, and the three Mixolydian-by-one charts above.

## Tests

In `SongScaleGroupsTest`, with its `song(…)` helper:

- `` `a key's home is kept however many chords it leaves out` ``: Blue Bossa as above with `key = "Cm"` is one group,
  `"C" to "natural_minor"`, whose exception names are `G7`, `Dbmaj7`, `Ebm7`, `Ab7` in that order (most played first,
  then play order); All of Me's two sections with `key = "C"` are one group `"C" to "major"` with exceptions
  `E7, A7, D7, Fm`; the written-once chart with `key = "C"` is `"C" to "major"`.
- `` `a secondary dominant and a leading-tone diminished chord are the key's, not against it` ``: the gospel chart with
  and without `key = "C"` is a single group `"C" to "major"`, its exceptions `E7, C7, F#dim, A7, D7`; the country chart
  with `key = "G"` is `"G" to "major"` with exceptions `A7, G7, Cm`.
- `` `a key must hold half the chords by itself` ``: `song("E A B E", key = "C")` stays `"E" to "major"` (its chords
  all lead into C major notes but none is held), and `song("D F G", key = "A")` is still one group (unchanged behaviour,
  pinned).
- `` `the key's own scale beats its mode unless the mode holds two chords more` ``: `song("C Gm7 C7 F C Gm7 C7 F G7 C",
  key = "C")`, `song("C F C F C F Ab Bb C C", key = "C")`, and `song("C Caug C6 C7 F F C C", key = "C")` are each
  `"C" to "major"`; `song("A G D A A G D A A G D A E A", key = "A")` is `"A" to "mixolydian"`; and
  `song("C7 Bb F G7 C7 Bb F G7", key = "C")` is `"C" to "mixolydian"` (the tonic is mostly a seventh).
- `` `a part in the key of the fifth is no part of the key, its chords leading into it or not` ``:
  `song("G C D G Em C D G", "D A Bm F#m G D E A", key = "G")` is `listOf("G" to "major", "D" to "major")`.

Existing tests to keep green, all in `SongScaleGroupsTest`: `the key names the centre wherever its own scale holds half
the section, the chords deciding otherwise`, `a guess on three chords is left out beside a better answer, and kept
alone`, `a verse with a secondary dominant joins its key's chorus`, `a chorus leaving the verse's scale gets the scales
that fit it`, `a rock riff is played over the minor pentatonic and the blues on its root, with no exceptions`, `a rock
verse joins a riff of other chords on its root, and a chorus in another key stands apart`; in `ScaleExceptionsTest`,
`a jazz blues is Mixolydian with the IV and V named` (still Mixolydian until plan 02) and `a diatonic song with one
outside chord keeps its group`. All of these were run against the amended fix and pass. In `ScaleExceptionsTest` (lane S's file, not to be edited by this lane) `a home leaving out more than two
chords and a third of them has no group` tests a chart without a key, so (a) leaves it true; check it before landing
and, if it names a key, tell the lane S executor rather than editing the test.

## CLAUDE.md

In `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`:

- The bullet starting "Where no scale holds a part, a **home** (major, the minor family as one, Dorian, Mixolydian)
  holding half its chords answers; it may leave out two distinct chords or a third of them, each a `ScaleException`":
  after "a third of them" add ", except on the key's root, where the author's word stands however many it leaves out; a
  chord leading into the home (a secondary dominant, a diminished chord a semitone under a home note, the ii before
  such a dominant; never a chord on a minor home's own root) is the home's for that cap and for the two-thirds level,
  never for the half floor, and is still named as an exception; a rock riff counts no leaders, and neither does the
  key's root for a part that has left the key (its best scored root's major or minor scale holds more of it than the
  key's does: a bridge in the key of the V stays in that key)".
- In the `SongScaleGroups` bullet, the sentence "then its own major or minor family as a home with exceptions, its mode
  too where its tonic chord is played (capo shapes in C under `{key: D}` play no D, so they never read as D Mixolydian)":
  add "and holds two chords more than the key's scale, or the tonic is mostly a seventh" after "where its tonic chord is
  played".
