<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# A jazz blues and a twelve-bar of triads with a V–IV turnaround are a blues, and a lone passing I7 is not

**Challenged:** amended — the jazz shape needs the I to be the most played root and no major seventh on I, IV, or V
(as written, Here Comes the Sun, the gospel chart without a key, a `E7 A7 E7 E7 A7 A7…` twelve-bar, and `C7 Bb F G7`
read as blues on the wrong root, and `C7 Fmaj7 G7` broke an existing test); the turnaround needs a dominant seventh in
the part (Bad Moon Rising's `D A G D` read as a blues); a blues the major scale holds (the triad twelve-bar) now leads
with the blues scales, which the plan as written never reached (E major holds `E A B7`, so `keyFitOf` and `tonalFitOf`
returned E major first); the expected exception order of the F jazz blues is `Bb7, D7, C7, Bdim7, Am7`.

**Kind:** design  ·  **Severity:** high  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/PlayableScales.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroups.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/PlayableScalesTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroupsTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`; one assertion in lane S's
`ScaleExceptionsTest.kt` (see Tests)

HEAD at the time of writing: `f70a58f89`. Lane G, plan 2 of 9; assumes plan 01 has landed (the key's loose home with
leaders), and is assumed by plan 03.

## Problem

`PlayableScales.isDominantBlues` is the only notion of a blues:

```kotlin
internal fun isDominantBlues(root: Int, chords: List<Chord>): Boolean {
    if (chords.isEmpty()) return false
    val isMajorOnBluesStep = chords.all { chord ->
        (chord.root - root).mod(12) in bluesSteps && chord.intervals.map { it.mod(12) }.let { MAJOR_THIRD in it && MAJOR_SEVENTH !in it }
    }
    val isAltered = chords.any { chord -> (chord.root - root).mod(12) == 0 && chord.intervals.any { it.mod(12) in alterations } }
    return isMajorOnBluesStep && !isAltered && chords.any { (it.root - root).mod(12) in seventhSteps && MINOR_SEVENTH in it.intervals }
}
```

Every chord must be major on I, IV, or V, and one seventh on the I or the IV is enough. Three kinds of chart fall on
the wrong side of it.

**(a) A jazz blues is no blues.** One ii–V or passing diminished chord breaks `all`, and the chart falls to the key's
loose homes, where the minor family on another root absorbs its dominants. At HEAD:

- `{key: F}` `F7 Bb7 F7 Cm7 F7 Bb7 Bdim7 F7 Am7 D7 Gm7 C7 F7 D7 Gm7 C7` → `[HOME] [Chorus] G: natural_minor, …` with
  `over Bb7 → G phrygian`, `over D7 → G harmonic_minor`, `over C7 & Am7 → G dorian`. (F major holds 5 of 16 and fails
  the floor; the root is then guessed, and G scores highest: `Gm7` twice and `D7 → Gm7` twice, while `F7`, a dominant
  seventh, scores nothing for F; plan 03 fixes that scoring. With plan 01 landed the chart still reads G: G ranks
  first and G natural minor holds more of it than F major, so plan 01's `leavesKey` keeps today's reading; this plan's
  blues check in `keyFitOf`, before the loose home, is what puts it on F.)
- `{key: Bb}` `Bb7 Eb7 Bb7 Bb7 Eb7 Edim7 Bb7 G7 Cm7 F7 Bb7 F7` → `(nothing)` at HEAD (Bb major holds `Cm7` and `F7`
  only); after plan 01, Bb Mixolydian LOOSE for the same reason.

**(b) A twelve-bar written with triads and only a V7** is no blues either, since no seventh sits on the I or the IV:
`{key: E}` `E E E E A A E E B7 A E B7` → `[HOME] [Verse] E: major, major_pentatonic, major_hexatonic, …`. Every
guitarist reads that chart as a blues in E and reaches for E minor pentatonic; the V to IV move in bars 9–10 (`B7 A`)
is the turnaround that gives it away.

**(c) A lone passing I7 makes a blues of a folk or gospel verse.** `{key: G}` `G G G7 C C G G D7 G G` →
`[HOME] [Verse] G: minor_pentatonic, blues, major_pentatonic, mixolydian (+2)` with `over D7 → D mixolydian`;
`{key: C}` `C F G C7 F G C C` → `C: minor_pentatonic, blues, major_pentatonic, mixolydian (+2)`. The key's own major
scale is nowhere in the row, and the fourth chip is Mixolydian. A `G7` walking to the C is a colour of a song in G
major, not a change of style. Yet `{key: A}` `A A A A7 D D A A E D A E`, a real twelve-bar with its one seventh
written in bar four, is the same shape by chord counts (I mostly plain, no IV7) and must stay a blues.

## Fix

### The blues, in `PlayableScales.isDominantBlues`

Rewrite the predicate so that a blues is one of two shapes, both without an altered I (the existing `isAltered` rule),
and both carrying a *blues mark*:

- **The plain shape** (today's): every chord is major or dominant, without a major seventh, on I, IV, or V.
- **The jazz shape**: every chord on the I is a dominant seventh (at least one), the I is played more often than any
  other root, no chord on I, IV, or V has a major seventh, and the major or dominant chords on I, IV, and V are more
  than half of the plays; the rest (a ii–V, a passing diminished, a VI7) are departures the home names as exceptions.

  The two extra conditions are needed. `tonalFitOf` tries `suggestedFitOn` on *every* ranked root of a chart no scale
  holds, so a loose shape finds a blues on a lesser root: without "the I most played", the gospel chart `C E7 Am C7 F
  F#dim C/G A7 D7 G7 C G7` without a key reads `G: minor_pentatonic` (G7 ×2 on G, C ×3 on its IV, D7 on its V: 7 of 12);
  Here Comes the Sun under `{key: A}` reads the whole song as an A blues (`A7` its I, the D shapes its IV); the
  twelve-bar `E7 A7 E7 E7 A7 A7 E7 E7 B7 A7 E7 B7` without a key reads on A (`ScaleExceptionsTest`'s `a dominant blues
  has no exceptions` fails); the ragtime `C A7 D7 G7` reads `G: minor_pentatonic`; and `{key: C}` `C7 Bb F G7`, which
  plan 01 pins as C Mixolydian, becomes a blues. Without "no major seventh", `C7 Fmaj7 G7` is a blues and the kept test
  `a blues with a seventh on the IV is a dominant blues, a plain V7 cadence is not` fails (the IV's major seventh is the
  blues scale's flat third's clash, as the tonic's is).

The blues mark is any of:

- the I is played as a dominant seventh at least as often as plain (the jazz shape always has it);
- the IV is played as a dominant seventh at least as often as plain;
- **the turnaround**: a chord on the V followed by a chord on the IV, in a part at least half of whose chords sit on
  the I (`chords.count { (it.root - root).mod(12) == 0 } * 2 >= chords.size`) and which plays a dominant seventh
  somewhere (the `B7` of decision (b)'s chart). The I share is what tells a twelve-bar (seven of twelve bars on the I)
  from the I–IV–V–IV loops of rock and roll (`A D E D`, `E A B A`, `G C D C`), which are the key's major, not a blues,
  and which the move alone would catch. The seventh is what tells it from a song of plain triads on I, V, IV:
  Bad Moon Rising, `{key: D}` `D A G D D A G D D A G D`, sits half on D and moves A→G, and without it would read
  `D: minor_pentatonic, blues, major_pentatonic, mixolydian` with D major gone from the row (so would Sweet Home
  Alabama's `D C G G` without a key, on G). A twelve-bar of triads with no seventh at all stays the major key, as today.

"At least as often as plain" is what removes (c): `G G G7 C C G G D7 G G` plays the I plain four times and as a seventh
once, has no IV7 and no V–IV move, so it is no blues; it falls to plan 01's key home, `G: major` with `over G7 → G
mixolydian`, which is what the chart is. `A A A A7 D D A A E D A E` keeps the blues by the turnaround (`E D`, with seven
chords of twelve on the A). The single-chord calls (`isDominantBlues(0, listOf(C9))`, `C7#9`) keep working: a lone
dominant seventh is its own I, always a seventh.

Sketch:

```kotlin
internal fun isDominantBlues(root: Int, chords: List<Chord>): Boolean {
    if (chords.isEmpty()) return false
    fun step(chord: Chord) = (chord.root - root).mod(12)
    fun isMajorOrSeventh(chord: Chord) = chord.intervals.map { it.mod(12) }.let { MAJOR_THIRD in it && MAJOR_SEVENTH !in it }
    val onBluesSteps = chords.filter { step(it) in bluesSteps && isMajorOrSeventh(it) }
    val isPlain = onBluesSteps.size == chords.size
    val onTonic = chords.filter { step(it) == 0 }
    val isTonicMostPlayed = chords.groupingBy { step(it) }.eachCount().all { (other, plays) -> other == 0 || plays < onTonic.size }
    val hasMajorSeventh = chords.any { step(it) in bluesSteps && MAJOR_SEVENTH in it.intervals.map { interval -> interval.mod(12) } }
    val isJazz = onTonic.isNotEmpty() && onTonic.all { it.isDominantSeventh } && isTonicMostPlayed && !hasMajorSeventh &&
        onBluesSteps.size * 2 > chords.size
    if (!isPlain && !isJazz) return false
    if (onTonic.any { chord -> chord.intervals.any { it.mod(12) in alterations } }) return false
    fun isMostlySeventh(step: Int) = chords.filter { step(it) == step }.let { on -> on.isNotEmpty() && on.count { MINOR_SEVENTH in it.intervals } * 2 >= on.size }
    val hasTurnaround = onTonic.size * 2 >= chords.size && chords.any { it.isDominantSeventh } &&
        chords.indices.any { step(chords[it]) == FIFTH && step(chords[(it + 1) % chords.size]) == FOURTH }
    return isMostlySeventh(0) || isMostlySeventh(FOURTH) || hasTurnaround
}
```

### A blues the major scale holds

A twelve-bar of triads with a V7 (`E E E E A A E E B7 A E B7`) is held whole by E major, which the old blues never was
(its I7 or IV7 always had a note the major scale lacks). So the predicate alone changes nothing for it: `keyFitOf`'s
first line (`harmony.holding(root, majorKeyScaleIds + …)`) and, without a key, `tonalFitOf`'s `homeIndex` both return
`tonalFitOn(harmony, root, major)`, and `suggestionFor` puts that home first (`E: major, major_pentatonic,
minor_pentatonic, blues`). Three small changes make it lead with the blues scales:

- `PlayableScales.suggestionFor`: where `isDominantBlues` and the home is the major scale, the dominant blues scales
  come first, then the home and the rest as today:
  ```kotlin
  // A blues the major scale holds (triads and a V7 with the turnaround) is still played as a blues.
  val lead = if (isDominantBlues && home?.id == MAJOR_ID) dominantBluesScales else emptyList()
  val scales = (lead + listOfNotNull(home) + bluesSound + …).distinct()
  ```
  (`MAJOR_ID = "major"`, a new constant.) No blues the old predicate accepted is held by the major scale, and a lone
  dominant seventh's home is Mixolydian, so nothing else moves.
- `SongScaleGroups.tonalFitOn`: where the home is major and the chords are a dominant blues on the root, answer with
  `suggestedFitOn(harmony, root)` instead (one line at the top: `if (home.id == MAJOR_ID && PlayableScales.isDominantBlues(root.pitchClass, harmony.chords)) return suggestedFitOn(harmony, root)`; make it a block body).
- `SongScaleGroups.suggestedFitOn`: for a dominant blues the fit's home is always `suggestion.scales.first()` (the
  minor pentatonic), never the first holding scale, so `Fit.isRockRiff` (`home != scales.first()`) stays false for a
  blues E major holds; see the next section for its exceptions.

(`FOURTH = 5`, `FIFTH = 7` as new constants; `isDominantSeventh` from `ChordNotes.kt`; the turnaround is read as a
loop like `rankedRoots` reads cadences, since a section repeats.) The KDoc of `isDominantBlues` and the class KDoc's
second bullet ("The minor pentatonic and the blues over a major sound…") are rewritten to say the two shapes and the
mark. `isDominantBlues` already decides `suggestionFor`'s `bluesSound` and `isFit`, so a jazz blues on its root (which
no scale holds) is now a fit, led by `dominantBluesScales`; a blues the major scale holds needs the section below.

