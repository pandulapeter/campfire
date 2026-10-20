<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# A secondary dominant targets only a note of the key, and a diminished chord is its target's harmonic minor or the diminished scale

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/ScaleExceptions.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/ScaleExceptionsTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`

Written at HEAD `0a0babf27`. Lane S; run after plan 06. Lane G's plan 01 counts secondary dominants and leading-tone
diminished chords as the key's own for the home's floor and cap; what they are *named* stays here, and this plan does
not depend on it.

## Problem

`ScaleExceptions.targetMinorOf` takes any major triad or dominant seventh for a secondary dominant of the chord a
fourth up:

```kotlin
    private fun targetMinorOf(chord: Chord) =
        (chord.root + FOURTH).mod(12).takeIf { (chord.isMajorOrDominant || chord.isDominantSeventh) && holdsChord(harmonicMinor, it, chord) }
```

and `answerOver` takes the closest of the target, the chord's root, and the home's root, a tie going to the target:

```kotlin
        val target = targetMinorOf(chord)?.let { Answer(ScaleSpelling.spell(chordRootName, letterStep = FOURTH_LETTER_STEP, pitchClass = it), it, listOf(harmonicMinor)) }
        val onChordRoot = PlayableScales.over(chord)
            .filter { it.intervals.size == HOME_SCALE_SIZE && holdsChord(it, chord.root, chord) }
            .sortedWith(compareBy({ it !in Scales.common }, { distance(it, chord.root) }))
        …
        return listOfNotNull(target, Answer(chordRootName, chord.root, onChordRoot), homeRoot.copy(scales = onHomeRoot))
            .filter { it.scales.isNotEmpty() }
            .minByOrNull { distance(it.scales.first(), it.root) }
```

So a tritone substitution, a Neapolitan, and the ♭III7/♭VI7 colour chords of a major song are sent to harmonic minors
on roots the key does not have, and a ragtime II7 drags its VI7 into a D minor nobody plays (probe at HEAD, `{key: C}`):

```
## 12 Tritone substitution (Dm7 G7 Cmaj7 Dm7 Db7 Cmaj7)
      over Db7 → Gb harmonic_minor
## 12 Neapolitan in major (C F Db G C)
      over Db → Gb harmonic_minor
## 12 bIII7 and bVI7 as colour (Cmaj7 Eb7 Ab7 G7)
      over Eb7 → Ab harmonic_minor
      over Ab7 → Db harmonic_minor
## 12 Ragtime VI7 II7 V7 (C A7 D7 G7 C)
      over A7 → D harmonic_minor
      over D7 → D mixolydian, lydian_dominant, mixolydian_flat_6, hungarian_major, phrygian_dominant, lydian_minor
```

Players: D♭ Mixolydian (or Lydian dominant) over the D♭7, D♭ major over the Neapolitan, E♭ and A♭ Mixolydian over the
colour chords, A Mixolydian over an A7 that goes to D7 (it goes to a minor D only in `C A7 Dm G7`, which is settled as
D harmonic minor and must stay so, as must `C F G E → A harmonic minor` and `C E7 Am F G → A harmonic minor`).

Diminished chords are answered with the rarity closest to the key on the chord's own root, since the textbook first
choice (`ChordScaleChoices`: `rule("3 6 9", "10", "diminished")`, `rule("3 6", "7 10", "diminished", "locrian")`) has
eight notes and `onChordRoot` keeps seven, and the harmonic minor a semitone up, the key the leading-tone chord belongs
to, is never a candidate:

```
## 12 Diminished passing chords (C C#dim Dm D#dim Em F G C)
      over C#dim → C# altered, locrian, ultralocrian, …
      over D#dim → D# ultralocrian, lydian_sharp_2, altered, …
## 12 Dim passing to ii (C C#dim7 Dm7 G7)
      over C#dim7 → C# ultralocrian, locrian_natural_6, …
## 12 Dim7 to V (C F F#dim7 G7 C)
      over F#dim7 → F# locrian_natural_6, ultralocrian, …
