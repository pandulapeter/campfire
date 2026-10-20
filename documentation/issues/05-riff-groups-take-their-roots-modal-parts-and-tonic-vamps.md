<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# A rock riff's group takes the parts in its root's modes and the key's guesses on it, and a vamp on one root joins whatever sits on that root

**Kind:** design  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroups.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroupsTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`

HEAD at the time of writing: `f70a58f89`. Lane G, plan 5 of 9; assumes plans 01–04 have landed.

## Problem

`SongScaleGroups.canJoin` lets a rock-riff group take only another riff on its root, or a part of the same home family
and the same riff-ness:

```kotlin
// Riffs on one root are played over its minor pentatonic whatever home their chords lean to.
listOf(group, part, combined).all { it.isRockRiff } -> listOf(group, part).all { it.root.pitchClass == combined.root.pitchClass }
else -> combined.level != Level.PASSING && listOf(group, part).all {
    it.root.pitchClass == combined.root.pitchClass && familyOf(it.home) == familyOf(combined.home) && it.isRockRiff == combined.isRockRiff
}
```

and the combined fit is `fitOf(group.parts + part)`, which for a riff plus a verse of the root's mode is usually no
riff at all (the ♭III share drops under one in eight), so the join is refused. Smoke on the Water, no key, Riff
`G5 Bb5 C5 G5 Bb5 Db5 C5`, Verse `G5 F5 G5 G5 F5 G5 C G5 C G5`, Chorus `C Ab G5 C Ab G5`, prints three rows at HEAD:

```
[Riff] G: minor_pentatonic, blues, natural_minor, dorian (+6)
[Verse] G: mixolydian, dorian, minor_pentatonic, blues (+4)
[Chorus] C: minor_pentatonic, blues, mixolydian, major (+11)
```

No row is the home (none plays half the chords), so a player gets three rows for one song in G minor pentatonic. With
`{key: Gm}` the verse reads G Dorian (TONAL, by the key) and the chorus, a loose G natural minor with `C` as the
exception, is a guess (three distinct chords) and is dropped beside the other groups:

```
[HOME] [Verse] G: dorian, minor_pentatonic, blues, mixolydian (+4)
[Riff] G: minor_pentatonic, blues, natural_minor, dorian (+6)
```

A second shape: a sus intro on the tonic before a riff, no key, Intro `Asus4 A Asus4 A`, Verse
`A5 A5 C5 D5 A5 A5 C5 D5`, Chorus `F G A A F G A A`. The intro is held by A major (TONAL), the verse and chorus are a
riff on A, and the intro cannot join a riff, so it becomes an "A major" row of its own, shown as another key:

```
[HOME] [Verse, Chorus] A: minor_pentatonic, blues, mixolydian, major_pentatonic (+4)
[Intro] A: major, major_pentatonic, mixolydian, phrygian_dominant (+8)
```

A vamp on one root says nothing about the mode; it should go with whatever the song puts on that root.

## Fix

In `SongScaleGroups.playedGroupsOf`, before the combined fit is tried, two kinds of join that keep one side's fit:

```kotlin
val joined = candidates.firstNotNullOfOrNull { group ->
    when {
        group.fit.isRockRiff && group.fit.absorbs(own, part) -> group to group.fit
        group.isTonicVamp(own.root) || part.isTonicVamp(group.fit.root) ->
            fitOf(group.parts + part)?.takeIf { it.level != Level.PASSING && it.root.pitchClass == group.fit.root.pitchClass }?.let { group to it }
        else -> fitOf(group.parts + part)?.takeIf { canJoin(group.fit, own, it) }?.let { group to it }
    }
}
```

**A riff absorbs** (a private `Fit.absorbs(part: Fit, songPart: SongPart)` on the riff's fit, documented with the why):
a part on the riff's root that is

- (i) TONAL with a home of natural minor, Dorian, or Mixolydian: the minor pentatonic's own modes (`G5 F5 G5 … C G5`,
  TONAL G Mixolydian, or G Dorian under the key). Never major: a riff `E5 G5 A5` and a chorus `E A B E` in E major are
  two sounds, and the minor pentatonic's ♭3 against the chorus's G♯ is not the style;
- (ii) LOOSE or THIN and no guess (`fit.exceptions.isNotEmpty() && distinct chords <= MAX_GUESSED_CHORDS` is the guess
  rule of `Group.isGuess`; share it through a small helper on the chords), whatever its home;
- (iii) a guess, only where the key in force at the part (`songPart.key`) names the riff's root and every chord of the
  part sits on a riff step (`riffSteps` of `PlayableScales.isRockRiff`: I, ♭III, IV, V, ♭VI, ♭VII, and a thirdless
  ♭V) or on the ♭II (metal's Phrygian chord). The key's word is what makes three chords on the riff's steps the riff's
  own rather than a chorus in another key.

The group keeps its own fit (the riff's scales lead, no exceptions, as a riff has none), since `fitOf` on the
combination would lose the riff's lead. `PlayableScales.riffSteps` becomes `internal` for (iii), or `PlayableScales`
gains `internal fun isOnRiffSteps(root: Int, chord: Chord)`.

**A tonic vamp** is a group or a part all of whose chords are on one root (`chords.all { it.root == root.pitchClass }`,
on `Group`/`SongPart` through a small `isTonicVamp(root)` helper). It joins across the usual rules, taking the combined
fit as long as it is on that root and not PASSING: `Asus4 A` before a riff `A5 A5 C5 D5` makes a riff on A of the pair
(the combination is thirdless by more than half and on riff steps, so `powerRiffFitOf` answers); `E9 E9` before
`E A B` makes E major with `E9` as the exception. The vamp defers to the other side; a vamp alone is still answered by
the chord's sheet (`oneChordFitOf`).

Update the KDoc of `canJoin` ("a rock riff, led by the minor pentatonic rather than its home, joins only another" is
no longer true) and the class KDoc's grouping paragraph.

### Expected probe output after the fix

- Smoke on the Water, no key → `[HOME] [Riff, Verse] G: minor_pentatonic, blues, natural_minor, dorian (+6)` (the
  verse is TONAL G Mixolydian on the riff's root, (i)) and `[Chorus] C: minor_pentatonic, blues, mixolydian, major
  (+11)` as another key (a riff on C: nothing in (i)–(iii) takes a part on another root).
- Smoke on the Water, `{key: Gm}` → one row, `[HOME] [Riff, Verse, Chorus] G: minor_pentatonic, blues, natural_minor,
  dorian (+6)`: the verse by (i) (TONAL G Dorian), the chorus by (iii) (a guess on G, `C` on the IV, `Ab` the ♭II,
  `G5` the I, the key naming G).
- Sus intro, no key → `[HOME] [Intro, Verse, Chorus] A: minor_pentatonic, blues, mixolydian, …`: the intro's group,
  a tonic vamp, takes the verse and the riff fit of the pair; the chorus (a riff on A) then joins as today.
- `{key: Gm}` chorus `C Ab G5 G5 C Ab G5 G5` alone (plan 10's chart) → unchanged: a lone guess is kept.
- The corpus's "Riff on power chords, chorus of triads (E5 G5 A5, C D E)" and the `SongScaleSuggestionsTest` riff
  songs → unchanged (riff-with-riff joins go through the old branch).

## Tests

`SongScaleGroupsTest`:

- `` `a riff takes the verse in its root's mode and, where the key names its root, the chorus on its steps` ``: the
  Smoke chart with `key = "Gm"` is a single group, labels `[Riff, Verse, Chorus]`, `scales.take(2)` ids
  `[minor_pentatonic, blues]`, no exceptions; without a key it is two groups, labels `[[Riff, Verse], [Chorus]]`, roots
  `[G, C]`.
