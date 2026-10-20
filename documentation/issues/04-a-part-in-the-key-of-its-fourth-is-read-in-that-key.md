<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# A part sitting in another key, the song's tonic heard as its dominant, is read in that key, not as the key's mode

**Kind:** design  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroups.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroupsTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`

HEAD at the time of writing: `f70a58f89`. Lane G, plan 4 of 9; assumes plans 01–03 have landed.

## Problem

`SongScaleGroups.keyFitOf` reads a part as the key's Mixolydian (Dorian for a minor key) whenever that mode holds every
chord, before anything else:

```kotlin
harmony.holding(root, if (key.isMinor) minorKeyScaleIds + DORIAN_ID else majorKeyScaleIds + MIXOLYDIAN_ID)?.let { home ->
    return tonalFitOn(harmony, root, home)?.let { saidAsNaturalMinor(harmony, it) }
}
```

A chorus that has moved to the IV is held whole by the key's Mixolydian (the IV's major scale and the key's Mixolydian
are the same notes), so it is announced on the wrong root. At HEAD the probe prints:

- `{key: C}`, verse `C G Am F C G F G`, chorus `F Bb C F F Bb C F` → `[HOME] [Verse] C: major, …` and
  `[Chorus] C: mixolydian, major_pentatonic, minor_pentatonic, blues (+3)`. A player wants F major and the F major
  pentatonic over that chorus; "C minor pentatonic" over it is wrong.
- `{key: G}`, verse `G C D G G C D G Em C D G`, chorus `C F G C C F G C Am F G C` → `[Chorus] G: mixolydian, …`,
  G Mixolydian **as the home** of a part that is plainly C major.
- `{key: A}{capo: 7}`, Here Comes the Sun with D shapes (verse `D D G A7 D D G A7`, chorus `D G A7 D D G A7 D`,
  bridge `F C G D A7 F C G D A7`) → `[HOME] [Verse, Chorus] A: mixolydian, major_pentatonic, minor_pentatonic, blues`
  and `[Bridge] A: minor_pentatonic, …`.
- `{key: C}`, verse `C Am F G ×2`, chorus `Eb Ab Bb Eb ×2` → the chorus is a rock riff on C (every chord on ♭III, ♭VI,
  ♭VII) and is shown only through `SongScaleSuggestions`' centre rule as `[Chorus] Eb: major_pentatonic, major`, a
  weight-one centre rather than the key it is.

What distinguishes these from a part the key should keep is not how many chords the key's scale holds (C major holds
six of the F chorus's eight, as it holds three of `G D Em C`'s four, which the test
`the key names the centre wherever its own scale holds half the section, the chords deciding otherwise` keeps in C with
`D` as the exception) but what the key's tonic chord does there: in `F Bb C F` every C is followed by F, the song's
tonic has become a dominant. In `G D Em C` the C closes the part and leads nowhere; in `C Bb F` (the test
`a chorus leaving the verse's scale gets the scales that fit it`, C Mixolydian) the part starts on C.

## Fix

Add to `SongScaleGroups` a step `otherKeyFitOf(harmony, key)`, tried in `keyFitOf` after the key's own scale(s) and
before its mode:

```kotlin
/**
 * The fit of a part that has left the key for another, read in that key ([Level.TONAL]): a major or minor key on the
 * part's best scored root holds every chord, that key is not the relative one (its notes are the key's, and such a
 * part stays in the key with its V as the exception), and the key's tonic chord, where the part plays it at all, is
 * every time followed by the new root's chord, which is a dominant's place, not a tonic's. A chorus on the IV is the
 * usual case: the key's Mixolydian holds it too, but a player wants the IV's major scale, and only such a part reads
 * that way; one that starts on the key's root (`C Bb F`), or returns to it, keeps the key's mode or home.
 */
private fun otherKeyFitOf(harmony: Harmony, key: SongKey): Fit? {
    val centre = harmony.ranked.first().takeIf { it.pitchClass != key.root.pitchClass } ?: return null
    val home = harmony.holding(centre, homeScaleIds) ?: return null
    val keyNotes = Scales.pitchClasses(if (key.isMinor) naturalMinor else major, key.root.pitchClass)
    val homeNotes = Scales.pitchClasses(if (home.id == MAJOR_ID) major else naturalMinor, centre.pitchClass)
    if (homeNotes == keyNotes) return null
    val chords = harmony.parts.flatMap { part -> part.chords.indices.map { part.chords[it] to part.chords[(it + 1) % part.chords.size] } }
    if (chords.any { (chord, next) -> chord.root == key.root.pitchClass && next.root != centre.pitchClass }) return null
    return tonalFitOn(harmony, centre, home)?.let { saidAsNaturalMinor(harmony, it) }
}
```

(`Harmony` needs to keep its `parts` for the cyclic "next" per part, or expose a `nextOf(index)`; `major` is a new
`private val major = Scales.fromId(MAJOR_ID)!!`.) The relative check uses natural minor's notes for a harmonic minor
home, as `saidAsNaturalMinor` does, so `{key: C}` `Am Dm E7 Am` (A harmonic minor holds it) stays in C.

`keyFitOf` then reads, in order: the key's own scale holding every chord (major; natural or harmonic minor), the minor
family (`minorFamilyFitOn`), **`otherKeyFitOf`**, the key's mode holding every chord (Mixolydian; Dorian), the dominant
blues, the loose home. Today's first line, which tries the scale and the mode in one `holding` call, is split into two.
Update `keyFitOf`'s KDoc accordingly (its sentence "a mode with exceptions and no tonic chord is no reading of a key,
or capo shapes in C under `{key: D}` would read as D Mixolydian" stays).

