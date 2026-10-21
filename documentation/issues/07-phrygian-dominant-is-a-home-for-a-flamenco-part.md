<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Phrygian dominant is a home for a part with a major tonic and a major flat second

**Challenged:** amended — the `mixolydian` val (`looseHomes.last()`) is pinned by id, or appending the home makes it
Phrygian dominant (riff homes, plan 02's jazz-blues exceptions); Phrygian dominant counts only the chords it holds, never
plan 01's leaders (`{key: C}` `C F G C C F Db C` read as C Phrygian dominant, 8 against major's 7, its I–IV–V all
"leading" into its B♭ and C); plan 03's root ranking over `looseHomes` takes the same gate and count; the lane S answer
over a ♭III has no lane S plan carrying it.

**Kind:** design  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroups.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroupsTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`

HEAD at the time of writing: `f70a58f89`. Lane G, plan 7 of 9; assumes plans 01–05 have landed.

## Problem

The homes a part may be loosely fit to are four:

```kotlin
private val looseHomes = listOf("major", NATURAL_MINOR_ID, "dorian", "mixolydian").map(Scales::fromId).requireNoNulls()
```

and in `keyFitOf` a major key allows only major and, where the tonic is played, Mixolydian:

```kotlin
val homes = setOfNotNull(if (key.isMinor) NATURAL_MINOR_ID else MAJOR_ID, mode.takeIf { playsTonic })
```

A flamenco or Andalusian part on a major tonic with a major ♭II and a ♭III (`E F E F … G F E`) is held by no scale of
seven notes (E Phrygian dominant lacks the G chord's G, E Phrygian lacks the E chord's G♯) and no home above is close
to it, so it is a guess on E major with two exceptions, dropped beside anything else, or an eight-note scale. At HEAD:

- `{key: E}` falseta `E F E F E F G F E E F E`, copla `Am G F E Am G F E Am G F E` → `[HOME] [Copla] A: natural_minor,
  …` with `over E → A harmonic_minor`; the falseta is gone (E major THIN with `F` and `G` as exceptions, three distinct
  chords: a guess).
- The falseta alone with `{key: E}` → `[HOME] [Falseta] E: major, major_pentatonic, mixolydian, major_blues (+18)` with
  `over F → Bb harmonic_minor` and `over G → E dorian, natural_minor`: E major over a Phrygian cadence.
- Malagueña alone, no key, `E F E F E F G F E` → `[HOME] [Verse] E: flamenco`, the single chip an eight-note scale.
- `E F E F` alone already reads `E: phrygian_dominant, double_harmonic, flamenco` (Phrygian dominant holds it whole),
  which is right and shows the home the others want.

## Fix

In `SongScaleGroups`:

1. Add Phrygian dominant to `looseHomes` (after Mixolydian; the index order matters only to `looseFitOf`'s
   `counts.indexOf(counts.max())`, so a tie with major goes to major, as it should), with a constant
   `PHRYGIAN_DOMINANT_ID = "phrygian_dominant"`.
2. It is allowed only where the part plays a major chord on the ♭II of the root, the Andalusian mark, for the key and
   the guess alike. Put the rule into `looseCountsOn` itself, so that no caller can forget it:

   ```kotlin
   private fun looseCountsOn(harmony: Harmony, root: SpelledNote, isAllowed: (Scale) -> Boolean = { true }) = looseHomes.map { home ->
       val isOffered = home.id != PHRYGIAN_DOMINANT_ID || harmony.playsMajorFlatSecond(root)
       …
   }
   ```

   with, on `Harmony`, `fun playsMajorFlatSecond(root: SpelledNote) = chords.any { (it.root - root.pitchClass).mod(12) == 1 && MAJOR_THIRD in it.intervals }`.

   Phrygian dominant counts **only the chords it holds**: plan 01's `leadsInto` and `Harmony.isTwoOfLeaderInto` return
   false for it (also in plan 01's `outside`). A leader leads into a key; on Phrygian dominant's notes the key's own IV
   and V triads would "lead" (into its ♭VII and its root), so a major song with one ♭II would read as Phrygian dominant:
   `{key: C}` `C F G C C F Db C` (HEAD: `C: major` with `over Db`) would count 8 for C Phrygian dominant (`C` ×4 and
   `Db` held, `F` ×2 and `G` leading) against major's 7, and `{key: G}` `G C D G G C Ab G` likewise; held alone it is
   5 against 7 and major stays. The flamenco charts need no leader (falseta 11 of 12, copla 9 of 12, Malagueña 8 of 9).
3. Pin `private val mixolydian = looseHomes.last()` by id (`looseHomes.first { it.id == MIXOLYDIAN_ID }`): with the new
   home appended last it would become Phrygian dominant, and `looseFitOf`'s `riffHome` and plan 02's
   `harmony.exceptionsTo(mixolydian, root)` (the jazz blues's exceptions) read it.
4. Plan 03's `guessedFitOf` ranks its candidate roots by `looseHomes.maxOf { … holdsChord || leadsInto … }`, outside
   `looseCountsOn`: give it the same gate and count (one private helper, what `home` accounts for on `root`, that both
   `looseCountsOn` and plan 03's `scaleCountOn` call), so a root is never ranked by a home that `looseFitOf` then refuses
   it.
5. In `keyFitOf`, for a major key add the id to `homes` (the `isAllowed` lambda), so the key path offers it under the
   same condition: `setOfNotNull(MAJOR_ID, mode.takeIf { playsTonic }, PHRYGIAN_DOMINANT_ID)` (the count is -1 anyway
   without the ♭II). A minor key keeps natural minor and Dorian: a minor song's ♭II is its Phrygian, which
   `ScaleExceptions` already names (`a flat second in a minor song is its Phrygian`).
6. Plan 01's `MIN_MODE_LEAD` rule compares the key's scale with its *mode* only; Phrygian dominant competes on its count
   (the falseta: Phrygian dominant 11, major 6).

`familyOf` and `canJoin` need no change (Phrygian dominant is its own family); `Group.isGuess` neither. Plan 11 keeps
Phrygian dominant among the scales that may lead a part (a harmonic minor mode), so `E F E F` stays TONAL on it.

The row is built as every loose home's is, `listOf(home) + (PlayableScales.over(root, held, home) - home)` with plan
10's overload: E Phrygian dominant, double harmonic, flamenco (the held chords `E`, `F`, `Am` are inside no pentatonic).

The exception `G` reads, with `ScaleExceptions` as it stands, `over G → G mixolydian, …` (one note from the home, the
closest of the three answers it tries). What a flamenco player wants over the ♭III chord is **the home root's Phrygian**
(E Phrygian: the cadence's minor third, the other half of the eight-note flamenco mode), which `ScaleExceptions` offers
only for a natural-minor home (`if (home.id == NATURAL_MINOR_ID) minorScales.filter { … }`). Lane S's change, not this
lane's: extend that first branch to a Phrygian-dominant home with its own list, Phrygian first, then the double
harmonic; the test there is `` `a flat third chord over a Phrygian dominant home is the home's Phrygian` `` with
`group("E F E F E F G F E", key = "E")` whose `G` exception is `Triple("G", "E", "phrygian")`. Until it lands the probe
prints `over G → G mixolydian`. No lane S plan (06, 12, 13) carries this change: the orchestrator adds it to lane S
(after plan 12, which also edits `answerOver`) or leaves it for a later review.

