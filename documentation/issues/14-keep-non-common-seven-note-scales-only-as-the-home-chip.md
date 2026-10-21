<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# A song's row keeps a non-common seven-note scale only as its first chip

**Kind:** design  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the first two chips stay whatever they are, not only the first: as first written the filter
stripped a one-chord vamp's textbook pair (`SongScaleGroups.oneChordFitOf`, the chord sheet's `ChordScaleChoices`
order, settled by `6c1b13c24`) to one chip: `Bm7b5` lost Locrian ♮2 (`locrian, blues`), `Cmaj7#5` Ionian ♯5
(`lydian_augmented` alone), `Caug` the augmented scale (`whole_tone` alone). Every row the plan meant to trim is trimmed
the same, its second chip being a pentatonic or the blues. The "today" lists of the vamps were re-probed (they had
`locrian_natural_2` second, `lydian_augmented` first).
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongScaleGroups.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongScaleGroupsTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/CLAUDE.md`

Written at HEAD `0a0babf27`. Lane U, after plan 09 (both edit `songScaleGroupsOf`'s file; this one touches only
`chipsOf`).

## Problem

`chipsOf` keeps the engine's order and cuts at `MAX_GROUP_SCALES = 4`, dropping only the eight-note scales:

```kotlin
private fun chipsOf(scales: List<Scale>) =
    (if (scales.any { it.intervals.size <= HOME_SCALE_SIZE }) scales.filter { it.intervals.size <= HOME_SCALE_SIZE } else scales).take(MAX_GROUP_SCALES)
```

`PlayableScales` lists the home, its pentatonics and blues, the other common holders, then every other seven-note
holder before the hexatonics. When the chords are few, that fourth chip is a rarity. The probe at HEAD prints the
engine's list; the chips are its first four (none of these has an eight-note scale among them):

```
## 14 Cmaj7 vamp alone with key        {key: C}, Cmaj7 ×4
  [HOME] [A] C: major, major_pentatonic, lydian, lydian_sharp_2 (+7)
## 12 Ragtime VI7 II7 V7 (C A7 D7 G7 C)
  [HOME] [A] C: major, major_pentatonic, harmonic_major, major_hexatonic (+2)
## 14 Thrill is gone (Bm7 Em7 Gmaj7 F#7)   {key: Bm}
  [HOME] [Verse] B: natural_minor, minor_pentatonic, blues, phrygian (+3)
## 14 Metal Em F Em F
  [HOME] [Verse] E: phrygian, minor_pentatonic, blues, neapolitan_minor (+1)
## 14 Smells Like Teen Spirit (F5 Bb5 Ab5 Db5)
  [HOME] [Riff, Verse, Chorus] F: minor_pentatonic, blues, natural_minor, phrygian (+3)
## 14 House of the Rising Sun (Am)
  [HOME] [Verse] A: natural_minor, minor_pentatonic, blues, phrygian (+3)
```

A Lydian ♯2 chip over a Cmaj7 vamp, Neapolitan minor over `Em F`, Phrygian as the fourth answer to Teen Spirit: a
player reading the row takes them for suggestions on a par with the first three. The chord sheet is where the full
list belongs; the song's row is "what a player reaches for first" (the `songScaleGroupsOf` KDoc).

## Fix

After the first two chips, keep only `Scales.common` scales and the two hexatonics; the first two stay whatever they
are: the first is the home scale, the answer even when rare (Phrygian over `Em F`, Lydian over `C D C D`, Phrygian
dominant over a falseta), and the second is either the pentatonic or the blues inside it or, over a one-chord vamp,
the second of the chord's textbook scales (Locrian ♮2 after Locrian over a `Bm7b5`, Ionian ♯5 after Lydian augmented
over a `Cmaj7#5`), which the chord sheet's order put there on purpose:

```kotlin
/**
 * The chips of a group's [scales]: the eight-note ones left out where a smaller scale fits, since bebop and the
 * diminished scales are a jazz player's passing tones rather than the answer to what a song is in; after the first
 * two, the home scale whatever its rarity and the pentatonic inside it or a vamp's second textbook scale, only the
 * common scales and the hexatonics, since a Lydian ♯2 beside the major scale reads as a suggestion on a par with it;
 * then the first [MAX_GROUP_SCALES]. A chord sheet keeps them all.
 */
private fun chipsOf(scales: List<Scale>): List<Scale> {
    val shown = if (scales.any { it.intervals.size <= HOME_SCALE_SIZE }) scales.filter { it.intervals.size <= HOME_SCALE_SIZE } else scales
    return (shown.take(KEPT_CHIPS) + shown.drop(KEPT_CHIPS).filter { it in Scales.common || it.id in hexatonicIds }).take(MAX_GROUP_SCALES)
}

private const val KEPT_CHIPS = 2

private val hexatonicIds = setOf("major_hexatonic", "minor_hexatonic")
```