### The fit, in `SongScaleGroups.suggestedFitOn`

Today the blues fit is built without exceptions:

```kotlin
private fun suggestedFitOn(harmony: Harmony, root: SpelledNote) = PlayableScales.suggestionFor(root.pitchClass, harmony.chords)?.takeIf { it.isFit }?.let { suggestion ->
    // A dominant blues is a fit no scale holds; its home is then the first it suggests.
    val home = suggestion.scales.firstOrNull { it.intervals.size <= HOME_SCALE_SIZE && harmony.holds(it, root) } ?: suggestion.scales.first()
    Fit(root, suggestion.scales, Level.TONAL, home)
}
```

For a blues of the jazz shape the chords off I, IV, and V must be named, so where the fit is a blues
(`PlayableScales.isDominantBlues(root.pitchClass, harmony.chords)`; its home is then `suggestion.scales.first()`, see
above) and not of the plain shape, pass `harmony.exceptionsTo(mixolydian, root)`
as the exceptions: everything Mixolydian on the root leaves out, the IV7 and V7 included. Naming the IV7 and the V7
here too, rather than leaving them to `SongScaleSuggestions`' dominant moments (weight 1), keeps them ahead of a passing
`Bdim7` (an exception weighs 2) in the three slots. A plain blues keeps no exceptions, as `ScaleExceptionsTest`'s
`a dominant blues has no exceptions` pins: its IV7 and V7 are the shape itself, and the suggestions already offer them
as dominant moments. The `Fit.home` stays `suggestion.scales.first()` (the minor pentatonic) so `Fit.isRockRiff`
(`home != scales.first()`) stays false; the exceptions are computed against Mixolydian explicitly. Expose whether a
blues is of the plain shape from `PlayableScales` (a small `internal fun isPlainBlues(root, chords)`, or have
`isDominantBlues` return the shape) rather than re-deriving it in `SongScaleGroups`.

