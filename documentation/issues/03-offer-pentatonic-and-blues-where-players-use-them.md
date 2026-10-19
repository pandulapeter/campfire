# Offer the pentatonic and blues scales where players use them, and put the rare scales last

**Challenged:** amended — `PlayableScalesTest`'s `a dominant blues offers the blues scales…` asserts
`isDominantBlues(C, F7)` is false, which fix 2 makes true, so it cannot "stay green": the assertion moves to the new test;
the order now says where a dominant blues' added scales go when a seven-note scale holds the chords (a lone `C7`, `C7 F
G`), which it left out (`major_blues` had no step).

**Kind:** design  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/PlayableScales.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/Scales.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/PlayableScalesTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/ScaleChordsTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongScaleGroups.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongScaleGroupsTest.kt`

**Decision:** awaiting the user on the new `Scales.common` order (B10) and on keeping eight-note scales out of a song's
chips (C1).

## Problem

`PlayableScales.suggestionFor` (`PlayableScales.kt:42-53`) builds the list:

```kotlin
val holding = ScaleChords.scalesContaining(root, notes)
val home = holding.firstOrNull { it.intervals.size == HOME_SCALE_SIZE }
val inside = home?.let { scale -> insideHome.filter { scale.intervals.containsAll(it.intervals) } }.orEmpty()
val minorBlues = if (inside.any { it.id == MINOR_PENTATONIC_ID }) listOfNotNull(Scales.fromId(BLUES_ID)) else emptyList()
val dominantBlues = if (isDominantBlues(root, chords)) dominantBluesScales else emptyList()
...
val scales = (holding.filter { it in Scales.common } + added + holding.filter { it !in Scales.common }).distinct()
```

**B2, pentatonics and blues taken only from inside the first seven-note scale.** Measured at HEAD
(`PlayableScales.over(root, chords)`, full lists):

- `Am Dm E7` (A) → harmonic_minor, bebop_melodic_minor, bebop_harmonic_minor: no A minor pentatonic, no blues (their
  G is not in harmonic minor), though that is what a player plays over the Am and Dm.
- `E D A` (E) → mixolydian, major_pentatonic, major_hexatonic, bebop_dominant, bebop_dorian: no E minor pentatonic or
  blues, the rock player's first choice over a Mixolydian vamp.
- `C F7 G` (C) → **nothing**: `isDominantBlues` (`PlayableScales.kt:59-63`) asks the I to be the seventh, so a IV7
  blues gets no list at all.

**C1, the five chips a major song shows.** `C F G`, `C G Am F`, `C Dm Em F G Am` all give exactly
major, major_pentatonic, major_hexatonic, bebop_dominant, bebop_major — two bebop scales (a jazz device; bebop
dominant's ♭7 over a major song) and a hexatonic of little use. (The relative minor pentatonic on its own root was
plan 06, withdrawn by the user's decision: every chip stays on the part's root.)

**B10, `Scales.common`** (`Scales.kt:127-130`):

```kotlin
public val common: List<Scale> = listOf(
    "major", "natural_minor", "harmonic_minor", "melodic_minor", "major_pentatonic", "minor_pentatonic", "blues",
    "major_blues", "dorian", "mixolydian", CHROMATIC_ID,
).map(byId::getValue)
```

It orders the Scales tab's Common chip, `ScaleChords.scalesContaining` (every chord sheet) and the "common first" part
of `PlayableScales`: harmonic and melodic minor before the pentatonics and the blues, Dorian and Mixolydian after major
blues. A guitarist reaches for major, natural minor, minor pentatonic, major pentatonic, blues, Dorian, Mixolydian
first.

After plan 02, `Am Dm E7` in a song is re-expressed as A natural minor with an E7 exception, so its group already
offers the minor pentatonic and the blues; the harmonic-minor rule below still matters for a chord sheet and for a
strict harmonic home plan 02 does not re-express.

## Fix

**1. The order of a suggestion** (rewrite the last lines of `suggestionFor`; the `dominantBluesScales` list for a
dominant blues stays exactly as the test pins it):

