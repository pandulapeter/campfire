<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# The minor 6 pentatonic and the dominant pentatonic join the catalogue

**Kind:** design  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — two claims corrected, the fix unchanged: a `Dm7` sheet gains nothing (the D minor 6
pentatonic has B, not C; only `Dm6`, `G7`, `G9`, and a song of `Dm Dm6` or `G G7` gain one, probed with the entries
added, in 12th place or later); and a missing `scaleNameResource` branch is caught by `ScaleNamesTest`'s `every scale
of the catalogue has a name of its own`, not by the compiler. With the entries, strings, and branches applied, every
chordpro and presentation test passes.
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/Scales.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/ScalesTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/scales/ScaleNames.kt`,
`presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`

Written at HEAD `0a0babf27`. Lane U. A decision: whether to add them at all, and whether a song's row offers them.

## Problem

Two pentatonics players use are not in `Scales.all`, so neither the Scales tab nor a chord sheet can show them:

- the **minor 6 pentatonic**, `0 3 5 7 9` (`1 b3 4 5 6`): the minor pentatonic with the sixth for the seventh, the
  Dorian and minor-sixth-chord pentatonic, and the dominant-ninth pentatonic a fourth up (D minor 6 pentatonic over
  G9);
- the **dominant pentatonic**, `0 2 4 7 10` (`1 2 3 5 b7`): the major pentatonic with the flat seventh for the sixth,
  the Mixolydian pentatonic over a dominant seventh.

Neither is a set the catalogue has under another name (the `PENTATONIC` family holds `0 2 4 7 9`, `0 3 5 7 10`,
`0 2 5 7 10`, `0 3 5 8 10`, `0 2 5 7 9`, `0 2 3 7 8`, `0 1 5 7 8`, `0 1 5 7 10`, `0 1 5 6 10`, `0 2 3 7 9`,
`0 4 6 7 11`), and the two are modes of each other: the dominant pentatonic is the minor 6 pentatonic from its third
degree (`0 3 5 7 9` from `5`: `0 2 4 7 10`), neither a mode of any existing entry.

## Fix

Decision 1, recommended **yes**: add both. Decision 2, recommended **browse-only first**: no change to what a song's
row offers.

1. `Scales.kt`, in `all`, after `ritusen` (the major pentatonic and its modes first, then these two, then the Japanese
   scales), ids permanent:

   ```kotlin
           scale(
               "minor_6_pentatonic", ScaleFamily.PENTATONIC, "0 3 5 7 9", "1 b3 4 5 6",
               aliases = listOf("Minor sixth pentatonic", "Dorian pentatonic"),
           ),
           scale(
               "dominant_pentatonic", ScaleFamily.PENTATONIC, "0 2 4 7 10", "1 2 3 5 b7", "minor_6_pentatonic" to 3,
               aliases = listOf("Mixolydian pentatonic", "Dominant seventh pentatonic"),
           ),
   ```

   `modeOf` names the parent's id and the degree it starts on, as `"major_pentatonic" to 5` does for the minor
   pentatonic; `ScalesTest` checks it (`every mode is its parent rotated to start on its degree`, `every mode is browsed
   in the family of the scale it is a mode of`). `ScaleChords.triads` and `sevenths` return nothing for a five-note
   scale, as for every pentatonic.

2. Strings, both locales, beside `scale_minor_pentatonic` and `scale_aliases_man_gong` (the group commented
   `<!-- Scales screen and the scale sheet. The scale names follow the usual theory terms of each language. -->`):

   `values/strings.xml`:
   ```xml
   <string name="scale_minor_6_pentatonic">minor 6 pentatonic</string>
   <string name="scale_dominant_pentatonic">dominant pentatonic</string>
   …
   <string name="scale_aliases_minor_6_pentatonic">Minor sixth pentatonic, Dorian pentatonic</string>
   <string name="scale_aliases_dominant_pentatonic">Mixolydian pentatonic, Dominant seventh pentatonic</string>
   ```

   `values-hu/strings.xml`:
   ```xml
   <string name="scale_minor_6_pentatonic">moll 6 pentaton</string>
   <string name="scale_dominant_pentatonic">domináns pentaton</string>
   …
   <string name="scale_aliases_minor_6_pentatonic">Moll szext pentaton, Dór pentaton</string>
   <string name="scale_aliases_dominant_pentatonic">Mixolíd pentaton, Domináns szeptim pentaton</string>
   ```

   The names follow the file's casing (`dúr pentaton`, `moll pentaton` lowercase; aliases capitalised as
   `Blues moll pentaton` is). The user checks the Hungarian, as the 38th review's aliases were.