The key path (`keyFitOf`) already calls `suggestedFitOn` right after the minor family check, before the loose home, so
with the key given the jazz blues is read on the key's root. Without a key, `tonalFitOf` tries
`ranked.firstNotNullOfOrNull { root -> suggestedFitOn(harmony, root) }` last, after the keys and modes; the F jazz
blues has no scale holding it, so this is reached, G (ranked first) is no blues (`Gm7` on its I), and F is. Plan 03
makes F rank first anyway.

### Expected probe output after the fix

- F jazz blues with the key → `[HOME] [Chorus] F: minor_pentatonic, blues, major_pentatonic, mixolydian (+1)` with
  `over Bb7 → Bb mixolydian, …`, `over D7 → G harmonic_minor`, `over C7 → C mixolydian, …` (each played twice; `Am7` and
  `Bdim7`, once each, get no slot). Without the key: the same (G ranks first but is no blues, `Gm7` sitting on its I;
  F is the next ranked root `tonalFitOf` tries).
- Bb jazz blues → `[HOME] [Chorus] Bb: minor_pentatonic, blues, major_pentatonic, mixolydian (+1)` with
  `over Eb7 → Eb mixolydian, …`, `over F7 → F mixolydian, …`, `over Edim7 → …` (as `ScaleExceptions` answers a
  diminished seventh today).