The pentatonics and the blues are in `Scales.common`, so a rock riff's and a blues's chips are untouched; the chromatic
scale is in `Scales.common` but `ScaleChords.scalesContaining` never offers it. Rows may now have fewer than four chips.

Expected chips at HEAD (ids):

| Chart | Today | After |
|---|---|---|
| `{key: C}` `Cmaj7 ×4` | major, major_pentatonic, lydian, lydian_sharp_2 | major, major_pentatonic, major_hexatonic |
| `C A7 D7 G7 C` | major, major_pentatonic, harmonic_major, major_hexatonic | major, major_pentatonic, major_hexatonic |
| `{key: Bm}` `Bm7 Em7 Gmaj7 F#7` | natural_minor, minor_pentatonic, blues, phrygian | natural_minor, minor_pentatonic, blues, minor_hexatonic |
| `Em F Em F` | phrygian, minor_pentatonic, blues, neapolitan_minor | phrygian, minor_pentatonic, blues |
| Teen Spirit `F5 Bb5 Ab5 Db5` | minor_pentatonic, blues, natural_minor, phrygian | minor_pentatonic, blues, natural_minor, minor_hexatonic |
| House of the Rising Sun | natural_minor, minor_pentatonic, blues, phrygian | natural_minor, minor_pentatonic, blues, minor_hexatonic |

The hexatonic in the "after" column is the one `PlayableScales.suggestionFor` offers *inside* the home scale (as it
offers the pentatonic), among the "(+n)" the probe does not print; where the engine's list after the merge has none,
the row is three chips. Pin the probe's actual lists.
| one-chord vamps `Bm7b5`, `Cmaj7#5`, `Caug` | locrian, locrian_natural_2, blues, locrian_natural_6; lydian_augmented, ionian_sharp_5, harmonic_major, double_harmonic; whole_tone, augmented, lydian_augmented, ionian_sharp_5 | locrian, locrian_natural_2, blues; lydian_augmented, ionian_sharp_5; whole_tone, augmented |

The one-chord vamps keep their textbook pair and lose the rarities after it on the song page; the chord's own sheet
(`ScaleChipRow` on the Chords section) still lists every one.

Lane G's plan 10 reorders what follows a minor home; this filter keeps whatever order it leaves and removes only what
is neither common nor a hexatonic, so a scale plan 10 puts second to fourth survives if it is one of those. Lane U is
cut after lanes G and S have merged: re-run the probe on `Bm7 Em7 Gmaj7 F#7`, `Em F Em F`, and Teen Spirit before
pinning the lists in the tests, and pin what the merged engine gives.

## Tests

In `SongScaleGroupsTest` (presentation), with `groups(text)`:

- `` `a song's chips keep a rarity only as the home scale` ``:
  `groups("{key: C}\n{start_of_verse}\n[Cmaj7]x [Cmaj7]x [Cmaj7]x [Cmaj7]x\n{end_of_verse}").single().scales.map { it.id }`
  is `listOf("major", "major_pentatonic", "major_hexatonic")`; and
  `groups("{start_of_verse}\n[Em]x [F]x [Em]x [F]x [Em]x [F]x [Em]x [F]x\n{end_of_verse}").single().scales` has
  `first().id == "phrygian"` and `drop(1).all { it in Scales.common }`.
- `` `a one-chord vamp keeps its chord's two textbook scales` ``:
  `groups("{start_of_verse}\n[Bm7b5]x [Bm7b5]x [Bm7b5]x [Bm7b5]x\n{end_of_verse}").single().scales.map { it.id }` is
  `listOf("locrian", "locrian_natural_2", "blues")`, and the same with `Cmaj7#5` is
  `listOf("lydian_augmented", "ionian_sharp_5")` (re-probe both after lanes G and S and pin what the merged engine gives).
- The existing `a group offers at most a handful of scales` and `a song's chips leave out the eight-note scales` stay
  and must pass.

`CLAUDE.md` (`ui/songView`, "The Scales section"): "At most four chips a group, and one chip a chord" → "At most four
chips a group, after the first two only common scales and the hexatonics (a rare home scale leads, and a vamp keeps its
chord's two textbook scales, but a rare fourth chip never follows; the chord sheet keeps the full list), and one chip
a chord".
