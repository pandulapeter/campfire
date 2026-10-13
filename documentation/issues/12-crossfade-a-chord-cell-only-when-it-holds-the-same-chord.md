# Crossfade a chord cell only when it holds the same chord, so a transposition in a numbering does not fade between two chords

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/ChordShapeCrossfade.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/ChordShapeCrossfadeTest.kt`

## Problem

`ui/chords/CLAUDE.md`: "A cell whose chord is drawn with another shape than before … crossfades into it
(`ChordShapeCrossfade.kt`, `changedShapeCells` tested); a shape the search finds arriving into the empty frame, and a
cell that is another chord now, do not." The test of "another chord" is the name alone (`ChordShapeCrossfade.kt` at
72d48deb7):

```kotlin
internal fun changedShapeCells(previous: List<ChordCell>, cells: List<ChordCell>) = cells.indices.filter { index ->
    val before = previous.getOrNull(index) ?: return@filter false
    val after = cells[index]
    before.name == after.name &&
        before.tuning.instrument == after.tuning.instrument &&
        before.selection.shape != null &&
        (before.selection.shape != after.selection.shape || before.tuning != after.tuning)
}
```

In the Nashville and Roman notations a cell's `name` is the step ("1", "IV"), which a transposition does not change, while
the chord does (its letters, `ChordCell.letterName`, and `root` change). A probe at 72d48deb7: the chords of
`{key: G} [G] [C] [D]` and of `{key: A} [A] [D] [E]` in `ChordNotation.NASHVILLE` are named `1, 4, 5` both times
(letters G, C, D → A, D, E), and `changedShapeCells` answered `[0, 1, 2]`: every cell fades from G's diagram into A's,
the letters under the name meanwhile changing in one frame. Two different chords blended for the length of the fade is
what the rule above keeps out.

## Fix

Add the chord itself to the test: `before.root == after.root && before.letterName == after.letterName &&` next to
`before.name == after.name`. `root` alone tells G from A; `letterName` also tells apart two chords of one root a numbering
could name alike (it is null for the letter notations, where the name already says it). Update the KDoc ("…and neither
is a cell that holds another chord now, a transposed step of a numbering included, whose name or letters change in one
frame anyway").

## Tests

`ChordShapeCrossfadeTest.kt`: a cell named "1" with `letterName = "G"`, `root = 7` and a shape, followed by a cell named
"1" with `letterName = "A"`, `root = 9` and another shape, is not in `changedShapeCells`; the same cell with only its
shape changed still is. Optionally build the two lists with `songChordsOf(…, ChordNotation.NASHVILLE, ChordTuning.GUITAR)`
and `chordCellsOf` from the two songs above, which is the case the probe found.

## Manual check

Set the chord notation to Nashville (Settings → Songs), open a song with a `{key}` and the Chords section unfolded, and
step the transposition up: the diagrams change in one frame with their letters, no blend of two chords. Then pick
another shape for one chord from its sheet: that cell still crossfades.
