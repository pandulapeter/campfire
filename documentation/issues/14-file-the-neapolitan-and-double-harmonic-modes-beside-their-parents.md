# File the modes of the Neapolitan major and double harmonic scales in their parents' family, not under World

**Kind:** design  ·  **Severity:** low  ·  **Platforms:** all
**Files:**
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/Scales.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/ScalesTest.kt`,
`presentation/src/commonMain/composeResources/values/strings.xml` and `values-hu/strings.xml` (option C only)

**Challenged:** amended — step 2 understated the suggestion change: the song view caps each group at `MAX_GROUP_SCALES` (5), so the moved scales, now ahead of the pentatonic, blues, symmetric, and bebop families in catalogue order, can push one of those out of a capped group; and `PlayableScales`' home scale (the first seven-note holding scale) was checked and cannot change in what it adds.

**Decision:** awaiting the user. The recommendation is A (move the four and keep the label).

## Problem

`Scales.all` files four scales under `ScaleFamily.WORLD` ("World" / "Világzene") while naming a parent in `MINOR_AND_HARMONIC` ("Other harmonic" / "Egyéb összhangzatos"):

```kotlin
scale("oriental", ScaleFamily.WORLD, "0 1 4 5 6 9 10", "1 b2 3 4 b5 6 b7", "double_harmonic" to 5),
scale("major_locrian", ScaleFamily.WORLD, "0 2 4 5 6 8 10", "1 2 3 4 b5 b6 b7", "neapolitan_major" to 5),
scale("lydian_minor", ScaleFamily.WORLD, "0 2 4 6 7 8 10", "1 2 3 #4 5 b6 b7", "neapolitan_major" to 4),
scale("leading_whole_tone", ScaleFamily.WORLD, "0 2 4 6 8 10 11", "1 2 3 #4 #5 #6 7", "neapolitan_major" to 2),
```

Their parents, and `hungarian_minor` (`"double_harmonic" to 4`), sit in `MINOR_AND_HARMONIC`:

```kotlin
scale("double_harmonic", ScaleFamily.MINOR_AND_HARMONIC, "0 1 4 5 7 8 11", "1 b2 3 4 5 b6 7", ...),
scale("hungarian_minor", ScaleFamily.MINOR_AND_HARMONIC, ..., "double_harmonic" to 4, ...),
scale("neapolitan_major", ScaleFamily.MINOR_AND_HARMONIC, "0 1 3 5 7 9 11", "1 b2 b3 4 5 6 7"),
```

Every other mode in the catalogue (major, harmonic minor, melodic minor, pentatonic, blues and hexatonic, symmetric, and bebop) is browsed in its parent's family. A player who opens Neapolitan major's sheet reads "Mode 5 of …" lines that point the other way, and finds Major Locrian, Lydian minor, and Leading whole tone among the Persian and Flamenco scales.

## Fix

Options:

- **A (recommended): move the four beside their parents.**
  - `MINOR_AND_HARMONIC` then holds, in browse order: `harmonic_major`, `double_harmonic`, `hungarian_minor`, `oriental`, `hungarian_major`, `neapolitan_major`, `leading_whole_tone`, `lydian_minor`, `major_locrian`, `neapolitan_minor`. Each parent comes before its modes, and its modes go in degree order (2, 4, 5), as the major and harmonic minor modes are listed.
  - `WORLD` keeps `persian`, `enigmatic`, `flamenco`, and `spanish_eight_tone`.
- B: leave the browse families as "where a player looks", and let the "Mode n of" line be the only link. It needs no change, but then World is the one family whose modes live away from their parent.
- C: also rename `MINOR_AND_HARMONIC`'s label. `neapolitan_major` and its three modes have no augmented second, so "Other harmonic" is already loose. The alternative is "Other seven-note" / "Egyéb hétfokú". Not recommended: the family is where a player looks for the harmonic-flavoured exotics, and the label is good enough. If taken, change `scale_family_minor_and_harmonic` in both `strings.xml` files. The enum id `minor_and_harmonic` stays, since ids never change.

Steps for A:

1. In `Scales.kt`, change the four entries' family to `ScaleFamily.MINOR_AND_HARMONIC`, and move them in `all` to the positions listed above.
   - `ScalesTest`'s `` `the families are browsed in their order and hold every scale` `` asserts `Scales.all == ScaleFamily.entries.flatMap(Scales::of)`, so the list must stay grouped by family. Moving only the family argument fails that test.
   - Ids and strings are unchanged.
2. `ScaleChords.scalesContaining` lists non-common scales "in catalogue order", so the order of these four among a chord's or song's suggestions changes: they now come before every non-common pentatonic, blues and hexatonic, symmetric, and bebop scale, and `oriental` before `hungarian_major` and the Neapolitans.
   - **Capped lists change, not only order.** The song view's Scales section keeps `MAX_GROUP_SCALES` (5) per group
     (`songView/model/SongScaleGroups.kt`), after the common scales and `PlayableScales`' added ones. A song whose
     chords one of the four holds can now show it in place of a non-common pentatonic, symmetric, or bebop scale that
     was fifth. Say so to the user with the decision; it is the point of "beside their parents" only if they accept it.
   - **`PlayableScales`' home scale** (the first seven-note scale holding the chords, whose pentatonics and hexatonics
     it adds) can now be one of the four where it was `hungarian_major`, a Neapolitan, `persian`, or `enigmatic`. None
     of those nine holds a major or minor pentatonic or hexatonic, so what it adds is empty either way: no change.
   - No test pins their position (no test names these ids), but run `./gradlew :chordpro:desktopTest :presentation:desktopTest` to be sure.
3. No `CLAUDE.md` names the families' members, so nothing to update there. `documentation/plans/scale-library.md` is the feature's original plan. It is history, so leave it as is.

## Tests

Add to `ScalesTest`: `` `every mode is browsed in the family of the scale it is a mode of` ``. For every `scale` in `Scales.all` with a non-null `modeOf`, assert `scale.family == Scales.fromId(scale.modeOf!!.first)!!.family`, with `scale.id` as the message. Today it fails on the four ids above, and after step 1 it holds for the whole catalogue.

This test pins option A. If the user picks B, write no test.
