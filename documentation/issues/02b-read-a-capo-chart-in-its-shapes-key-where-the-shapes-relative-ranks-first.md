<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Read a capo chart keyed by the sounding key in its shapes' key also where the shapes' relative key ranks first

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** amended — the widening is limited to the reading of the whole song (`keyOfChordsIn` and the whole-song
fit); applied to every part's fit too, as first written, it read a correctly keyed chart's G minor prechorus (Don't
Stop Me Now under `{key: F}{capo: 7}`) as B♭ major, so "misread none" held only for the song's key. Test added.
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/Harmony.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroups.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/SongKeys.kt` (KDoc only),
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/SongKeysTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroupsTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`

**Decision for the user:** this widens a heuristic for hand-written and third-party capo charts (Campfire's own PDF
round trip is fixed by 02a without one). Recommended: yes, as amended (whole song only): it gained 46 of 2,094
sounding-keyed charts' keys and changed nothing at all, key or scale suggestion, on any of 2,094 shapes-keyed ones. The
alternative is to let it reach every part's fit as well: 48 more sounding-keyed songs' scale suggestions come out as
the uncapoed song's, at the price of correctly keyed charts' sections read in their relative (one measured, below).
Drop this plan if the user would rather keep the rule as narrow as it is.

## Problem

A capo chart may write its `{key}` as the key that sounds (`{key: A}{capo: 2}` over G shapes). `SongKeys` reads it so
only where the song is heard on the capo-shifted root: `Harmony.capoKey`

```kotlin
val capoKey by lazy {
    val capo = parts.first().capo?.takeIf { it != 0 } ?: return@lazy null
    val key = key ?: return@lazy null
    ranked.first().takeIf { it.pitchClass == (key.root.pitchClass - capo).mod(12) }?.let { SongKey(it, key.isMinor) }
}
```

`ranked` scores roots by chords on them, the first and last chord, and cadences, so a progression that starts on the
relative chord ranks the relative first, and the chart is then read in the written (sounding) key. Probed at HEAD
(`SongKeys.ofChords`):

| file | chords | read | should be |
|---|---|---|---|
| `{key: F#m}{capo: 2}` | `G D Em C` | F#m | Em |
| `{key: A}{capo: 2}` | `Em C G D` | A | G |
| `{key: D}{capo: 2}` | `Am F C G` | D | C |
| `{key: F#m}{capo: 2}` | `C G D Em` | F#m | Em |

Under the default chord names the card, header, and stepper then name F♯m as the chords' key, the song is said to
sound in G♯m, and the degrees and scale suggestions count from the wrong tonic.

## Fix

In `Harmony.capoKey`, also accept a first-ranked root that is the shifted key's relative (three semitones up for a
minor key, nine for a major one), where the shifted root itself is ranked too, and never where that relative is the
written key's own root (a minor key under capo 3, a major one under capo 9: there the first-ranked root is the written
tonic, the best evidence the written key is the chords' own):

```kotlin
val capoKey by lazy {
    val capo = parts.first().capo?.takeIf { it != 0 } ?: return@lazy null
    val key = key ?: return@lazy null
    val shifted = (key.root.pitchClass - capo).mod(12)
    val relative = (shifted + if (key.isMinor) 3 else 9).mod(12)
    val first = ranked.first()
    when (first.pitchClass) {
        shifted -> SongKey(first, key.isMinor)
        // A progression opening on the relative chord (G D Em C in Em) ranks the relative first; the shapes' tonic,
        // ranked too, is still the key, unless that relative is the written tonic itself.
        relative -> if (relative == key.root.pitchClass) null else ranked.firstOrNull { it.pitchClass == shifted }?.let { SongKey(it, key.isMinor) }
        else -> null
    }
}
```

`shapesKey`'s guard (the shifted key's own scale must hold more of the plays than the written key's) is unchanged, so
the widening only proposes a key; the plays still decide.

**Only for the whole song.** `Harmony` is also built for every part and group of parts (`SongScaleGroups.fitOf`), where
`shapesKey ?: key` is the key the part's scale suggestion is tried in. A section with a centre of its own in a
correctly keyed chart can rank the shifted key's relative first: Don't Stop Me Now (`tools/screenshots/library`) under
`{key: F}{capo: 7}`, its prechorus `F7 Bb Gm7 D7 Gm D7 Gm Gm Am Bb B C` ranks G first, the shifted key is B♭, whose
scale holds more of those plays than F's (the F7), so the prechorus is suggested B♭ major instead of G natural minor.
So `Harmony` takes `isWholeSong: Boolean` (no default), and the `relative` branch answers null unless it is set:

```kotlin
relative -> if (!isWholeSong || relative == key.root.pitchClass) null else ranked.firstOrNull { it.pitchClass == shifted }?.let { SongKey(it, key.isMinor) }
```