### Expected probe output after the fix

- `{key: E}` falseta + copla → one row, `[HOME] [Falseta, Copla] E: phrygian_dominant, double_harmonic, flamenco` with
  `over G → G mixolydian, …` (then `over G → E phrygian` after lane S). The copla is LOOSE on E Phrygian dominant too
  (`Am`, `F`, `E` held, `G` the exception, 9 of 12), and the two join as one home family. Plan 04's other-key step
  returns null for the copla: A ranks first, but A natural minor does not hold the `E` chord and A harmonic minor does
  not hold `G`.
- Falseta alone with the key → `[HOME] [Falseta] E: phrygian_dominant, double_harmonic, flamenco` with the `G` moment.
- Malagueña alone, no key → the same (a guess on E Phrygian dominant, kept alone).
- `E F E F` alone → unchanged.
- `{key: C}` `C F G C7 …` and every chart without a major ♭II → unchanged (the home is never offered).
- `{key: C}` `C F G C C F Db C`, the same without a key, and `{key: G}` `G C D G G C Ab G` → unchanged, the key's major
  with the ♭II as the moment.
- The metal `E F G F` chart (`Em F` with a minor tonic) → unchanged, E Phrygian holds it.

## Tests

`SongScaleGroupsTest`:

- `` `a flamenco part with a major flat second is Phrygian dominant on its root, its flat third chord the exception` ``:
  `song("E F E F E F G F E E F E", "Am G F E Am G F E Am G F E", key = "E")` is a single group
  `"E" to "phrygian_dominant"` whose exception names are `[G]`; `song("E F E F E F G F E")` without a key is
  `"E" to "phrygian_dominant"`.
- `` `Phrygian dominant needs a major chord on the flat second` ``: `song("C Dbm C G C Dbm C G", key = "C")` is
  `"C" to "major"` (the `Dbm` an exception; the home is not offered), and `song("Am Bb Am Bb Am Bb Am Bb", key = "Am")`
  is `"A" to "natural_minor"` (a minor key keeps its Phrygian through the exception).
- `` `a major song with a passing flat second stays major` ``: `song("C F G C C F Db C", key = "C")`,
  `song("C F G C C F Db C")`, and `song("G C D G G C Ab G", key = "G")` are each a single group on the key's root,
  `"major"` first, exceptions `[Db]` (`[Ab]`) (pins that nothing leads into Phrygian dominant).
- Plan 02's jazz-blues test (exceptions `Bb7, D7, C7, Am7, Bdim7`) must stay green (pins the `mixolydian` val).
- Keep: `a Dorian vamp of triads is Dorian, not melodic minor`, `a mode is the answer only on a root that outweighs the
  home key`, and the `E F E F` reading (add `assertEquals(listOf("E" to "phrygian_dominant"), rootAndFirst(song("E F E F E F E F")))`
  to the first test above).

## CLAUDE.md

In the scales `CLAUDE.md`, the bullet "Where no scale holds a part, a **home** (major, the minor family as one, Dorian,
Mixolydian) holding half its chords answers": change the parenthesis to "(major, the minor family as one, Dorian,
Mixolydian, and Phrygian dominant where a major ♭II chord is played: the Andalusian cadence, under a major key or
guessed, counting only the chords it holds)". In the `ScaleExceptions` parenthesis of the same bullet, lane S adds the Phrygian-dominant home's answer.