3. `ScaleNames.kt`: two branches in `scaleNameResource` (`"minor_6_pentatonic" -> Res.string.scale_minor_6_pentatonic`,
   `"dominant_pentatonic" -> Res.string.scale_dominant_pentatonic`) after `"minor_pentatonic"`, two in
   `scaleAliasesResource`, and the four imports (`scale_minor_6_pentatonic`, `scale_dominant_pentatonic`,
   `scale_aliases_minor_6_pentatonic`, `scale_aliases_dominant_pentatonic`) in the file's alphabetical order. The
   Scales tab's search already finds a scale by its name or an alias in either language
   (`ui/screens/scales/CLAUDE.md`), so "dorian pentatonic" finds the minor 6 pentatonic without more.

4. Where they are offered. Nothing in `PlayableScales` names a pentatonic but the major and the minor
   (`private val pentatonics = scalesOf(MAJOR_PENTATONIC_ID, MINOR_PENTATONIC_ID)`), so a song's row keeps offering
   those two; the new scales still appear wherever a list is built from containment: a chord sheet's row
   (`PlayableScales.over(chord)` lists every scale of `Scales.all` holding the chord, the five-note ones after the
   hexatonics: a `Dm6` gains the minor 6 pentatonic, a `G7` or `G9` the dominant pentatonic; a `Dm7` gains nothing,
   its C being no note of D minor 6 pentatonic, since every list is on the chord's own root), and a song's row where the song's chords are all inside one
   (`Dm Dm6 Dm Dm6` has the minor 6 pentatonic among its holders; `G G7 G G7` the dominant pentatonic), after the
   hexatonics and so usually past the four chips. Recommended: leave it there, browse-only in effect, and see whether
   players ask.

   The alternative, for lane G if the user wants it, is one line in `PlayableScales`: add both to `pentatonics`, so a
   Dorian or melodic minor home offers the minor 6 pentatonic as the pentatonic *inside* the home (after the minor
   pentatonic, which the `sortedBy` there puts first for a minor-third home) and a Mixolydian or Lydian dominant home
   the dominant pentatonic after the major one; natural minor and major contain neither, so the plain keys are
   untouched. That changes `a Mixolydian vamp offers the minor pentatonic and the blues after the major pentatonic`
   (`PlayableScalesTest`, expecting exactly `mixolydian, major_pentatonic, minor_pentatonic, blues` as the first four)
   and is a lane G plan of its own, not this one.

Drop this plan if the user declines decision 1.

## Tests

`ScalesTest` holds the invariants and needs no new test for a well-formed entry: `every scale is a sorted set of notes
within the octave starting at its root`, `every degree label names its interval` (`b3`, `6`, `b7` are in its map), `no
two scales are the same notes and every id is one scale` (which is what fails if either set is already in), `every mode
is its parent rotated to start on its degree`, `the steps of a scale close the octave`. Add one pin so the entries are
what players call them:

- `` `the minor 6 pentatonic is the minor pentatonic with a sixth, and the dominant pentatonic its mode` ``:
  `Scales.fromId("minor_6_pentatonic")!!.intervals == listOf(0, 3, 5, 7, 9)`,
  `Scales.fromId("dominant_pentatonic")!!.intervals == listOf(0, 2, 4, 7, 10)`, and
  `Scales.parentRoot(Scales.fromId("dominant_pentatonic")!!, root = 7) == 2` (G dominant pentatonic is D minor 6
  pentatonic's notes).

No new presentation test: `ScaleNamesTest`'s `every scale of the catalogue has a name of its own` fails for an id
`scaleNameResource` has no branch for, and a branch naming a missing string does not compile.

`CLAUDE.md` (`chordpro/scales`): the first bullet's "**One entry per set of notes**: a second name is an alias, never a
second scale (`ScalesTest`)" stays true; add to the `PlayableScales` bullet, after "the pentatonic sharing its third and
the blues", the words "(the major and the minor; the minor 6 and dominant pentatonics are offered only where they hold
the chords, as any other scale)".
