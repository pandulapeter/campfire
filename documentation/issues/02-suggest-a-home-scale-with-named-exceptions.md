# Suggest a home scale with the chords it leaves out named as exceptions, where no one scale holds every chord

**Challenged:** amended — a fit's `home` is the scale that decided it, not "the first of its scales" (that named every
chord of a twelve-bar blues an exception, the minor pentatonic holding none of them); a strict fit has no exceptions
except the minor-family re-expression; an exception's scales are the seven-note ones holding the chord (the stated
`PlayableScales.over` list put A minor pentatonic first over an `A7` in C, and the plan's own table only reproduces with
this filter); a natural-minor home offers Dorian over a chord its sixth holds (House of the Rising Sun's D, Hotel
California's E); LOOSE also tries the first chord's root (`C F G E` lost its group); a LOOSE minor home's list comes from
the chords natural minor holds; tests added.

**Kind:** design  ·  **Severity:** high  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroups.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/ScaleGroup.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/ScaleException.kt` (new),
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroupsTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/ScaleExceptionsTest.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongScaleGroups.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongLyricsModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/RenderSection.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SongScalesSection.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/ScaleChipRows.kt`,
`presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongScaleGroupsTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/RenderSectionTest.kt`

**Decision:** awaiting the user — the design (recommended: (a) home scale + exceptions, with (c) the minor family) and
the floor (recommended: two thirds of the chords).

## Problem

A group needs one scale holding every note of every chord: `PlayableScales.suggestionFor` collects the notes of all
chords and asks `ScaleChords.scalesContaining(root, notes)` (`PlayableScales.kt:43-44`), and `SongScaleGroups.of`
leaves out a part no suggestion fits:

```kotlin
// SongScaleGroups.kt:61
fitOf(listOf(part), isStrict)?.let { groups += Group(mutableListOf(part), it, isStrict) }
```

One chord outside the key is enough to lose the part, so the commonest harmony in songs — a secondary dominant, a
major V in a minor key, a borrowed chord — makes the section silently vanish from the Scales section, or leaves it
with only an eight-note scale. Measured at HEAD through `SongScaleGroups.of` (one section each, no `{key}` unless
given):

- **No group at all:** `C A7 Dm G7`; `C C#dim Dm G7`; `Am Dm Bb E7`; Hotel California `Bm F# A E G D Em F#`;
  `C Ab Bb C`; jazz blues `C7 F7 C7 Dm7 G7 C7`; House of the Rising Sun `Am C D F Am C E`; Yesterday
  `F Em7 A7 Dm Dm/C Bb C7 F`; Something `C Cmaj7 C7 F D7 G Am`; All of Me; rhythm changes.
- **Only an eight-note scale:** `Am G F E` → A bebop_harmonic_minor; `Am Dm G C F Bm7b5 E7 Am` → A
  bebop_harmonic_minor; minor blues `Am7 Dm7 Am7 E7` → A bebop_harmonic_minor only (no minor pentatonic, no blues);
  `Em D C B7` → E bebop_harmonic_minor; `C E7 Am F G` → C bebop_major; Creep `G B C Cm` → G bebop_major;
  `C Bb F G` → C bebop_dominant; `F G C E7` → E flamenco.
- **C2, a split a player would not make:** `{key: Am}`, verse `Am Dm Em`, chorus `Am Dm E` → two groups on A,
  natural_minor… and harmonic_minor…, where a player says "A minor" once, with harmonic minor over the E.

## Fix

After plan 01 the root of a part list is chosen by score (key where its scale holds, home scales, Mixolydian/Dorian on
a heavier root, then the fallback). This plan adds two kinds of answer under that choice, carries what is left out, and
draws it.

### Options

- **(a), recommended:** a *home scale* that holds most chords, plus *exceptions*: each chord it leaves out, with the
  scales to play over that chord.
- (b) a note tolerance (accept a scale missing one note): silently suggests a wrong note over the outside chord.
- (c) the **minor family** alone: natural, harmonic and melodic minor on one root count as one home. Fixes C2, the
  Andalusian cadence and the minor blues, not the major-key songs. Recommended **together with (a)**, as its first step.

### The model (`:chordpro`, explicit API)

```kotlin
// ScaleGroup.kt
public data class ScaleGroup(
    val sections: List<ChordProBlock.Section>,
    val rootName: String,
    val scales: List<Scale>,
    /** The chords of [sections] the first of [scales] does not hold, each with what to play over it instead. */
    val exceptions: List<ScaleException> = emptyList(),
)

// ScaleException.kt (new file, KDoc'd as the contract)
public data class ScaleException(
    /** The chord as the song first writes it, in the standard notation (`E7`, `D/F#`). */
    val chordName: String,
    /** The note [scales] are played on, in the standard notation. */
    val rootName: String,
    val scales: List<Scale>,
)
```

### Fits, in `SongScaleGroups` (private)

`Fit` gains `level: Level`, `home: Scale` and `exceptions`. `home` is the scale that decided the fit — the key's,
the home or mode of plan 01's steps 2–5, natural minor where re-expressed (1, 2), the LOOSE winner (3) — **not** the
first of its scales: a dominant blues lists the minor pentatonic first, which holds none of its chords, and a step-6
fallback can list an added pentatonic first (`Em F Em F` at HEAD order: minor_pentatonic, blues, phrygian). `enum class Level { TONAL, LOOSE,
PASSING }`. `fitOf(parts)` (no more `isStrict` argument) returns the first that applies, on plan 01's ranked roots:

1. **TONAL, strict**: plan 01's steps 2–6 for a fit whose suggestion `isFit`. If its home is `harmonic_minor` or
   `melodic_minor` and natural minor on the same root holds at least one of the chords, it is **re-expressed in the
   minor family**: home natural minor (scales `PlayableScales.over(root, chordsNaturalMinorHolds)`), the other chords
   exceptions (below). `Am Dm E7` then reads A natural minor, with harmonic minor over the E7, like C2.
2. **TONAL, minor family**: on the minor key's root (where the key is minor) else the top-ranked root, every chord is
   held by natural, harmonic or melodic minor on it → home natural minor as in 1.
3. **LOOSE**: on the key's root (where there is a key), then the top-ranked root, then the first chord's root, the
   first of these that passes — never further down the ranking: searching every root reads `C A7 Dm G7` as D minor
   (natural, harmonic and melodic minor on D hold all four chords). The first chord's root is needed because the I → IV
   cadence can put the IV on top: `C F G E` ranks F 3, C 2 and F major holds only 2 of 4, so without it the section had
   no group. For each of major, natural minor (counting a chord the minor family holds as held), Dorian and Mixolydian,
   count the chord *occurrences* it holds; the most wins (ties in that order). Accepted if it holds at least **two
   thirds** of the occurrences (`held * 3 >= total * 2`). Scales: that home first, then `PlayableScales.over(root,
   heldChords)` without it, where for a natural-minor home `heldChords` are the chords natural minor itself holds (as in
   1), so `Am Dm Bb E7` offers the minor pentatonic and the blues instead of harmonic minor second.
4. **PASSING**: today's non-strict answer (an eight-note scale), on the top-ranked root only (plan 01 step 6).

**Exceptions** (built for a re-expressed 1, for 2 and for 3; **never for any other strict fit**, whose home holds every
chord by definition — and a dominant blues, whose home is no holding scale, must not list its I7, IV7 and V7): every
distinct chord (compared as `Chord(root, intervals)`, the bass left out) the home scale on the root does not hold, ordered
by occurrences, then by first played; `chordName` the first spelling written.
- With a natural-minor home, a chord that harmonic minor, Dorian or melodic minor on the home root holds gets those,
  `rootName` the home root, ordered by how many of their notes are outside natural minor (harmonic and Dorian one,
  melodic two; ties harmonic first): "Over E7: A harmonic minor", "Over D: A Dorian" (the House of the Rising Sun's
  IV, Hotel California's E in B minor — the raised sixth is what players use there; melodic minor's raised seventh
  clashes with the next chord). Dorian is offered here only: counting a chord Dorian holds as held by the minor family
  (level 2, 3) reads `C A7 Dm G7` and Yesterday as D minor.
- Any other chord: `rootName` its own root as written (`rootOf`), scales `PlayableScales.over(chord)` limited to those
  of **seven notes that hold the chord** (not the pentatonics, hexatonics and blues `PlayableScales` adds: a lone `A7`
  is a dominant blues, so its list holds A minor pentatonic and blues, whose C clashes with the A7's C♯ inside a C-major
  song and which, being five notes, are always "closest" to the home), stably sorted by how many of the scale's notes
  on that root are outside the home scale's notes (fewest first; ties keep `PlayableScales` order, so plan 04's
  quality order breaks ties): over `A7` in C major that is A mixolydian_flat_6 (one note outside, the C♯), then
  Mixolydian, then Phrygian dominant.
- A chord nothing holds gets no entry.

### Grouping (`of`)

The `isStrict` partition goes; levels replace it.
- One group for the whole song where `fitOf(parts)` is TONAL (C2 becomes one group).
- Each part: its own fit (`fitOf(listOf(part))`, none → left out as today). It joins the first candidate group (same
  order as today: own kind, the last group, any) whose combined fit `fitOf(group.parts + part)` passes:
  - both TONAL → the combined must be TONAL (keeps Hey Jude: verse F major, chorus F Mixolydian, since together they
    are only LOOSE);
  - either PASSING → both PASSING and any combined fit (today's non-strict partition);
  - otherwise (a LOOSE one with a TONAL or LOOSE one) → the combined is TONAL or LOOSE **and** has the same root and
    the same home family (major / minor family / Dorian / Mixolydian / other by id) as both the group's and the part's
    own fit — a C-major verse with an `A7` joins its C-major chorus, a D-major bridge does not.

### Measured (prototype of 01 + these rules, throwaway probe)

| Chords | Root, home | Exceptions |
|---|---|---|
| `C A7 Dm G7` | C major | A7: A mixolydian_flat_6, mixolydian, phrygian_dominant |
| `C C#dim Dm G7` | C major | C#dim: C# altered, locrian, ultralocrian |
| `Am Dm Bb E7` | A natural minor | Bb: Bb lydian, major, lydian_sharp_2; E7: A harmonic_minor, melodic_minor |
| `Bm F# A E G D Em F#` | B natural minor | F#: B harmonic_minor, melodic_minor; E: B dorian, melodic_minor |
| `C Ab Bb C` | C mixolydian | Ab: Ab lydian, major, lydian_sharp_2 |
| `C7 F7 C7 Dm7 G7 C7` | C mixolydian | F7: F mixolydian, …; G7: G mixolydian, … |
| `Am C D F Am C E` | A natural minor | D: A dorian, melodic_minor; E: A harmonic_minor, melodic_minor |
| `F Em7 A7 Dm Dm/C Bb C7 F` | F major | Em7: E phrygian, …; A7: A phrygian_dominant, … |
| `C Cmaj7 C7 F D7 G Am` | C major | C7: C mixolydian, …; D7: D mixolydian, … |
| rhythm changes A `Bb G7 Cm7 F7 Bb G7 Cm7 F7 Bb Bb7 Eb Ebm Bb F7 Bb` | Bb major | G7, Bb7, Ebm |
| `F G C E7` / with `{key: Am}` | C major (E7: E phrygian_dominant, …) / A natural minor (E7: A harmonic_minor, …) | |
| `Am G F E`; `Am Dm G C F Bm7b5 E7 Am`; `Am7 Dm7 Am7 E7` | A natural minor | E or E7: A harmonic_minor, melodic_minor |
| `Cm7 F7 Bbmaj7 Ebmaj7 Am7b5 D7 Gm` | G natural minor | D7: G harmonic_minor, melodic_minor |
| `Em D C B7` | E natural minor | B7: E harmonic_minor, melodic_minor |
| `C E7 Am F G` | C major | E7: E phrygian_dominant, … |
| `C Bb F G` | C major | Bb: Bb lydian, … |
| Stairway intro `{key: Am}` | A natural minor | D/F#: A dorian, melodic_minor |
| `C F G E` | C major (LOOSE on the first chord's root) | E: E phrygian_dominant, mixolydian_flat_6, double_harmonic |
| twelve-bar `E7 A7 … B7`, `C7 F7 … G7` | E / C, minor_pentatonic first (strict, dominant blues) | none |
| C2 (`{key: Am}`, two sections) | one group, A natural minor | E: A harmonic_minor, melodic_minor |

The natural-minor home's own list carries the minor pentatonic and the blues, which fixes the minor blues (B2).
Challenger's re-measure (a probe of 01 as amended + these rules as amended) reproduces every row above; with the rules
as first written, the twelve-bar rows listed all three chords as exceptions, `C A7 Dm G7`'s A7 read "A minor_pentatonic,
blues, mixolydian_flat_6", and `C F G E` had no group. Known limits, accepted: a jazz blues `C7 F7 C7 Dm7 G7 C7` is C
Mixolydian with F7 and G7 named and **no blues scale** until plan 03 adds the minor pentatonic and blues to a Mixolydian
home; a rock riff of major chords on the minor pentatonic's roots (`E G A E`, `C Eb F C`) reads E/C major with the ♭III
named, not the minor pentatonic a rock player reaches for.
Below the floor, accepted as no answer: Creep `G B C Cm` (C ranks first, its best home holds 2 of 4) and a full All of
Me chart (C major holds 17 of 31). **Decision:** a floor of one half would give both (C major with B and Cm, C major
with five exceptions); recommended to keep two thirds.

### Presentation

- `SongScaleGroup` gains `exceptions: List<SongScaleException>`;
  `internal data class SongScaleException(val chordName: String, val rootName: String, val scales: List<Scale>)`
  (`@Immutable`), `chordName` as shown: `songScaleGroupsOf` takes the `notation` (`prepareSongLyrics` passes its own)
  and maps it with `ChordProNotation.withAccidentalSigns(ChordProNotation.shownNames(song, notation)[name] ?: name)`.
  At most `MAX_GROUP_EXCEPTIONS = 3` exceptions a group, `MAX_EXCEPTION_SCALES = 3` chips each (next to
  `MAX_GROUP_SCALES`).
- `RenderSection.Scales` flattens groups into rows of chips: `rows: List<ScaleChipBlock>` with
  `ScaleChipBlock(groupIndex: Int, exception: SongScaleException?)` (a group's own row has none), and `rowStarts`
  replaces `groupStarts`, still one slot per chip:
  `rows.runningFold(0) { start, row -> start + row.scalesOf(groups).size.coerceAtLeast(1) }`. `itemCount` and
  `chunkStarts` follow.
- `ScaleChipRows` takes `rootName` and `scales` instead of `group`.
- `SongScalesSection` loops over `rows`: a group's own row titled as today (`group.label ?: other` when grouped, at its
  first slot); an exception row titled `stringResource(Res.string.song_details_scales_over, exception.chordName)` at its
  first slot, in every song (also a one-group one); `trailingGap` `CHIP_GAP` after a row followed by its group's next
  row, 16.dp after a group's last row, 0 after the last.
- Strings, after `song_details_scales_other`:
  `values/strings.xml`: `<!-- Over the scales for one chord the scales above it leave out, e.g. "Over E7". -->`
  `<string name="song_details_scales_over">Over %1$s</string>`;
  `values-hu/strings.xml`: `<string name="song_details_scales_over">%1$s akkordhoz</string>`.
- `songView/CLAUDE.md`, "The Scales section": "each group has one slot per chip" → "each row of chips (a group's, then
  one per chord it leaves out) has one slot per chip (`RenderSection.Scales.rowStarts`)"; "At most five chips a group"
  → "At most five chips a group, and three chords it leaves out, three chips each".