- `{key: E}` `E E E E A A E E B7 A E B7` → `[HOME] [Verse] E: minor_pentatonic, blues, major_pentatonic, mixolydian (+5)`
  with `over B7 → B mixolydian, …` (a dominant moment, as the plain twelve-bar's are today; the `+5` is E major and
  what it holds, after the blues scales).
- `{key: G}` `G G C G D7 C G G` → the same shape, `G: minor_pentatonic, blues, …` with `over D7`.
- `{key: G}` `G G G7 C C G G D7 G G` → `[HOME] [Verse] G: major, major_pentatonic, major_hexatonic, bebop_dominant (+1)`
  with `over G7 → G mixolydian, …`.
- `{key: C}` `C F G C7 F G C C` → `C: major, …` with `over C7 → C mixolydian, …`.
- `{key: A}` `A A A A7 D D A A E D A E` → `A: minor_pentatonic, blues, major_pentatonic, mixolydian (+1)` with no
  moment (a plain blues has no exceptions, and the `E` is a triad, which the dominant-moment rule of
  `SongScaleSuggestions` leaves alone).
- `{key: C}` `C7 F G C7 F G C7 C7` → unchanged, `C: minor_pentatonic, …`.
- `E A E E7 A A E E B7 A E B7` (`{key: E}`, the corpus's "quick change and turnaround") → unchanged, a blues by the
  turnaround.
- `A D E D` without a key → `A: major, …` (unchanged: never a blues).
- Unchanged by the guards: Bad Moon Rising `{key: D}` `D A G D` ×3 and Sweet Home Alabama `D C G G` without a key
  (`G: major`) — triads, no seventh; the gospel chart without a key (`C: major` with plan 01's moments); Here Comes the
  Sun `{key: A}` (`A7` on its I, but the D shapes outnumber it); `E7 A7 E7 E7 A7 A7 E7 E7 B7 A7 E7 B7` without a key
  (`E: minor_pentatonic`, never on A); `C A7 D7 G7` ×2 without a key (`C: major` with the `A7`, `D7` moments);
  `{key: C}` `C7 Bb F G7` ×2 (`C: mixolydian`, plan 01's test).

### Decisions for the user

1. **(b) The V–IV turnaround reads as a blues** — recommended yes, with the I-share guard above. Without the guard the
   I–IV–V–IV loops would lead with the minor pentatonic; with it only charts that sit on their I half the time do, which
   is the twelve-bar's shape.
2. **(c) A lone passing I7 is no blues** — recommended as above (the key with the I7 as a moment). The alternative the
   second reviewer proposed, keeping the chart a blues but leading the row with the key (`major, major_pentatonic,
   major_blues, minor_pentatonic, blues, mixolydian`), shows the blues scales a player may well want over such a verse,
   but it makes a six-chip row of a four-chord gospel verse and a TONAL "blues" fit whose home is the major scale that
   does not hold its I7. Say which; the plan is written for the first.

## Tests

`PlayableScalesTest`, with its `isBlues(root, vararg names)` helper:

- `` `a jazz blues is a blues on its tonic seventh, its other chords departures` ``: `isBlues(5, "F7", "Bb7", "F7",
  "Cm7", "F7", "Bb7", "Bdim7", "F7", "Am7", "D7", "Gm7", "C7", "F7", "D7", "Gm7", "C7")` is true;
  `isBlues(10, "Bb7", "Eb7", "Bb7", "Bb7", "Eb7", "Edim7", "Bb7", "G7", "Cm7", "F7", "Bb7", "F7")` is true;
  `isBlues(0, "C7", "Dm")` stays false (nothing on the IV or V; keep the existing assertion in `a dominant blues
  offers the blues scales…`), and `isBlues(0, "C7", "Dm", "G7", "Am")` is false (not more than half on I, IV, V).
- `` `a jazz shape needs its I the most played root and no major seventh` ``: `isBlues(7, "C", "E7", "Am", "C7", "F",
  "F#dim", "C", "A7", "D7", "G7", "C", "G7")` false (the gospel chart on G); `isBlues(0, "C7", "Bb", "F", "G7", "C7",
  "Bb", "F", "G7")` false; `isBlues(0, "C7", "Fmaj7", "C7", "Fmaj7", "C7", "G7")` false.
- `` `a twelve-bar of triads with a V to IV turnaround is a blues, a I IV V IV loop is not` ``:
  `isBlues(4, "E", "E", "E", "E", "A", "A", "E", "E", "B7", "A", "E", "B7")` true;
  `isBlues(9, "A", "A", "A", "A7", "D", "D", "A", "A", "E", "D", "A", "E")` true; `isBlues(9, "A", "D", "E", "D")`
  false; `isBlues(4, "E", "A", "B", "A")` false; `isBlues(2, "D", "A", "G", "D", "D", "A", "G", "D", "D", "A", "G", "D")`
  false (no seventh: Bad Moon Rising).
- `` `a tonic played plain more often than as a seventh is no blues` ``: `isBlues(7, "G", "G", "G7", "C", "C", "G",
  "G", "D7", "G", "G")` false; `isBlues(0, "C", "F", "G", "C7", "F", "G", "C", "C")` false; `isBlues(0, "C7", "F",
  "G", "C7", "F", "G", "C7", "C7")` true.
- Keep: `a blues with a seventh on the IV is a dominant blues, a plain V7 cadence is not` (`C F7 G` true, `C7 F G`
  true, `C6 F7 G` true, `Cmaj7 F7 G7` false, `C7 Fmaj7 G7` false, `C F G7` false), `an altered dominant is not a
  blues`, `an altered turnaround keeps a song's blues`, `a twelve-bar blues is one group of the blues scales`.

`SongScaleGroupsTest`:

- `` `a jazz blues is one group on its tonic, led by the blues scales, its departures the exceptions` ``: the F chart
  with `key = "F"` and without a key is a single group `"F" to "minor_pentatonic"` whose exception names are
  `Bb7, D7, C7, Bdim7, Am7` (most played first, then play order: `Bdim7` is bar 7, `Am7` bar 9); the
  `E E E E A A E E B7 A E B7` chart with `key = "E"` is `"E" to "minor_pentatonic"` with no exceptions, and so is
  `song("E E E E A A E E B7 A E B7")` without a key; `song("D A G D D A G D", key = "D")` is `"D" to "major"`.
- `` `a passing tonic seventh is a moment of the key, not a blues` ``: `song("G G G7 C C G G D7 G G", key = "G")` is
  `"G" to "major"` with exceptions `[G7]`; `song("C F G C7 F G C C", key = "C")` is `"C" to "major"` with `[C7]`;
  `song("A A A A7 D D A A E D A E", key = "A")` is `"A" to "minor_pentatonic"`.

Lane S's `ScaleExceptionsTest.a jazz blues is Mixolydian with the IV and V named` asserts `group("C7 F7 C7 Dm7 G7 C7").home`
is `"C" to "mixolydian"`; with the jazz shape that chart is a blues led by the minor pentatonic, and the assertion
must become `"C" to "minor_pentatonic"` (its second assertion, `F7` on F and `G7` on G as the exceptions, stays true
through the Mixolydian exceptions). As the README says, lane G makes that one-line change in this plan's commit (the
test fails the moment this plan lands) and lane S's cherry-pick keeps it. `SongScaleSuggestionsTest.a dominant blues changes scale
over the IV and the V…` (plain shape, key E) stays true.

## CLAUDE.md

In the scales `CLAUDE.md`:

- `PlayableScales` bullet, the parenthesis "over a dominant blues with the I or IV a seventh": replace with "over a
  dominant blues (every chord major on I, IV, V, or every chord on the I a seventh, the I the most played root, no
  major seventh on I, IV, V, and those steps more than half the plays; the I or the IV mostly a seventh, or a V–IV
  turnaround in a part half on its I with a seventh in it), a jazz blues's departures named against its Mixolydian,
  and ahead of the major scale where that holds the blues (triads and a V7)".
- `SongScaleGroups` bullet, "a dominant blues, then its own major or minor family": unchanged wording; add after the
  sentence on the one-chord part: "A tonic seventh passing once among plain tonics is no blues but the key's moment."