`SongScaleGroups.keyOfChordsIn` builds it with `isWholeSong = true`; `fitOf` takes the flag too, `playedGroupsOf`
passing `true` for its first, whole-song call (`fitOf(parts, isWholeSong = true)`) and `false` for each part and group.
The whole-song fit and the song's key then agree (a song read in Em is suggested in Em when it is one tonal group),
and a part keeps today's rule. Update the KDoc of `capoKey` (naming the whole-song limit and why: a section's own
centre on the shifted key's relative), `fitOf`, and `SongKeys`, and the scales `CLAUDE.md` sentence "only where the
part is heard on its root" to "where the part is heard on its root, or, read as the whole song, on that key's relative
with its root ranked too, never the written tonic".

Measured out of tree (the chordpro sources compiled with each rule, `SongKeys.ofChords` and `SongScaleSuggestions.of`;
challenge's probe in the review's scratchpad, `c2b/`):

- 349 real songs (`:chordpro`'s corpus, the demo songs, `tools/screenshots/library`, and the test fixtures with one
  `{key}` and no `{capo}` or `{transpose}`), each given capo 1, 2, 3, 4, 5, and 7: **shapes-keyed** (the written key
  kept, 2,094 charts) misread at HEAD 19, with this rule 19, the same 19 (**0 new false positives**), and their scale
  suggestions identical to HEAD's (as first written, applied to every part: two songs' suggestions changed, Don't
  Stop Me Now's prechorus misread as above); **sounding-keyed** (the key moved up by the capo, 2,094 charts) read right
  at HEAD 1,403, with this rule 1,449 (the same as applied to every part). Their scale suggestions match the uncapoed
  song's at HEAD in 1,225 of 2,214 runs, with this rule 1,245, applied to every part 1,293.
- 70 hand-labelled progressions (the review's probe set, common capo progressions keyed both ways): 66 → 69 right;
  the three first rows of the table above are fixed.
- Without the "never the written tonic" exclusion: one new false positive (`{key: F#m}{capo: 3}` read in E♭m).
  Accepting the shifted root anywhere in the top three ranked: 45 new false positives (`{key: C}{capo: 5}` over
  `C Am D7 G` read in G among them), so that looser rule is not safe.

What stays unfixed: `{key: F#m}{capo: 2}` over `C G D Em` (ranked C, E, G: the first is the shapes' VI, neither the
shifted root nor its relative). Only the looser rule reads it, at 45 misreads; Campfire's own PDF of such a song is
fixed by 02a, which writes the shapes' key.

Independent of 02a and 12 (disjoint files).

## Tests

In `SongKeysTest`, a test `a capo chart keyed by the sounding key is read in its shapes' key where it opens on the
relative chord`:

```kotlin
assertEquals("Em", SongKeys.ofChords(song(key = "F#m", capo = 2, chords = "G D Em C")))
assertEquals("G", SongKeys.ofChords(song(key = "A", capo = 2, chords = "Em C G D")))
assertEquals("C", SongKeys.ofChords(song(key = "D", capo = 2, chords = "Am F C G")))
```

(all fail at HEAD), and in `a capo chart keyed by its shapes' key, or fitting both keys alike, keeps its written key`:

```kotlin
assertEquals("F#m", SongKeys.ofChords(song(key = "F#m", capo = 3, chords = "F#m C#7 D Bm Ebmaj7 Abm Db Gb E# Cb B#dim")))
assertEquals("C", SongKeys.ofChords(song(key = "C", capo = 5, chords = "C Am D7 G")))
```

pinning the exclusion (the corpus's `23-sharps-flats` chords: without the "never the written tonic" clause they read
in E♭m, probed) and the capo-5 case.

In `SongScaleGroupsTest`, `a section on the relative of a correctly keyed capo chart's shifted key keeps its own
centre`: the chords of `tools/screenshots/library/library/songs/queen-dont_stop_me_now.cho` (its sections from
`{start_of_intro}` on, inlined; a shortened copy came out as one tonal F major group under every rule, so keep the
whole chart) under `{key: F}\n{capo: 7}`, asserting every group of `SongScaleGroups.of` holding a prechorus section has root
`G` and `natural_minor` first (at HEAD: F major for seven sections, then two G natural minor groups). Passes at HEAD and fails with the widening applied to every part (B♭ major, probed), so it
guards the limit. Run the whole `:chordpro:desktopTest`: the scale suggestion and degree tests read capo charts
through `shapesKey` too.

Plan 02a: a song this plan reads differently prints a different shapes marker (`PrintLayout.playingDetails` names
`ShapeNames.shapesKey`, which follows `Song.chordsKey`, which is `capoChartKeyOf`): `{key: F#m}{capo: 2}` over
`G D Em C` prints `Key: F♯m   Capo: 2 (Em shapes)` instead of `Key: G♯m   Capo: 2 (F♯m shapes)`, and 02a reads Em back,
the right key; nothing persists `capoChartKey` (recomputed on every scan), so no stale value outlives the change.
