# Rank a chord sheet's scales by the chord's quality, and keep the blues off altered dominants

**Challenged:** amended — the alteration test reads only the chords on the I: applied to every chord, one `B7b9`
turnaround in an E blues stops the whole song being a dominant blues, and then (plans 01–02) no root has a fit the
blues' I7 and IV7 share, so the song loses its Scales group. Verified with a probe: `C9` [0,2,4,7,10], `C13`
[0,4,7,9,10], `C7#9` [0,3,4,7,10] stay blues; `C7b9`, `C7#5`, `C7b5`, `C7b13`, `C+7`, `C7#11` do not.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/PlayableScales.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/ChordScaleChoices.kt` (new),
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/PlayableScalesTest.kt`

## Problem

A chord sheet's chips are `PlayableScales.over(chord)` (`ScaleChipRow.kt`, `remember(chord.chord) {
PlayableScales.over(chord.chord) }`), ordered only by `Scales.common`, with the blues list added wherever
`isDominantBlues` says so:

```kotlin
// PlayableScales.kt:59
internal fun isDominantBlues(root: Int, chords: List<Chord>): Boolean {
    if (chords.isEmpty()) return false
    val isMajorOnBluesStep = chords.all { (it.root - root).mod(12) in bluesSteps && MAJOR_THIRD in it.intervals }
    return isMajorOnBluesStep && chords.any { (it.root - root).mod(12) == 0 && MINOR_SEVENTH in it.intervals }
}
```

**A5.** A lone altered dominant counts as a dominant blues, so its sheet opens with five scales that clash with its
alteration. Measured at HEAD (first chips):

- `G7#5` → minor_pentatonic, blues, major_pentatonic, major_blues, mixolydian, phrygian_dominant, mixolydian_flat_6,
  altered (8th), whole_tone (9th) — every one of the first five has a natural fifth against the ♯5.
- `G7b9` → minor_pentatonic, blues, major_pentatonic, major_blues, mixolydian, … dominant_diminished (8th).
- `C7b5` → major_pentatonic, minor_pentatonic, blues, major_blues, mixolydian (none holds the G♭), lydian_dominant 6th.
- `C7b13` → minor_pentatonic, blues, … the same.

**A6.** Odd first chips where `Scales.common` is the only order:

- `Cm7b5` → blues, locrian, locrian_natural_6, … (Locrian, Locrian ♮2 wanted)
- `Cdim` → blues, locrian, … ; `Cdim7` → locrian_natural_6, …, diminished 6th
- `C7sus4` → natural_minor, minor_pentatonic, blues, dorian, mixolydian (Mixolydian wanted)
- `Cm6` → melodic_minor, major_blues, dorian (Dorian wanted)
- `Cm7` → natural_minor, minor_pentatonic, blues, dorian (Dorian first is the textbook choice over a m7)
- `Cmaj7#11` → major_pentatonic, lydian (Lydian wanted; major does not hold the ♯11)

Plan 03 reorders `Scales.common` and the suggestion; that helps but cannot know a chord's quality.

## Fix

**1. Keep the blues off altered dominants.** In `isDominantBlues`, a chord **on the first step** counts only without a
♭9, ♭5 or ♯5/♭13: `chords.none { (it.root - root).mod(12) == 0 && it.intervals.any { interval -> interval.mod(12) in
ALTERATIONS } }` added to the conditions, with `ALTERATIONS = setOf(1, 6, 8)`. A chord sheet's chord is its own first
step, so `G7#5` and `G7b9` lose the blues; a song's altered IV or V (a `B7b9` turnaround in E) does not take the blues
from the whole song, since over the other eleven bars it is still the answer. A ♯9 (3) is allowed: it is the blues third. (`Chord.intervals` are semitones above the
root within the octave as `ChordProChords.parse` gives them: `C7b9` is `[0, 1, 4, 7, 10]`, `C7#5` `[0, 4, 8, 10]`,
`C7b13` `[0, 4, 7, 8, 10]`, `C7#9` `[0, 3, 4, 7, 10]`.) This changes a song's dominant blues only where its I is altered: a blues written with an `E7#9` still is one, one
whose I is an `E7b9` is not.

**2. A first choice per quality**, a new `internal object ChordScaleChoices` (its own file, KDoc'd: why a chord's
quality outranks the shortlist) with one function `firstChoices(chord: Chord): List<String>`, read on the chord's
interval set (`intervals.map { it.mod(12) }.toSet()`), first match wins:

| Intervals (has / lacks) | Quality | First choices (ids, in order) |
|---|---|---|
| 3, 6, 9 / 10 | dim7 | diminished |
| 3, 6, 10 | m7♭5 | locrian, locrian_natural_2 |
| 3, 6 / 7, 10 | dim | diminished, locrian |
| 4, 10, 1 | 7♭9 (also 7♭9♯5) | dominant_diminished, phrygian_dominant, altered |
| 4, 10, 8 | 7♯5, 7♭13 | whole_tone, altered, mixolydian_flat_6 |
| 4, 10, 6 / 7 | 7♭5 | whole_tone, lydian_dominant, altered |
| 5, 10 / 3, 4 | 7sus4 | mixolydian, dorian |
| 4, 10 | 7, 9, 13 | mixolydian |
| 3, 9 / 10 | m6 | dorian, melodic_minor |
| 3, 10 | m7, m9, m11 | dorian, natural_minor |
| 4, 11, 6 | maj7♯11 | lydian |
| 4, 11 | maj7, maj9 | major, lydian |

In `PlayableScales.over(chord: Chord)` only (a chord sheet; never `over(root, chords)`, which a song uses), move the
scales of the list whose id is among the chord's first choices to the front, in the table's order; the rest keep their
order. A first choice the list lacks (does not hold the chord) is skipped.

Plan 02's exceptions take `PlayableScales.over(chord)` and re-sort by closeness to the home scale, stably, so a
quality's first choice wins there only among equally close scales; that is intended (inside a key the closest scale is
the better answer).

Update the `PlayableScales` KDoc (the chord overload) and add to the scales `CLAUDE.md` one line: "A chord sheet puts
its quality's textbook scales first (`ChordScaleChoices`), and an altered dominant (♭9, ♭5, ♯5) is never a blues."

## Tests

`PlayableScalesTest` (`fun firstOf(name: String, count: Int) = PlayableScales.over(ChordProChords.parse(name)!!).take(count).map { it.id }`):

- `an altered dominant is not a blues` — `isDominantBlues` false for `G7#5`, `G7b9`, `C7b5`, `C7b13`; true for `C7#9`,
  `C9` and `C13`; `blues` not among the first five of `G7#5`.
- `an altered turnaround keeps a song's blues` — `isDominantBlues(4, E7 A7 E7 B7b9)` is true; `isDominantBlues(4, E7b9
  A7 B7)` is false.
- `a chord's sheet starts with its quality's textbook scales` — `Cm7b5` → [locrian, locrian_natural_2]; `Cdim7` →
  [diminished]; `C7sus4` → [mixolydian, dorian]; `Cm6` → [dorian, melodic_minor]; `Cm7` → [dorian, natural_minor];
  `G7#5` → [whole_tone, altered]; `G7b9` → [dominant_diminished, phrygian_dominant]; `Cmaj7#11` → [lydian]; `Cmaj7` →
  [major, lydian]; `C7` → [mixolydian].
- `a first choice the chord's notes rule out is skipped` — `C7b13` starts with mixolydian_flat_6 (whole_tone and
  altered do not hold its natural fifth).
- Keep `a dominant seventh chord's sheet offers the blues after Mixolydian` and `every suggestion is a scale on the root,
  never the chromatic one` green.