- Scales `CLAUDE.md`: replace "a section no scale holds is left out" with the levels in two sentences: a home scale
  holding two thirds of the chords, the rest named as exceptions; the minor family as one home.

**Decision (from the challenge, awaiting the user):** an exception's scales are the seven-note scales holding the chord,
ordered by how few notes fall outside the home scale, which puts an obscure name first where a common one would do: over
`Fm` in C (`C Am F Fm C`) the first chip reads "F Lydian ♯2". Recommended: among the holders, the ones in
`Scales.common` first (F Dorian, F natural minor, F melodic minor…), then the rest by closeness to home; or keep pure
closeness.

## Tests

`ScaleExceptionsTest` (chordpro; helper `fun group(chords: String, key: String? = null): ScaleGroup` building a one-verse
song and asserting a single group):

- `a secondary dominant is an exception to the major key` — `C A7 Dm G7`: root C, first scale major, exceptions
  `[A7]` on A with mixolydian_flat_6 first.
- `a major fifth in a minor key is the harmonic minor of the key` — `Am Dm E7`, `Am G F E`, `Am7 Dm7 Am7 E7`,
  `Em D C B7`: root the minor tonic, first scale natural_minor, the one exception on the same root with harmonic_minor
  first; and the minor blues offers minor_pentatonic and blues.