1. the home scale (the first seven-note holding scale in `Scales.common` order, else catalogue order);
2. the pentatonics inside the home — the one sharing the home's third first (major pentatonic for a major third,
   minor for a minor) — then the blues scale where the minor pentatonic is among them;
   - where the home is harmonic or melodic minor, these come from **natural minor on the root** instead (the safe set
     over a minor song's i and iv);
   - where the home is Mixolydian, the **minor pentatonic and the blues follow the major pentatonic**;
3. (nothing: the relative pentatonic on its own root was withdrawn with plan 06);
4. the other common holding scales of at most seven notes, in `Scales.common` order;
5. the other holding scales of seven notes, in catalogue order;
6. the hexatonics (inside or holding), then every other scale of six notes or fewer;
7. the holding scales of eight notes.

Over a dominant blues: where no scale of seven notes or fewer holds the chords (`C7 F7 G7`, the twelve-bar), the list
is `dominantBluesScales` as pinned, then the holding ones; where one does (a lone `C7` on a chord sheet, `C7 F G`), its
home is Mixolydian and the dominant blues' scales step 2 has not placed (`major_blues`) follow step 2's, before step 4.

**2. `isDominantBlues`**: chords all major (third 4) on I, IV, V, and **the I or the IV** a dominant seventh
(`MINOR_SEVENTH in it.intervals`), so `C F7 G` and `C7 F G` are a blues. The brief's wider rule ("any of them a
dominant seventh") is **not** recommended: `C F G7` is the plain V7 cadence of every hymn and folk song, and would put
the minor pentatonic and the blues first on all of them.

**3. `Scales.common`**, recommended order (**Decision**):
`major, natural_minor, minor_pentatonic, major_pentatonic, blues, dorian, mixolydian, harmonic_minor, melodic_minor,
major_blues, chromatic`. What else it changes: the Scales tab's Common chip lists them in this order
(`ScaleLibraryTest` compares with `Scales.common` itself, so it stays green); every chord sheet's order through
`ScaleChords.scalesContaining` (C reads major, major_pentatonic, mixolydian, major_blues, …; Cm reads natural_minor,
minor_pentatonic, blues, dorian, harmonic_minor, …); the promo video's Common scroll (`tools/screenshots/.../Promo.kt`,
regenerated at release time, not here). Update the KDoc "in the order a player reaches for them" (it then is).

**4. A song's chips** (C1, **Decision**): in `songScaleGroupsOf`, drop eight-note scales from a group's list when it
has any scale of seven notes or fewer, before `take(MAX_GROUP_SCALES)`; they stay on the chord sheet. C major then
reads major, major_pentatonic, major_hexatonic. The alternative is to keep them last
(order alone); recommended to drop them, since a five-chip row is the whole answer for most songs.

Update the `PlayableScales` KDoc (its bullet list and "The common scales that hold the notes come first, then these…")
and the scales `CLAUDE.md` bullet "What is suggested is `PlayableScales`…" to the new order in the same length.

Expected after this plan (full `PlayableScales.over` lists):
- `C F G` → major, major_pentatonic, major_hexatonic, bebop_dominant, bebop_major (the song section shows the first three).
- `Am Dm Em` → natural_minor, minor_pentatonic, blues, minor_hexatonic, bebop_harmonic_minor.
- `E D A` → mixolydian, major_pentatonic, minor_pentatonic, blues, major_hexatonic, bebop_dominant, bebop_dorian.
- `Am Dm E7` → harmonic_minor, minor_pentatonic, blues, …, the bebop scales last.
- `C F7 G` → minor_pentatonic, blues, major_pentatonic, major_blues, mixolydian.

**Decision (from the challenge, awaiting the user):** rock riffs of major chords on the minor-pentatonic roots (`E G A E`,
`C Eb F C`, `G Bb C G`) get no minor pentatonic or blues even after 02 and this plan: they read as major with the ♭III or
♭VII named as an exception. Recommended: a "rock riff" rule beside the dominant blues one — every chord a major triad or
dominant seventh on I, ♭III, IV, V or ♭VII, with a ♭III or ♭VII among them, adds the minor pentatonic and the blues scale
on the root after the holding scales (the ♭3 against the chords' major third is the style, as in a blues); or leave it.

## Tests

`PlayableScalesTest`:
- `a minor key with a major fifth still offers its pentatonic and the blues` — `over(9, "Am", "Dm", "E7")` starts with
  harmonic_minor, then minor_pentatonic, blues.
- `a Mixolydian vamp offers the minor pentatonic and the blues after the major pentatonic` — `over(4, "E", "D", "A")`:
  mixolydian, major_pentatonic, minor_pentatonic, blues first.
- `a blues with a seventh on the IV is a dominant blues, a plain V7 cadence is not` — `isDominantBlues` true for
  `C F7 G` and `C7 F G`, false for `C F G7`; `over(0, "C", "F", "G7")` has no blues.
- `rare scales come last` — in `over(0, "C", "F", "G")` every eight-note scale comes after every scale of six or fewer.
- In `a dominant blues offers the blues scales…`, **delete** `assertFalse(PlayableScales.isDominantBlues(0,
  listOf(C, F7)))` (`PlayableScalesTest.kt:44`): fix 2 makes it true, and the new test above pins it. Keep the rest of
  that test (the pinned list, `C9` true, `C7 Dm` false) and the other tests green; update `a major key offers the
  pentatonic…` if its index assertion moves.
- `a lone dominant seventh offers Mixolydian, then the pentatonics and the blues` — `over(0, "C7")` starts mixolydian,
  major_pentatonic, minor_pentatonic, blues, major_blues.

`ScaleChordsTest`: `the common scales come first and the chromatic one never` becomes
`listOf("major", "major_pentatonic", "mixolydian", "major_blues")` for C (only if the order is accepted).

Presentation `SongScaleGroupsTest`: `a song's chips leave out the eight-note scales` — `[C]One [F]two [G]three`: no
scale of eight notes among the chips. `a group offers at most a handful of scales` (`[C]One [G]two`) asserts
`<= MAX_GROUP_SCALES` and that the list is non-empty, since dropping the bebop scales can leave fewer than five.