## 12 Hungarian folk (Am E Am, Dm Am E Am) with a raised fourth   {key: Am}, D#dim before E
      over D#dim → D# ultralocrian, lydian_sharp_2, altered, …
## 12 Ebdim passing in a major key (C Ebdim Dm7 G7)
      over Ebdim → Eb ultralocrian, lydian_sharp_2, altered, …
```

A player names C♯dim→Dm "D harmonic minor", F♯dim7→G7 "G harmonic minor", and a passing E♭dim that goes down to Dm7
"the E♭ diminished scale"; "C♯ altered" and "D♯ ultralocrian" are names nobody reaches for over a passing chord.

## Fix

All in `ScaleExceptions`; `of` passes `chords` (the group's chords in play order) into `answerOver`, which needs them
for (b) and (c).

**(a) A target counts only where its root is a note of the home scale on the home root.** `homeNotes` is already
computed in `answerOver`; build the target as
`targetOf(chord, chords, homeNotes)?.let { … }` and require `it in homeNotes`. In C: A7→D ✓, E7→A ✓, D♭7→G♭ ✗,
D♭→G♭ ✗, E♭7→A♭ ✗, A♭7→D♭ ✗, B♭7 (backdoor)→E♭ ✗ (stays B♭ Mixolydian, as today). In a minor home `homeNotes` is the
natural minor's (the minor-family answer is taken before this path), so `Dm E7 A7 Dm` keeps E7→A harmonic minor and
`Am B7 E7 Am` keeps B7→E harmonic minor.

**(b) On a distance tie between a secondary dominant's target and the chord's root, the target wins only where the
part plays a chord on the target root with a minor third.** A7 in C: D harmonic minor and A Mixolydian are both two
notes from C major (B♭ C♯; C♯ F♯). With a Dm or Dm7 in `chords` (`C A7 Dm G7`, All of Me, Fly Me to the Moon,
`C A7b9 Dm7 G7 C`, whose A Phrygian dominant also ties at two) the target stays; in `C A7 D7 G7 C` it goes to
A Mixolydian. Keep `minByOrNull { distance(...) }` and resolve the tie before it: where the chord is a secondary
dominant (not diminished), the target's distance equals the chord-root answer's, and
`chords.none { it.root == target && MINOR_THIRD in it.intervals.map { i -> i.mod(12) } }`, drop the target
(`MINOR_THIRD = 3`, a new constant; `ChordNotes.kt` keeps its own private one). A diminished chord's target is already
confirmed by the chord that follows it (see (c)), so its tie goes to the target as today (`F#dim7` before `G7` in C:
G harmonic minor and F♯ diminished are both three notes away). Distances that differ keep today's order: `C F G E`
(A harmonic minor one away, E Mixolydian three), Creep's `B → E harmonic minor` (one against three), Hey Jude's
`F7 → F Mixolydian` (one against B♭ harmonic minor's three).

**(c) A diminished chord has a target a semitone up, and the diminished scale as its own-root answer.** The predicate
stays private in `ScaleExceptions` (`ChordNotes.kt` is read by lane G's files too):

```kotlin
    /** Whether the chord is a diminished triad or a diminished seventh, the leading-tone chord of the key a semitone up. */
    private val Chord.isDiminished get() = intervals.map { it.mod(12) }.toSet().let { MINOR_THIRD in it && FLAT_FIFTH in it && it.all { interval -> interval in diminishedSeventh } }
```

with `FLAT_FIFTH = 6` and `diminishedSeventh = setOf(0, MINOR_THIRD, FLAT_FIFTH, 9)`. Replace `targetMinorOf` with:

```kotlin
    /**
     * The root of the harmonic minor [chord] leads into: for a secondary dominant (a major triad or a dominant seventh,
     * with the flat ninth that is that harmonic minor's own, `A7b9` into Dm; a ninth or a thirteenth is not in it) the
     * chord a fourth up; for a diminished triad or seventh, the leading-tone chord, the chord a semitone up, where a
     * play of it in [chords] is followed by a chord on that root, the list read as a loop (a `C#dim` before a `Dm`; an
     * `Ebdim` falling to a `Dm7` is a passing chord, played over its own diminished scale). Null where the harmonic
     * minor there does not hold the chord.
     */
    private fun targetOf(chord: Chord, chords: List<Chord>): Int? {
        val target = when {
            chord.isMajorOrDominant || chord.isDominantSeventh -> (chord.root + FOURTH).mod(12)
            chord.isDiminished -> (chord.root + 1).mod(12).takeIf { target ->
                chords.indices.any { chords[it] == chord && chords[(it + 1) % chords.size].root == target }
            }
            else -> null
        } ?: return null
        return target.takeIf { holdsChord(harmonicMinor, it, chord) }
    }
```

`chords` are the group's parts' chords concatenated, so the loop runs from the last part's last chord to the first
part's first; say so in the KDoc. The target's spelling: a fourth up stays `ScaleSpelling.spell(chordRootName,
letterStep = FOURTH_LETTER_STEP, pitchClass = it)`; a semitone up is `letterStep = 1` (`C#`→`D`, `D#`→`E`, `F#`→`G`,
`Eb`→`E`: `spell("Eb", 1, 4)` gives `E`).

