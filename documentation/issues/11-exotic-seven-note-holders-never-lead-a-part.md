<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# An exotic seven-note scale never leads a part or makes it a fit

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/PlayableScales.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/PlayableScalesTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroupsTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`

HEAD at the time of writing: `f70a58f89`. Lane G, plan 11 of 9 in the lane's numbering (last to run); assumes plans
01–10 have landed, plan 10's `suggestionFor(root, chords, home)` in particular.

## Problem

`PlayableScales.suggestionFor` takes any seven-note scale holding the chords as the home, and any holder of seven notes
or fewer as a fit:

```kotlin
val holding = ScaleChords.scalesContaining(root, notes)
val home = holding.firstOrNull { it.intervals.size == HOME_SCALE_SIZE }
…
return Suggestion(scales = scales, isFit = isDominantBlues || holding.any { it.intervals.size <= HOME_SCALE_SIZE })
```

`SongScaleGroups.suggestedFitOn` turns such a suggestion into a TONAL fit, and `tonalFitOf` reaches it for any part no
major, minor, Mixolydian, or Dorian scale holds. A chart with one chromatic neighbour is then a whole song in a scale
nobody plays, with no pentatonic, and no exception named. At HEAD:

- Bossa vamp without a key, `Cmaj7 Cmaj7 Dbmaj7 Dbmaj7 ×2` → `[HOME] [A] C: double_harmonic`, one chip. With
  `{key: C}` the same chart reads `C: major, major_pentatonic, lydian, lydian_sharp_2 (+7)` with `over Dbmaj7 → Db major,
  …`, which is what the no-key chart should read too.
- `C F Fm G ×2` without a key → `C: harmonic_major` (harmonic major holds the borrowed iv), where a player wants C major
  with `Fm` as the moment, exactly what the keyed chart gets.
- The `Caug` and `E7#9` vamps the review noted (`C: ionian_sharp_5, phrygian_dominant, ultralocrian`; E Hungarian major
  first) are already answered by the landed one-chord rule (`SongScaleGroups.oneChordFitOf`, commit `6c1b13c24`): a
  lone `Caug` reads `C: whole_tone, augmented, lydian_augmented, ionian_sharp_5 (+14)` and a lone `E7#9` reads
  `E: blues, minor_pentatonic, dominant_diminished, hungarian_major (+5)`, both the chord sheet's order
  (`ChordScaleChoices`). Nothing of this plan changes a one-chord part; what remains is the multi-chord case.

What must keep working: `C Db C Db` → `C: phrygian_dominant, double_harmonic, flamenco`; `Dm E Dm E` →
`D: dorian_sharp_4`; `Em F Em F` → `E: phrygian, minor_pentatonic, blues, …`; `C D C D` → `C: lydian, major_pentatonic`.
These are modes of the major, harmonic minor, and melodic minor scales, which players name and play.

## Fix

In `PlayableScales`:

```kotlin
/**
 * Whether [scale] may lead a part: the common scales and the modes of the major, harmonic minor, and melodic minor
 * scales, which a player names a song by. The other seven-note scales (double harmonic, harmonic major, the Hungarian
 * and Neapolitan ones) hold the odd chart whole, a bossa's ♭II or a borrowed iv, but nobody plays a song in them: such
 * a chart is its key with the chord as an exception, and the scale stays on offer further down.
 */
private val Scale.canLead get() = this in Scales.common || family in leadingFamilies

private val leadingFamilies = setOf(ScaleFamily.MAJOR_MODES, ScaleFamily.HARMONIC_MINOR_MODES, ScaleFamily.MELODIC_MINOR_MODES)
```

and in `suggestionFor`:

- `val home = given ?: holding.firstOrNull { it.intervals.size == HOME_SCALE_SIZE && it.canLead }`;
- `isFit = isDominantBlues || holding.any { it.intervals.size == HOME_SCALE_SIZE && it.canLead }`. A part held only by a
  hexatonic or pentatonic (the augmented scale over `C Caug`) is no fit either: a key is seven notes; the hexatonics
  and smaller stay on offer where a home scale is also there, and a chart only they hold falls to a loose home with the
  odd chord as its exception.

