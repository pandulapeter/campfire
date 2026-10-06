# Bound what a chord diagram draws — at most 24 frets and four octaves, keys outside them left out — whatever shape it is handed

**Kind:** bug (robustness, defence in depth)  ·  **Severity:** low once lane C's plans 01 and 12 land; medium without them  ·  **Platforms:** all (screen, editor preview, PDF)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/ChordDiagramGeometry.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/chords/ChordDiagramGeometryTest.kt`, `presentation/CLAUDE.md`

## Problem

`chordDiagramGeometryOf` (`ChordDiagramGeometry.kt`) sizes the diagram from the shape with no upper bound:

```kotlin
is ChordVoicing.Keys -> ChordDiagramGeometry.Keyboard(
    octaves = maxOf(MIN_OCTAVES, ((listOfNotNull(shape.bass) + shape.notes).maxOrNull() ?: 0) / 12 + 1),
    ...
)
...
val fretCount = maxOf(MIN_FRETS, (stopped.maxOrNull() ?: 0) - baseFret + 1)
```

and `ChordDiagram.kt` then draws a line per fret (`(0..geometry.fretCount).forEach { … drawLine(…) }`) and a key per
semitone (`(0 until geometry.octaves * 12).filter { … }.forEach { … }`, plus `whiteKeys + 1` lines). A shape spanning
`3 … 99999999` (`{define: G frets 3 2 0 0 0 99999999}`, which `ChordProDefinitions.read` accepts today) is a hundred
million draw calls a frame: the song page, the editor's preview while the number is typed, and the PDF hang. A stored
keyboard shape `"0 4 99999999"` (synced `chords` member, `ChordVoicings.read` takes any note ≥ 0) does the same per key.
A negative bass (`"4 7 / -5"`, accepted by `ChordVoicings.read`) is drawn left of the diagram.

Lane C's plan 01 (bound frets, base fret and keys in definitions) and plan 12 (bound a stored keyboard shape's keys and
bass) close the readers. The geometry is where every source meets (definitions, stored shapes, the search, future
readers), so it should not trust them.

## Fix

In `chordDiagramGeometryOf`:

```kotlin
is ChordVoicing.Keys -> {
    val octaves = ((listOfNotNull(shape.bass) + shape.notes).maxOrNull() ?: 0).let { it / 12 + 1 }.coerceIn(MIN_OCTAVES, MAX_OCTAVES)
    val range = 0 until octaves * 12
    ChordDiagramGeometry.Keyboard(
        octaves = octaves,
        keys = shape.notes.filter { it in range }.toSet(),
        roots = shape.notes.filter { it in range && it % 12 == root }.toSet(),
        bass = shape.bass?.takeIf { it in range },
    )
}
```

and for a fretted shape `val fretCount = maxOf(MIN_FRETS, …).coerceAtMost(MAX_FRETS)`, dropping the dots and barres
whose `row` is not in `0 until fretCount` (filter `dots` and the `barresOf` result). Constants:
`private const val MAX_OCTAVES = 4` (every keyboard shape the app makes fits: `ChordVoicings.keyboard` puts the
highest note below 48) and `private const val MAX_FRETS = 24` (the same neck `ChordVoicings.read` allows). A shape cut
this way is a broken file's, and drawing part of it is better than hanging. Add a sentence to the KDoc of
`chordDiagramGeometryOf` and to `presentation/CLAUDE.md`'s description of `ChordDiagramGeometry` ("…as many frets as the
shape spans, up to 24, … as many octaves as its keys reach, up to four, a key past them left out").

## Tests

`ChordDiagramGeometryTest`: `a shape out of all reason is drawn bounded` —
`fretted(listOf(3, 2, 0, 0, 0, 99999999))` has `fretCount == 24` and no dot with a `row >= 24`;
`chordDiagramGeometryOf(ChordVoicing.Keys(listOf(0, 4, 99999999)), KEYBOARD, root = 0)` has `octaves == 4` and
`keys == setOf(0, 4)`; `ChordVoicing.Keys(listOf(4, 7), bass = -5)` has `bass == null`. The existing octave tests
(2, 2 and 3) still hold.

## Manual check

Without lane C's reader bounds (or before they land), add `{define: G frets 3 2 0 0 0 99999999}` to a song and open it
and its editor: the page stays responsive and the G diagram is a crowded 24-fret frame without the impossible dot rather than a hang. With
the bounds landed the definition is reported invalid instead, which is fine.
