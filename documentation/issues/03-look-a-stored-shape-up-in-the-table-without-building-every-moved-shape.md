# Look a stored shape's fingering up in the table directly, instead of building and sorting every moved shape of its chord on each cell

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the optional micro-step must leave the drone out of its count of sounding strings, since a
move rewrites the drone; the lookup itself is equivalent and stands.
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordVoicingSearch.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordVoicings.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/chords/ChordVoicingsTest.kt`,
`chordpro/CLAUDE.md`

## Problem

`ChordVoicings.read` gives a stored shape (`x 3 2 0 1 0`) its table fingering back by building the chord's whole
table answer and scanning it (`ChordVoicings.kt:108` at 72d48deb7):

```kotlin
return chord?.let { tableShapes(it, tuning).firstOrNull { known -> known.frets == frets } } ?: ChordVoicing.Fretted(frets)
```

`ChordVoicingSearch.tableShapes` is uncached: for every call it takes every movable shape of the chord's quality filed
under another root, moves each to the chord by up to three octave shifts, allocates the moved voicings, sorts them all,
and `distinctBy`s the result. `read` with a chord runs on every cell build of every chord the player has stored a shape
for: `selectShape` (`presentation/.../ui/chords/ChordSelection.kt`) calls it from `chordCellsOf` per song model, from
`PrintDocument.kt` per printed chord, from `ChordReferenceSheet` and `ChordShapeSheet`, and from `ChordsScreen.kt:186`
inside the grid's item composable, so once per Chords tab cell per composition.

Measured with a probe on the desktop JVM (warm): `read` with a chord took 33–112 µs per call for C, Am, G7, Fmaj7, and
C/E on the guitar, against 0.6–2.7 µs without the chord. That is a few milliseconds per frame for a Chords tab page of
chords with stored shapes, and repeated work on every song model and PDF; small, but entirely avoidable.

## Fix

Add a direct lookup to `ChordVoicingSearch` that returns exactly what `tableShapes(chord, tuning).firstOrNull { it.frets == frets }`
returns, without building or sorting the list:

```kotlin
/**
 * The shape of [tableShapes] for [chord] in [tuning] whose frets are [frets], with its fingering, or null where the
 * table has none: what [tableShapes] would answer, without moving and sorting every shape of the chord's quality.
 */
fun tableShape(chord: Chord, tuning: ChordTuning, frets: List<Int?>): ChordVoicing.Fretted? {
    val target = playedOn(chord.movedBy(-tuning.shift), tuning.instrument)
    val table = table(tuning)
    table.filed[target]?.firstOrNull { it.frets == frets }?.let { return it }
    val drone = tuning.instrument.droneString
    val droneFret = drone?.let { if (tuning.tableTuning.strings[it] % 12 in target.pitchClasses) 0 else null }
    table.movable[target.quality].orEmpty().forEach { shape ->
        if (shape.chord == target) return@forEach
        shiftsOf(shape.voicing.frets, (target.root - shape.chord.root).mod(12), drone).forEach { shift ->
            val moved = shape.voicing.shiftedBy(shift, drone, droneFret)
            if (moved.frets == frets) return moved
        }
    }
    return null
}
```

Why the result is identical: `tableShapes` puts every filed shape before every moved one, and `distinctBy { it.frets }`
keeps the first of equal frets, so a filed match always wins, which the first lookup does. Among moved shapes it sorts by
`position(frets)` then by the count of sounding strings, both functions of the frets alone, with a stable sort; so shapes
with equal frets keep their table-and-shift order, which is the order the loop walks. (An optional micro-step: skip a
shape whose number of non-null frets, *the drone string left out of both counts*, differs from `frets`' before moving
it: a move sets the banjo's drone to `droneFret` whatever the table shape had there, so counting it would skip a shape
that does match.)

Then in `ChordVoicings.read`:

```kotlin
return chord?.let { ChordVoicingSearch.tableShape(it, tuning, frets) } ?: ChordVoicing.Fretted(frets)
```

and import `tableShape` alongside the others. `tableShapes` itself stays for `default` and `all`.

The alternative, a bounded cache of `tableShapes` by `(chord, tuning)` like `defaults`, also works but adds state and
`resetCache` bookkeeping for no gain over the direct lookup; not recommended.

In `chordpro/CLAUDE.md`, the sentence "`write` and `read` are a stored choice …, and reading one back for a chord the
tables know gives it their fingering" can gain "(looked up in the table's index, without building the chord's shapes)";
optional.

## Tests

The existing `a stored shape is read back as it was written` in `ChordVoicingsTest` already reads back every shape of
`ChordVoicings.all` (filed, moved, and searched) for every tuning of its corpus and compares fingerings, so it pins the
equivalence; it must still pass. Add one assertion that a *moved* table shape keeps its fingering: pick a chord whose
answer includes a moved shape (any shape of `ChordVoicingSearch.tableShapes(chord, ChordTuning.GUITAR)` that is not in
`ChordVoicingTables.guitar` under that chord, for example a barre shape of a root with few filed shapes), and assert
`ChordVoicings.read(ChordVoicings.write(it), ChordTuning.GUITAR, chord) == it` with `it.fingers != null`. Also assert
`ChordVoicingSearch.tableShape(chord, tuning, frets) == ChordVoicingSearch.tableShapes(chord, tuning).firstOrNull { it.frets == frets }`
for every shape of `all` across a handful of chords (C, Am, G7, Fmaj7, C/E, Bbm7b5) and the tunings guitar, drop D,
half step down, ukulele baritone, and banjo, plus a searched shape for which both are null.

## Manual check

None visible beyond the tests: pick a non-default shape for a few chords in the Chords tab, scroll the tab and open a
song using them; the diagrams keep their finger numbers as before.