The chord-root answer for a diminished chord: let the eight-note diminished scale through the size filter and put the
chord's textbook scales first, in their own order:

```kotlin
        val firsts = ChordScaleChoices.firstChoices(chord)
        val onChordRoot = PlayableScales.over(chord)
            .filter { holdsChord(it, chord.root, chord) && (it.intervals.size == HOME_SCALE_SIZE || chord.isDiminished && it.id in firsts) }
            .sortedWith(compareBy({ if (chord.isDiminished) firsts.indexOf(it.id).let { index -> if (index < 0) firsts.size else index } else 0 }, { it !in Scales.common }, { distance(it, chord.root) }))
```

(`ChordScaleChoices` is `internal` in the same package.) Every other chord keeps today's list; a diminished triad's is
`diminished, locrian, …`, a dim7's `diminished, …`. Ranking stays by distance: D harmonic minor is two notes from C
major, C♯ diminished four, so the target wins where it exists; where none does (the root a semitone up is no home note,
or no chord on it follows), the diminished scale is the answer. `SongScaleSuggestions.shownScale`
(`scales.firstOrNull { it in Scales.common } ?: scales.first()`) and the viewer's
`exception.scales.filter { it in Scales.common }.ifEmpty { exception.scales }.take(1)` already fall back to the first
scale, so an eight-note answer reaches the chip without a change there.