`canJoin` needs no change: a C major verse (TONAL) and an F major chorus (TONAL) join only if the combination is
TONAL, and C major does not hold Bb, so they stay two groups and `SongScaleSuggestions` shows the chorus as another
key (`KEY_WEIGHT`, its own row with its full scale list).

### Expected probe output after the fix

- `{key: C}` verse `C G Am F C G F G`, chorus `F Bb C F ×2` → `[HOME] [Verse] C: major, …` and
  `[Chorus] F: major, major_pentatonic, major_hexatonic, bebop_dominant (+1)`.
- `{key: G}` verse/chorus above → `[HOME] [Verse] G: major, …` and `[Chorus] C: major, major_pentatonic, …`.
- Here Comes the Sun, `{key: A}{capo: 7}` → `[HOME] [Verse, Chorus] D: major, …` (both parts: every `A7` is followed
  by `D`) and `[Bridge] A: minor_pentatonic, blues, …` unchanged (F major holds neither `G` nor `A7`, so the bridge is
  no other key; it stays the rock riff on the key's root until plan 08 reads the capo and puts it on D).
- `{key: C}` chorus `Eb Ab Bb Eb ×2` → `[Chorus] Eb: major, major_pentatonic, major_hexatonic, bebop_dominant (+1)`,
  a key moment instead of the centre; the home row is unchanged.
- `{key: Am}` verse `Am Dm E7 Am ×2`, bridge `F G F G ×2` → unchanged (`[HOME] [Verse] A: natural_minor, …`, the
  bridge a centre `F: major_pentatonic, major`): F is the best root, F major does not hold `G`, F Lydian is no key.
- `{key: C}` verse `Am Dm E7 Am ×2`, chorus `C F G C ×2` → unchanged, one C major group with `over E7 → A
  harmonic_minor` and the verse a centre on A.
- `{key: A}` `D F G` alone → unchanged, one group (G ranks first by the `D → G` cadence, G major does not hold `F`).
- Corpus charts starting on the key's root (`Hey Jude`'s outro `F Eb Bb F`, `Gloria`, the "Mixolydian with its V"
  verse) are unchanged, since the key's root ranks first there and the step returns null.

## Tests

`SongScaleGroupsTest`:

- Change the second assertion of `the key names the centre wherever its own scale holds half the section, the chords
  deciding otherwise` from `listOf("C" to "major", "C" to "mixolydian")` to `listOf("C" to "major", "F" to "major")`
  for `song("C F G C", "F Bb C F", key = "C")`; its other assertions (`G D Em C` in C with `D` the exception, `E A B E`
  as E major, `C Am F G` under `{key: D}` as C major, `Am Dm Em` under `{key: Am}`) stay.
- `` `a part on the key's IV, the key's tonic heard as its dominant, is read in the IV's key` ``:
  `song("G C D G G C D G Em C D G", "C F G C C F G C Am F G C", key = "G")` is `listOf("G" to "major", "C" to "major")`;
  `song("D D G A7 D D G A7", "D G A7 D D G A7 D", key = "A")` is a single group `"D" to "major"`;
  `song("C Am F G C Am F G", "Eb Ab Bb Eb Eb Ab Bb Eb", key = "C")` is `listOf("C" to "major", "Eb" to "major")`.
- `` `a part that starts on the key's root or returns to it keeps the key's mode or home` ``:
  `song("C F G C", "C Bb F C", key = "C")` is `listOf("C" to "major", "C" to "mixolydian")`;
  `song("C F G C", "F Bb C G", key = "C")` is a single group `"C" to "major"` with exceptions `[Bb]` (Bb ranks first
  but Bb major holds no `G`; the C leads to G, not back to a new root; the chorus is then the key's loose home and
  joins the verse).
- `` `the relative minor stays in the key` ``: `song("Am Dm E7 Am Am Dm E7 Am", "C F G C C F G C", key = "C")` is a
  single group `"C" to "major"` with exceptions `[E7]`; `song("Am Dm E7 Am", "F G F G", key = "Am")` is a single group
  `"A" to "natural_minor"` (the bridge is held by A natural minor itself and joins the verse through the family).
- Keep: `a chorus leaving the verse's scale gets the scales that fit it` (`C Bb F` in C stays Mixolydian),
  `a mode needs the part to start on its root or the key to name it` (`D A E E` under `{key: E}` stays E Mixolydian:
  E ranks first), `a minor key is read from its m` (`Dm G Dm G` under D minor stays Dorian: D ranks first),
  `a guess on three chords is left out beside a better answer, and kept alone`, `a section replaying another's chords
  joins its group before one of its own kind` (the `Eb Bb Ab Eb` chorus under `{key: A}` reads Eb major either way),
  `a rock verse joins a riff of other chords on its root, and a chorus in another key stands apart`.

## CLAUDE.md

In the `SongScaleGroups` bullet of the scales `CLAUDE.md`, the sentence "**Then the key decides the root**: the key in
force (…), a scale of its third holding the part, a minor key's family, a dominant blues, then its own major or minor
family as a home with exceptions, its mode too where its tonic chord is played": rewrite the list as "the key's own
scale holding the part, a minor key's family, then another key on the part's best scored root holding it whole where
the key's tonic chord is only ever followed by that root's (a chorus on the IV is the IV's major, never the key's
Mixolydian; the relative key is the key's), its mode holding the part, a dominant blues, then its own major or minor
family as a home with exceptions, its mode too where its tonic chord is played and it holds two chords more".
