<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# A part that cadences into the home's root is no centre of its own

**Kind:** design  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** amended — a cadence lands home only on a chord that is no dominant seventh: as first written, a bridge on
the IV approached through its own V7 (`F F Dm Dm F F G C7` in C, the `G → C7` a V/V → V of F) lost its F centre and
was sent to C major with the C7 as an exception (probed); the rule now matches plan 03's `rankedRoots`, which scores no
cadence into a secondary dominant.
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleSuggestions.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleSuggestionsTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`

Written at HEAD `0a0babf27`. Lane S.

## Problem

`SongScaleSuggestions.centreMomentsOf` offers a part of a group as a centre of its own wherever it hardly plays the
group's root:

```kotlin
    private fun centreMomentsOf(group: PlayedGroup, weight: Int): List<SectionMoment> = group.parts
        .filter { part ->
            part.centre != group.rootPitchClass && part.chords.size >= MIN_CENTRE_CHORDS &&
                part.chords.count { it.root == group.rootPitchClass } * MAX_ROOT_SHARE <= part.chords.size
        }
        .groupBy { it.centre }
        .map { (centre, centred) ->
            val chords = centred.flatMap { it.chords }
            val home = centreHomes.maxBy { home -> chords.count { holdsChord(home, centre, it) } }
```

`part.centre` is `rankedRoots(part).first()` (`SongScaleGroups.Fit.playedGroupOf`), the root scored by chords on it,
the first and the last chord, and two for every cadence into it. A chorus or a bridge that *returns home* through a
V→I inside the part is still offered a centre of its own, and the scale on that centre holds fewer of its chords than
the home does, so the player is sent away from home exactly where the song comes back to it. Three charts, as the
probe prints them today:

Hotel California, `{key: Bm}`, chorus `G D F# Bm G D Em F#`:

```
## 06 Hotel California (Bm)
  [HOME] [Verse] B: natural_minor, minor_pentatonic, blues, minor_hexatonic (+1)
      over F# → B harmonic_minor, melodic_minor
      over E → B dorian, melodic_minor
  [Chorus] G: major_pentatonic, major
```

G major holds neither F♯ of the chorus; the F♯→Bm is the chorus. The G centre comes from `rankedRoots` scoring G three
(two chords, the first), tied with B (one chord and the F♯→Bm cadence) and F♯ (two chords, the last), the tie going to
G by its count.

A song in A with a bridge `D E F#m D E F#m D E A` (verse `A D E A …`, chorus `A E F#m D …`):

```
## 06 Key A, bridge D E F#m D E F#m D E A
  [HOME] [Verse, Chorus, Recall] A: major, major_pentatonic, major_hexatonic, bebop_dominant (+1)
  [Bridge] D: major_pentatonic, major
```

D major lacks the E ×3 (G♯); A major holds all nine chords, and the E→A at the end is the bridge landing home.

A song in E with a bridge `A A C#m C#m A A B B` ×2:

```
## 06 Key E, bridge A A C#m C#m A A B B x2
  [HOME] [Verse, Chorus, Recall] E: major, major_pentatonic, major_hexatonic, bebop_dominant (+1)
  [Bridge] A: major_pentatonic, major
```

A major lacks the B ×4 (D♯); E major holds all sixteen. This one, however, has the same shape as the settled "bridge
on the IV" below (IV vi IV vi IV vi V V), which must keep its centre: see the decision.

What must survive (all probed at HEAD, all kept by the recommended rule):

- The settled bridge on the IV, `{key: G}` verse `G D Em C G D Em C`, chorus `C D G Em C D G G`, bridge
  `C Am C Am C Am D D` → `[Bridge] C: major_pentatonic, major`.
- The chorus on the vi, `{key: G}` verse `G D C G G D C G`, bridge `Em C Em C Em C D D` and bridge `Em C Em C Em C Em C`
  → `[Bridge] E: minor_pentatonic, natural_minor` in both.
- The two tests `a bridge that hardly plays the home's root is a moment on its own centre, in the scale holding most of
  it` (bridge `D D F#m Bm A G F#m D E F#m E` in A: the E→F♯m and the loop E→D are no cadence into A) and `a part both in
  another key and on a centre of its own is one moment, on its centre` (bridge `A7 A6 A A7 A6 A E A E A …` in E: its
  E→A cadences into the centre, not into E).
- The bridge of "Chorus recalled many times" (`C#m G#m A B C#m G#m A B F# B` in E → `[Bridge] B: major_pentatonic,
  mixolydian`): the F♯→B cadences into the centre B, never into E.

## Fix

A decision between two rules; the first is recommended.

**Option 1 (recommended): no centre where the part cadences into the group's root.** A cadence is what `rankedRoots`
scores one as once lane G's plan 03 has landed: a chord with a major third a fifth above the group's root, followed by
a chord on the group's root that is no dominant seventh, the part read as a loop (the last chord followed by the
first). A dominant seventh on the root inside a part that hardly plays the root is a secondary dominant leading away,
not home: `{key: C}` bridge `F F Dm Dm F F G C7` ×2 is the IV's own II→V→I (`G → C7 → F`), and must keep its F centre
(probed at HEAD: `[Bridge] F: major_pentatonic, major`; without the dominant-seventh clause the bridge joins the home
row with `over C7 → C mixolydian`). Add to `SongScaleSuggestions`:

```kotlin
    /**
     * Whether the part cadences into [root]: a chord with a major third a fifth above it followed by a chord on it that
     * is no dominant seventh, the part read as a loop, as `SongScaleGroups.rankedRoots` scores a cadence (a `G → C7` in
     * a bridge on F leads on to the F, not home to C). A part that resolves home that way is heard at home however
     * little it plays the root, so it is no centre of its own.
     */
    private fun PlayedPart.cadencesInto(root: Int) = chords.indices.any { index ->
        val chord = chords[index]
        val next = chords[(index + 1) % chords.size]
        MAJOR_THIRD in chord.intervals && (chord.root - next.root).mod(12) == FIFTH && next.root == root && !next.isDominantSeventh
    }
```

(`isDominantSeventh` is the package's, `ChordNotes.kt`.)

with `private const val FIFTH = 7` beside `MAJOR_THIRD`, and extend the filter of `centreMomentsOf`:

```kotlin
        .filter { part ->
            part.centre != group.rootPitchClass && part.chords.size >= MIN_CENTRE_CHORDS &&
                part.chords.count { it.root == group.rootPitchClass } * MAX_ROOT_SHARE <= part.chords.size &&
                !part.cadencesInto(group.rootPitchClass)
        }
```

`PlayedPart.chords` (`PlayedGroup.kt`) is the part's chords in play order, read without their bass, so this is
self-contained in lane S's file. Update the KDoc of `centreMomentsOf` and the class KDoc's "Another centre" bullet
("… a part of a group that hardly plays the group's root (at most one chord in [MAX_ROOT_SHARE]), never cadences into it,
and leans on a root of its own …").

Under this rule: Hotel California's chorus (F♯→Bm) and the bridge in A (E→A) join the home row; the bridge in E
(`A A C#m C#m A A B B`, no B→E anywhere, the loop B→A included) **keeps its centre**, as does the settled
`C Am C Am C Am D D` (D→D, then the loop D→C: no chord on G follows the D, so the ending is no cadence) and both vi
bridges (D→Em on the loop is a chord on E, not on G). The two bridges on the IV ending on the V are the same shape; no
rule on the chords alone can keep one and drop the other, and this plan keeps both: a vamp on the IV that lifts to the
V and never resolves inside the part is the IV's, and the player takes the V's third as it comes.

The "ties in the centre's score go to the group's root" rule of the review is not needed: a tie with the root on a part
playing the root at most one chord in eight can only come from the two points of a cadence into it (chords on the root,
the first, and the last add at most three, and a centre out-playing it eight to one scores more), which is the cadence
this rule already reads. It would also need lane G to expose `rankedRoots`' scores on `PlayedPart`; do not ask for it.

**Option 2: Option 1, and no centre where the centre's scale holds fewer of the part's chords than the group's lead
scale does.** This drops the bridge in E (E major holds all sixteen, A major twelve), but equally the settled bridge on
the IV (G major holds all eight of `C Am C Am C Am D D`, C major six) and both vi bridges (G major holds all of them, E
natural minor six of eight), which the review says must survive. Take it only if the user wants those rulings
reversed; the chips would then show nothing for those bridges, the home covering them.

Expected probe lines after Option 1:

```
## 06 Hotel California (Bm)
  [HOME] [Verse, Chorus] B: natural_minor, minor_pentatonic, blues, minor_hexatonic (+1)
      over F# → B harmonic_minor, melodic_minor
      over E → B dorian, melodic_minor
## 06 Key A, bridge D E F#m D E F#m D E A
  [HOME] [Verse, Chorus, Bridge, Recall] A: major, major_pentatonic, major_hexatonic, bebop_dominant (+1)
## 06 Key E, bridge A A C#m C#m A A B B x2
  [HOME] [Verse, Chorus, Recall] E: major, major_pentatonic, major_hexatonic, bebop_dominant (+1)
  [Bridge] A: major_pentatonic, major
```

Out of scope, for the record: a flamenco falseta `E F E F E F G F E` beside a verse in Am is today a centre
"E major pentatonic, E major" (E major holds its five E chords, the most of the four `centreHomes`); its right answer,
E Phrygian dominant, needs the centre homes widened, which is a design question of its own, not this plan's.

## Tests

In `SongScaleSuggestionsTest` (its `suggestions(vararg sections, key)` helper; the `labels` helper casts blocks to
`ChordProBlock.Section`, so write every section out rather than recalling a chorus):

- `` `a part that cadences into the home's root is no centre of its own` ``: Hotel California,
  `"Intro" to "Bm F#7 A E G D Em F#"`, `"Verse" to "Bm F#7 A E G D Em F#"`, `"Chorus" to "G D F# Bm G D Em F#"`,
  `"Verse" to "Bm F#7 A E G D Em F#"`, `key = "Bm"` → `song.map { it.labels }` is
  `listOf(listOf("Intro", "Verse", "Chorus", "Verse"))` and `song.single().isHome`; and the song in A,
  `"Verse" to "A D E A A D E A A D E A"`, `"Chorus" to "A E F#m D A E F#m D A E D A"`,
  `"Bridge" to "D E F#m D E F#m D E A"`, `"Chorus" to "A E F#m D A E F#m D A E D A"`, `key = "A"` → one group,
  `labels == listOf("Verse", "Chorus", "Bridge", "Chorus")`, `rootName to scales.first().id == "A" to "major"`.
- `` `a vamp on the IV that never resolves home stays a centre of its own` ``: the song in E,
  `"Verse" to "E B C#m A E B C#m A E B A B"`, `"Chorus" to "A B E E A B E E"`,
  `"Bridge" to "A A C#m C#m A A B B A A C#m C#m A A B B"`, `"Chorus" to "A B E E A B E E"`, `key = "E"` →
  `song.map { it.labels } == listOf(listOf("Verse", "Chorus", "Chorus"), listOf("Bridge"))` and
  `song[1].rootName to song[1].scales.map { it.id } == "A" to listOf("major_pentatonic", "major")`; and the settled
  bridge on the IV, `"Verse" to "G D Em C G D Em C"`, `"Chorus" to "C D G Em C D G G"`,
  `"Bridge" to "C Am C Am C Am D D"`, `key = "G"` → `listOf(listOf("Verse", "Chorus"), listOf("Bridge"))`, the bridge
  on `"C"` with `listOf("major_pentatonic", "major")`; and the bridge on the IV reached through its own V7,
  `"Verse" to "C Am F G C Am F G"`, `"Chorus" to "C F G C C F G C"`,
  `"Bridge" to "F F Dm Dm F F G C7 F F Dm Dm F F G C7"`, `"Chorus" to "C F G C C F G C"`, `key = "C"` →
  `listOf(listOf("Verse", "Chorus", "Chorus"), listOf("Bridge"))`, the bridge on `"F"` with
  `listOf("major_pentatonic", "major")` (the `G → C7` lands on a dominant seventh, no cadence home).
- The existing `a bridge that hardly plays the home's root is a moment on its own centre, in the scale holding most of
  it`, `a part both in another key and on a centre of its own is one moment, on its centre`, and `chords one scale on
  one root serves are one moment, named by each` (its Hotel California chorus now joins the home row; it reads
  `.first()`, so it still passes) stay as they are and must pass.

`CLAUDE.md` (`chordpro/scales`): in the `SongScaleSuggestions` bullet, change "parts on a centre of their own that
hardly play the root (a bridge on the IV, offered as the scale on it holding most of its chords)" to "parts on a centre
of their own that hardly play the root and never cadence into it (a bridge on the IV, offered as the scale on it holding
most of its chords; a chorus whose V→I lands home is the home's)".