- `` `a riff never takes a part in its parallel major` ``: `song("E5 G5 A5 E5 G5 A5", "E A B E E A B E")` is two groups,
  `[E minor_pentatonic, E major]`.
- `` `a vamp on the tonic joins the riff after it instead of naming a key` ``: the sus-intro chart is a single group,
  labels `[Intro, Verse, Chorus]`, first scale `minor_pentatonic`; `song("E9 E9 E9 E9", "E A B E A B")` is a single
  group `"E" to "major"` with exceptions `[E9]`.
- Keep: `a rock verse joins a riff of other chords on its root, and a chorus in another key stands apart`,
  `a section replaying another's chords joins its group before one of its own kind`, `a rock riff is played over the
  minor pentatonic and the blues on its root, with no exceptions`, `a vamp on one chord is answered as the chord's
  sheet is, unless a key says otherwise` (a lone vamp has nothing to join), and in `SongScaleSuggestionsTest` the two
  "riff of power chords" songs.

## CLAUDE.md

In the scales `CLAUDE.md`, the sentence "it leads with its minor pentatonic and blues, has no exceptions, and riffs on
one root join whatever home their chords lean to" at the end of the rock-riff bullet: replace the last clause with
"and a riff's group takes any part on its root in natural minor, Dorian, or Mixolydian, or on a home with exceptions,
and, where the key names the root, a guess on the riff's steps or the ♭II; never a part in the parallel major. A vamp
on one root (a sus intro) joins whatever the song puts on that root, taking its fit."
