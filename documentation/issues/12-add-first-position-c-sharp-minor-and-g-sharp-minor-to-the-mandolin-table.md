# Add the first-position C#m and G#m shapes to the mandolin table, so that C#m, G#m, and Bbm stop defaulting to shapes high up the neck

**Kind:** bug (chord data)  ·  **Severity:** low  ·  **Platforms:** all
**Files:**
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordVoicingTables.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordVoicingsTest.kt`

## Problem

The mandolin table (G D A E, `ChordVoicingTables.mandolin`) has only two minor triads with no open string, which are
the ones moved to every other root:

```kotlin
"Fm frets 5 3 3 4 fingers 4 1 1 2",
...
"F#m frets 2 4 4 5 fingers 1 2 3 4",
```

`ChordVoicingSearch.tableShapes` puts the table's open shapes of a chord first and then every movable shape of its
quality moved to its root, kept between the first and the ninth fret and sorted by position:

```kotlin
val open = shapes.filter { !it.isMovable && it.chord == target }.map { it.voicing }
val movable = shapes.filter { it.isMovable && it.chord.intervals == target.intervals && it.chord.bassInterval == target.bassInterval }
    .flatMap { shape -> shiftsOf(shape.voicing.frets, (target.root - shape.chord.root).mod(12)).map { shape.voicing.shiftedBy(it) } }
    .sortedWith(compareBy({ position(it.frets) }, { -it.frets.count { fret -> fret != null } }))
```

So a minor chord without an open shape of its own gets whichever of those two lands lowest. Checked against HEAD with a
throwaway test of `ChordVoicings.default(…, ChordInstrument.MANDOLIN)`:

| Chord | Default today | What a chart shows |
|---|---|---|
| C#m | `9 11 11 12` | `1 2 4 4` |
| G#m / Abm | `4 6 6 7` | `1 1 2 4` |
| Bbm | `6 8 8 9` | `3 3 4 6` |
| D#m / Ebm | `3 1 1 2` | (fine) |
| Fm | `1 3 3 4` | (fine) |
| F#m | `2 4 4 5` | (fine) |

C#m — a common chord in E and A — is drawn at the ninth fret, which reads as a mistake to any mandolin player.

## Fix

Add two lines to `ChordVoicingTables.mandolin`, in its existing syntax and in the table's root order (C#m after
`"C# frets 1 3 4 1 fingers 1 2 3 1"`, G#m before or after `"Ab frets 1 1 3 4 fingers 1 1 3 4"`):

```kotlin
"C#m frets 1 2 4 4 fingers 1 2 3 4",
"G#m frets 1 1 2 4 fingers 1 1 2 4",
```

Both were checked with `ChordProDefinitions.read` and `ChordShapeGeometry`: C#m sounds G# E C# G# (exactly C#m), G#m
sounds G# D# B G# (exactly G#m), both are holdable (four fingers, a stretch of three frets, inside the
`DIAGRAM_FRETS = 4` rule), and their fingerings name a finger for every stopped string, so
`ChordVoicingTablesTest` accepts them. Both have no open string, so they are movable and become candidates for every
minor root. Worked out from `tableShapes`' rules, the defaults then become: C#m `1 2 4 4`, G#m and Abm `1 1 2 4`, Bbm
`3 3 4 6` (the G#m shape moved up two frets, which is the usual chart shape); D#m `3 1 1 2`, Fm `1 3 3 4`, and F#m
`2 4 4 5` stay as they are (still the lowest), and Cm, Dm, Em, Gm, Am, and Bm keep their open shapes, which come first.

Minor sevenths are a different quality and are not affected: Cm7 stays `5 5 6 6`, Bm7 `4 4 5 5`, and C#m7 `6 6 7 7`
(the `Am7 frets 2 2 3 3` shape moved). Optionally add `"Bm7 frets 2 0 2 2 fingers 1 0 2 3"` (A D B F#, all four notes,
holdable — checked the same way), a common open Bm7 that would then come first for Bm7 only, since an open shape is
never moved. Leave Cm7 alone unless the mandolin player review (below) asks for `3 1 3 3`.

No CLAUDE.md change: `chordpro/CLAUDE.md` describes the tables and the rule, not their contents.

## Tests

Extend the mandolin map in `ChordVoicingsTest` `the forty chords every songbook uses are shown the way every chart shows
them` with `"C#m" to "1 2 4 4"`, `"G#m" to "1 1 2 4"`, and `"Bbm" to "3 3 4 6"` (and `"Bm7" to "2 0 2 2"` if the
optional line lands). `ChordVoicingTablesTest` and `every shape the search finds keeps its rules` then check the new
lines' notes, fingering, and holdability on their own. Run
`./gradlew :chordpro:desktopTest`.

## Manual check

Set the chord instrument to mandolin, open a song in E or A with C#m and G#m (and one in Db or F with Bbm): the chord
diagrams at the top show the first-position shapes above. A mandolin player should still review the whole mandolin table,
which is owed already since it was typed from memory.