Update the KDoc of `of` (the paragraph "Otherwise the closest to the home's notes …") to say: the harmonic minor a
secondary dominant leads into, where that root is a note of the key (a tritone substitution's is not) and, on a tie
with the chord's own root, where the part plays that minor chord; the harmonic minor a diminished chord leads into a
semitone up, else its own diminished scale, the one answer of eight notes.

Expected chips after the fix (`{key: C}` unless said):

| Chart | Chip |
|---|---|
| `Dm7 G7 Cmaj7 Cmaj7 Dm7 Db7 Cmaj7 Cmaj7` | Over D♭7 → D♭ Mixolydian |
| `C F Db G C` | Over D♭ → D♭ major |
| `Cmaj7 Eb7 Ab7 G7` ×2 | Over E♭7 → E♭ Mixolydian; Over A♭7 → A♭ Mixolydian |
| `C A7 D7 G7 C` ×2 | Over A7 → A Mixolydian; Over D7 → D Mixolydian |
| `C A7 Dm G7` | Over A7 → D harmonic minor (unchanged) |
| `C E7 Am F G` | Over E7 → A harmonic minor (unchanged) |
| `C F G E` | Over E → A harmonic minor (unchanged) |
| `C C#dim Dm D#dim Em F G C` ×2 | Over C♯dim → D harmonic minor; Over D♯dim → E harmonic minor |
| `C C#dim7 Dm7 G7` ×2 | Over C♯dim7 → D harmonic minor |
| `C F F#dim7 G7 C` ×2 | Over F♯dim7 → G harmonic minor |
| `{key: Am}` `Am E Am Am Dm Am E Am Am E Am Am D#dim E Am Am` | Over E → A harmonic minor; Over D♯dim → E harmonic minor |
| `C Ebdim Dm7 G7` ×2 | Over E♭dim → E♭ diminished |
| `Cmaj7 Am7 Fm7 Bb7` (backdoor) | Over B♭7 → B♭ Mixolydian (unchanged) |

The probe prints the ids (`mixolydian`, `major`, `harmonic_minor`, `diminished`); verify each row with it.

## Tests

In `ScaleExceptionsTest` (its `group(chords, key)` helper and the `first` triple `chordName, rootName, scales.first().id`):

- `` `a secondary dominant targets only a note of the key` ``: `group("Dm7 G7 Cmaj7 Cmaj7 Dm7 Db7 Cmaj7 Cmaj7", key = "C")`
  → exceptions `listOf(Triple("Db7", "Db", "mixolydian"))`; `group("C F Db G C C F Db G C", key = "C")` →
  `listOf(Triple("Db", "Db", "major"))`; `group("Cmaj7 Eb7 Ab7 G7 Cmaj7 Eb7 Ab7 G7", key = "C")` → the chords `Eb7`,
  `Ab7` on `Eb`, `Ab`, both `mixolydian`.
- `` `a dominant that ties with its target leads there only where the part plays that minor chord` ``:
  `group("C A7 D7 G7 C C A7 D7 G7 C", key = "C")` → `A7` on `A` `mixolydian` and `D7` on `D` `mixolydian`;
  `group("C A7 Dm G7")` → `Triple("A7", "D", "harmonic_minor")` (the existing first test asserts this too and stays).
- `` `a diminished chord leading a semitone up is played as the harmonic minor there` ``:
  `group("C C#dim Dm D#dim Em F G C C C#dim Dm D#dim Em F G C", key = "C")` → exceptions
  `listOf(Triple("C#dim", "D", "harmonic_minor"), Triple("D#dim", "E", "harmonic_minor"))`;
  `group("C C#dim7 Dm7 G7 C C#dim7 Dm7 G7", key = "C")` → `Triple("C#dim7", "D", "harmonic_minor")`;
  `group("C F F#dim7 G7 C C F F#dim7 G7 C", key = "C")` → `Triple("F#dim7", "G", "harmonic_minor")`;
  `group("Am E Am Am Dm Am E Am Am E Am Am D#dim E Am Am", key = "Am")` → the `D#dim` on `E` `harmonic_minor`.
- `` `a passing diminished chord that leads nowhere is its own diminished scale` ``:
  `group("C Ebdim Dm7 G7 C Ebdim Dm7 G7", key = "C")` → `Triple("Ebdim", "Eb", "diminished")`, and that exception's
  `scales.first().intervals.size == 8`.
- Amend `an exception offers only seven-note scales holding the chord, the common ones first`: its loop includes
  `"C C#dim Dm G7"`, whose `C#dim` now answers D harmonic minor (seven notes, holds the chord: the assertion as written
  passes); add `"C Ebdim Dm7 G7"` to the loop with the size assertion relaxed to
  `it.intervals.size == 7 || it.id == "diminished"`, and rename the test
  `` `an exception offers seven-note scales holding the chord, the common ones first, or a passing chord's diminished scale` ``.
- Everything else in the file stays and must pass, `a borrowed chord is played over the home's own mode where that is
  closest` (G in A → A Mixolydian: C is no note of A major, so no target) included.

`CLAUDE.md` (`chordpro/scales`), the `ScaleException` bullet: replace "else the closest of a secondary dominant's
target harmonic minor, the chord's root, and the home's root, never a harmonic or melodic minor there, ties in that
order" with "else the closest of a secondary dominant's target harmonic minor (the target a note of the key, and on a
tie the minor chord played), a diminished chord's target a semitone up where a chord on it follows (else its own
diminished scale, the one answer of eight notes), the chord's root, and the home's root, never a harmonic or melodic
minor there, ties in that order".
