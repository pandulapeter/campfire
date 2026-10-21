<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# A home's row is built on the home, so a minor home never offers the parallel major and its pentatonic

**Challenged:** amended — `tonalFitOn`'s change is a fix of its own (`{key: Am}` `A5 D5 E5 A5` reads `natural_minor,
major, major_pentatonic, dorian` at HEAD), but it must not filter a part of power chords read without a key, whose
home is major only because `homeScaleIds` tries it first (`A5 D5 E5 A5` would go from `major, major_pentatonic,
natural_minor, dorian` to `major, major_pentatonic, mixolydian, mixolydian_flat_6`); the "first four chips unchanged"
claim is corrected (power-chord rows under a key, a Dorian row whose fourth chip was Mixolydian); the parameter is
named `given` as the sketch uses it.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/PlayableScales.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroups.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/PlayableScalesTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroupsTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`

HEAD at the time of writing: `f70a58f89`. Lane G, plan 10 of 9 in the lane's numbering (eighth to run); assumes plans
01–08 have landed. Plan 11 builds on the overload added here.

## Problem

A loose home's row is the home followed by whatever `PlayableScales.over` suggests for the chords the home holds:

```kotlin
private fun minorHomeFitOn(harmony: Harmony, root: SpelledNote, level: Level): Fit? {
    val home = naturalMinor
    val held = harmony.chords.filter { holdsChord(home, root.pitchClass, it) }
    if (held.isEmpty()) return null
    return Fit(root, listOf(home) + (PlayableScales.over(root.pitchClass, held) - home), level, home, harmony.exceptionsTo(home, root))
}
```

and the same in `looseFitOf` (`Fit(root, listOf(home) + (PlayableScales.over(root.pitchClass, held) - home), …)` and the
riff branch's `PlayableScales.rockRiffScales + home + PlayableScales.over(root.pitchClass, held)`). `over` knows nothing of
the home: `suggestionFor` takes the first seven-note scale holding the chords as *its* home and seeds the pentatonic of
that scale's third:

```kotlin
val home = holding.firstOrNull { it.intervals.size == HOME_SCALE_SIZE }
…
val source = if (home?.id in raisedMinorIds) naturalMinor else home
val inside = source?.let { scale -> pentatonics.filter { scale.intervals.containsAll(it.intervals) } }.orEmpty()
    .sortedBy { MAJOR_THIRD !in (source?.intervals.orEmpty()) == (it.id == MAJOR_PENTATONIC_ID) }
```

Where the held chords are power chords only, every scale on the root holds them and `Scales.common` puts major first,
so a natural-minor home is followed by G major and the major pentatonic. `{key: Gm}` chorus `C Ab G5 G5 C Ab G5 G5`
alone (the minor family holds `C` through melodic minor and `G5`, 6 of 8; natural minor itself holds only the `G5`s):

```
[HOME] [Chorus] G: natural_minor, major, major_pentatonic, minor_pentatonic (+39)
    over C → G dorian, melodic_minor
    over Ab → G phrygian
```

The second and third chips contradict the first, and the 39 scales behind them are every scale on G.

## Fix

Give `PlayableScales` an overload that takes the home the row is built for, and use it wherever a home is known:

```kotlin
/**
 * [over] for a part whose [home] is already decided (a key's scale, a home with exceptions): the row leads with the
 * home, the pentatonic and the blues inside it follow its third, and of the other scales holding [chords] only those
 * sharing the home's third, or having none, are offered, since a scale of the other third contradicts the home
 * whatever the chords allow (power chords allow everything).
 */
internal fun over(root: Int, chords: List<Chord>, home: Scale): List<Scale> = suggestionFor(root, chords, home)?.scales.orEmpty()
```

with `suggestionFor(root, chords, given: Scale? = null)` (the public `over(root, chords)` passes null, so its behaviour
is unchanged; `suggestionFor` is `internal`, and a defaulted parameter on it is fine, this is no Koin definition):

- `val home = given ?: holding.firstOrNull { it.intervals.size == HOME_SCALE_SIZE }` (plan 11 then restricts the
  `firstOrNull` to scales that may lead);
- `source` as today from that home, so `inside` follows the home's third (natural minor's pentatonics for a harmonic or
  melodic minor home, as the `raisedMinorIds` rule says);
- where a home is given, `holding` is filtered before the list is assembled:

  ```kotlin
  val third = home.intervals.firstOrNull { it == MINOR_THIRD || it == MAJOR_THIRD }
  val agreeing = if (given == null || third == null) holding else holding.filter { scale ->
      third in scale.intervals || scale.intervals.none { it == MINOR_THIRD || it == MAJOR_THIRD }
  }
  ```

  (`MINOR_THIRD = 3` is a new constant of the object.) A scale holding both thirds (the major blues, bebop Dorian) shares
  the home's and stays. The given home is listed first whether or not it holds every chord (a loose home does not), as
  today's callers put it first by hand;