- `the minor family holds a minor song with a raised sixth` — House of the Rising Sun (D: A dorian first) and Hotel
  California (E: B dorian first; F#: B harmonic_minor first), as the table above.
- `a borrowed chord is an exception on its own root` — `C Ab Bb C`: C mixolydian, Ab exception on Ab with lydian first;
  `Am Dm Bb E7`: two exceptions, Bb then E7 (play order on equal counts).
- `a jazz blues is Mixolydian with the IV and V named` — as the table.
- `a song most of whose chords no scale holds has no group` — Creep `G B C Cm` gives no groups.
- `a dominant blues has no exceptions` — the twelve-bar `E7 A7 E7 E7 A7 A7 E7 E7 B7 A7 E7 B7`: root E, first scale
  minor_pentatonic, `exceptions` empty; a song one scale fits (`C Am F G`) has none either.
- `a diatonic song with one outside chord keeps its group` — `C F G E`: root C, major, exception `E` with
  phrygian_dominant first.
- `an exception offers only seven-note scales holding the chord` — `C A7 Dm G7`'s A7 exception has no
  minor_pentatonic or blues, and every scale of every exception in this file holds its chord.
- `an exception names the chord as the song first writes it` — Stairway's `D/F#` exception reads `D/F#`.

`SongScaleGroupsTest`:
- `a verse and a chorus in one minor key are one group` — C2: one group on A, natural_minor first, exception E with
  harmonic_minor.
- Keep `a chorus leaving the verse's scale gets the scales that fit it` (Hey Jude's shape) green: still two groups.
- `a verse with a secondary dominant joins its key's chorus` — `{key: C}` verse `C A7 Dm G7`, chorus `F G C`: one group.
- Update plan 01's `a minor key is read from its m` (now natural_minor first with an E exception) and `an eight-note
  answer is never taken on another root` (Stairway: one group on A).

Presentation:
- `SongScaleGroupsTest`: `an exception is named in the reader's notation, capped` — `songScaleGroupsOf` for `C Bb F G`
  in `ChordNotation.GERMAN` names its exception `B` (German's B♭), and a group with four outside chords keeps three.
- `SongScaleGroupsTest.kt:81` builds `SongScaleGroup(label = null, rootName = "C", scales = emptyList())`: add
  `exceptions = emptyList()` there (the presentation class takes no default, as a model built in one place should
  not), and to `RenderSectionTest.kt:39-40`.
- `RenderSectionTest`: `a scales section has a slot for every chip of every row` — one group of 3 chips with an
  exception of 2: `rowStarts` is `[0, 3, 5]`, `itemCount` 5.