The list's order needs no change: the scales that cannot lead were already placed after the blues sound and the common
holders (`holding.filter { it.intervals.size == HOME_SCALE_SIZE }`), and with `home` null for them `source` is null, so
no pentatonic is seeded from a double harmonic scale. `over(chord)` (the chord sheet) reorders by `ChordScaleChoices`
and is unaffected in its first chips; its `isFit` is never read.

`ScaleExceptions.answerOver` filters `PlayableScales.over(chord)` by size and containment only and is unaffected.

### Expected probe output after the fix

- Bossa vamp without a key → `[HOME] [A] C: major, major_pentatonic, lydian, lydian_sharp_2 (+7)` with `over Dbmaj7 →
  Db major, lydian_sharp_2, double_harmonic, lydian, harmonic_major` (the keyed chart's row at HEAD: C major holds half,
  THIN, a guess kept alone).
- `C F Fm G ×2` without a key → `[HOME] [Verse] C: major, …` with `over Fm → F melodic_minor, dorian, …` (as the keyed
  chart reads at HEAD; lane S may change the answer over a borrowed iv).
- `C Db C Db`, `Dm E Dm E`, `Em F Em F`, `C D C D`, `E F E F` → unchanged.
- `Caug` and `E7#9` vamps → unchanged.
- `{key: E}` `E7#9 E7#9 A7 E7#9 B7 A7 E7#9 E7#9` → unchanged, a dominant blues (plan 02's jazz shape: every I chord a
  seventh with a sharp ninth, which `isDominantBlues` allows, I/IV/V all of the plays).

## Tests

`PlayableScalesTest`:

- `` `an exotic seven-note scale never leads a suggestion or makes a fit` ``: `PlayableScales.suggestionFor(0,
  [Cmaj7, Dbmaj7])` has `isFit == false`; `suggestionFor(0, [C, F, Fm, G])` has `isFit == false` and its scales
  contain `harmonic_major` after whatever precedes it; `suggestionFor(0, [C, Db])` has `isFit == true` and
  `scales.first().id == "phrygian_dominant"`; `suggestionFor(2, [Dm, E])` has `isFit == true` with `dorian_sharp_4`
  first; `suggestionFor(0, [C, Caug])` has `isFit == false` (the augmented scale is six notes, and harmonic major, the
  one seven-note holder, cannot lead).
- Keep `a chord's sheet starts with its quality's textbook scales` (`Caug` → `whole_tone, augmented`; `E7#9` →
  `blues, minor_pentatonic`; `Cmaj7#5` → `lydian_augmented, ionian_sharp_5`) and `every suggestion is a scale on the
  root, never the chromatic one`.

`SongScaleGroupsTest`:

- `` `a chart only an exotic scale holds whole is its key with the chord as the exception` ``:
  `song("Cmaj7 Cmaj7 Dbmaj7 Dbmaj7 Cmaj7 Cmaj7 Dbmaj7 Dbmaj7")` is `"C" to "major"` with exceptions `[Dbmaj7]`;
  `song("C F Fm G C F Fm G")` is `"C" to "major"` with exceptions `[Fm]`; `song("C Db C Db C Db C Db")` is
  `"C" to "phrygian_dominant"`; `song("Dm E Dm E")` is `"D" to "dorian_sharp_4"`; `song("Em F Em F")` is
  `"E" to "phrygian"`.
- Keep `a vamp on one chord is answered as the chord's sheet is, unless a key says otherwise` and `an eight-note
  answer is never taken on another root`.

## CLAUDE.md

In the scales `CLAUDE.md`:

- `SongScaleGroups` bullet, "Groups are decided on scales of at most seven notes, or an eight-note scale's passing tone
  would join a major verse and a Mixolydian chorus": extend with ", and of seven only the common scales and the modes
  of the major, harmonic minor, and melodic minor scales: a double harmonic or harmonic major scale holding a chart
  whole never names it, the odd chord is its key's exception".
- `PlayableScales` bullet, "then the other common holders, other seven-note ones, hexatonics and smaller, eight-note
  last": unchanged wording; add "(the seven-note ones outside those families never lead)" after "other seven-note
  ones".