- `isFit` is unchanged (the callers with a home never read it).

In `SongScaleGroups`, `minorHomeFitOn`, the two sites in `looseFitOf`, and `tonalFitOn` (`listOf(home) + (it.scales - home)`,
where `suggestionFor(root, chords)` picks the first holder as the seed) call `PlayableScales.over(root.pitchClass, held,
home)`. `tonalFitOn` has the same fault where the chords are power chords: `{key: Am}` `A5 D5 E5 A5` is held by A
natural minor, but the first holder `suggestionFor` takes is major, so the row is `natural_minor, major,
major_pentatonic, dorian` (probe at HEAD). With triads the first holder is the home anyway (both read major, natural
minor, Dorian, Mixolydian in that order), so the change matters only for thirdless chords, and there only where the
home's third was decided: `tonalFitOn` passes the home where a chord of the part has a third or the home is on the root
of the key in force, and otherwise keeps `PlayableScales.over(root.pitchClass, chords)`. A part of power chords read
without a key (`A5 D5 E5 A5`, TONAL A major only because `homeScaleIds` tries major first) is then unchanged, `major,
major_pentatonic, natural_minor, dorian`; filtered it would lose every minor-third scale for
`major, major_pentatonic, mixolydian, mixolydian_flat_6`, its home's third being nobody's choice. The riff branch keeps `rockRiffScales` first and
passes the riff's home (Mixolydian or natural minor), so the Smoke riff's row stays `minor_pentatonic, blues,
natural_minor, dorian, …` and loses nothing.

Plan 07's Phrygian dominant home has a major third, so a falseta's row keeps `double_harmonic` (major third) and drops
nothing it offers today.

### Expected probe output after the fix

- `{key: Gm}` `C Ab G5 G5 C Ab G5 G5` → `[HOME] [Chorus] G: natural_minor, minor_pentatonic, blues, dorian (+N)` with
  the same two moments; the count behind the fourth chip falls to the minor-third scales on G.
- Blue Bossa (plan 01) → `C: natural_minor, minor_pentatonic, blues, …` unchanged in its first chips (the held `Cm7`,
  `Fm7`, `Dm7b5` already seed natural minor).
- `{key: Am}` `A5 D5 E5 A5` → `A: natural_minor, minor_pentatonic, blues, dorian` (HEAD: `natural_minor, major,
  major_pentatonic, dorian`); `{key: A}` the same chords → `A: major, major_pentatonic, mixolydian, mixolydian_flat_6`
  (HEAD: `major, major_pentatonic, natural_minor, dorian`: the key says major); without a key → unchanged.
- A Dorian row of mostly power chords loses a fourth chip of the other third: `G5 F5 G5 G5 F5 G5 C G5 C G5` read as G
  Dorian (HEAD: `dorian, minor_pentatonic, blues, mixolydian`) becomes `dorian, minor_pentatonic, blues,
  dorian_flat_2`, which lane U's plan 14 then cuts to `minor_hexatonic`.
- Every other major, Mixolydian, Dorian, riff, and blues row in `corpus-all.txt` → unchanged in its first four chips;
  diff the probe output before and after, and read any changed row.

## Tests

`PlayableScalesTest`:

- `` `a home given leads the row and keeps only the scales of its third` ``: `PlayableScales.over(7, [G5, G5],
  Scales.fromId("natural_minor")!!)` starts `natural_minor, minor_pentatonic, blues` and contains neither `major` nor
  `major_pentatonic`; `over(7, [G5, G5], Scales.fromId("mixolydian")!!)` starts `mixolydian, major_pentatonic,
  minor_pentatonic, blues` (the Mixolydian rule) and contains no `natural_minor`; `over(7, [G5], major)` keeps
  `egyptian` (no third) and drops `dorian`.
- Keep every existing `over(root, …)` assertion (`a major key offers the pentatonic and hexatonic inside its scale…`,
  `a minor key offers its pentatonic and the blues scale`, `rare scales come last`, …): the public overload is untouched.

`SongScaleGroupsTest`:

- `` `a minor home of power chords never offers the parallel major` ``: `song("C Ab G5 G5 C Ab G5 G5", key = "Gm")` and
  `song("A5 D5 E5 A5", key = "Am")` are each a single group whose `scales.take(4)` ids are `[natural_minor,
  minor_pentatonic, blues, dorian]` and whose scales contain no `major` and no `major_pentatonic`; `song("A5 D5 E5 A5")`
  without a key keeps `natural_minor` among its scales (its home's third is nobody's choice).

## CLAUDE.md

In the `PlayableScales` bullet of the scales `CLAUDE.md`, "**What is suggested is `PlayableScales`**, not bare
containment: the seven-note home scale holding the chords, the pentatonic sharing its third and the blues": add after
it "(a group's row is built on the group's home, every other holder of its third's opposite dropped, so a minor home
over power chords never offers the parallel major; a part of power chords alone, read without a key, has no third to
keep)".
