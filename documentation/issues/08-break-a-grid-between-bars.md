# Break a chord grid between bars, never inside one

**Kind:** output quality  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt` (`bars()` made `internal`, nothing else; no other plan touches the file)
**Challenged:** amended — groups bars the way the viewer's `bars()` does (each bar ends on the line that closes it, so a wrapped row never starts with a stray bar line); the old grouping and its "every row starts with `|`" test failed on `|: … :|`, voltas and a margin label before the first bar.

## Problem

A grid line is joined into one string and wrapped as prose:

```kotlin
is ChordProLine.Grid -> if (options.showChords) addAll(wrapped(line.tokens.joinToString(" ") { … }, bold = true))
```

so a line too wide for the column breaks wherever the width runs out: `| C . . . | F .` / `. . | G …` (live run,
`hard-continuous-p3`). The viewer breaks between bars (`SongGridLine`).

## Fix

Split the tokens into bars exactly as the viewer's private `List<GridToken>.bars()` in `SongLyrics.kt` does: each bar
ends on the bar line that closes it, the line that opens the first bar (and a margin label before it) stays with it,
and whatever follows the last bar line is a piece of its own. Make that function `internal` and call it rather than
copying it, so the two cannot drift. Each group's text is its tokens joined by `" "` as today; measure each group
(bold, and after plan 13 in the grid's monospace style), and fill rows greedily with whole groups joined by `" "`. A
single group wider than the column falls back to `wrapped` for that group alone. Keep the rows of one grid line as
separate `Row`s (they may flow), bold.

## Tests

`PrintLayoutTest`: an eight-bar grid line `|: C . | F . | … :|` in a column that fits three bars → every row but the
last ends with a bar-line token's text, no row but the first starts with one, and the rows joined by `" "` equal the
unwrapped line; a line with a margin label (`GridToken.Text`) before its first bar keeps the label on the first row.

## Manual check

Export a song with `{start_of_grid}` at two columns and font 16.
