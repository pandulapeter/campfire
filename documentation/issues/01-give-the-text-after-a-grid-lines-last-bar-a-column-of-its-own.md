# Give the text after a grid line's last bar line a column of its own, so it never widens a real bar of a longer line

**Kind:** bug (layout)  ·  **Severity:** medium  ·  **Platforms:** all (song viewer and PDF)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/GridColumns.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/GridColumnsTest.kt`, `presentation/CLAUDE.md`
**Challenged:** amended — exact test expectations computed (a port of the algorithm reproduces the current tests), the trailing piece defined precisely, a margin-plus-trailing case and the bar-less line's exact output added, CLAUDE.md made a required edit.

## Problem

`List<GridToken>.bars()` makes whatever follows a line's last bar line (`x2`, `(fade out)`) "a piece of its own", and
`alignedGridBars` then aligns pieces purely by their index in that list:

```kotlin
val lines = map { tokens -> tokens.bars().map { it.toBarParts() } }
val barCount = lines.maxOfOrNull { it.size } ?: 0
...
bar.cells.forEachIndexed { cellIndex, cell ->
    val width = textOf(cell).length
    if (cellIndex < cellWidths[index].size) {
        cellWidths[index][cellIndex] = maxOf(cellWidths[index][cellIndex], width)
```

So in a line with three bars and a trailing `x2`, `x2` is piece 3 (its `toBarParts()` has no bar lines, so `x2` is
read as cell 0) and is aligned with bar 4 of every longer line. The existing test
`the bar lines of a run stand under each other whatever the chords are` has baked the defect in: the first line's `F`
cell is padded to the width of `x2`:

```
"|: Am . . | C  . . | D  . .  | F  . . |",
"|  Am . . | E7 . . | Am     :| x2",
```

With a longer note every word of it becomes a cell: `| Am | C | D | F | G |` over `| Am | C | D | F | x2 (fade out)`
draws today as `| Am | C | D | F | G             |`, widening the run and making it wrap earlier on a phone and in the
PDF (`gridRows` in `PrintLayout.kt` and `SongGridLine` in `SongLyrics.kt` both draw what `alignedGridBars` returns).

## Fix

In `alignedGridBars`, split each line's `bars()` into its real bars and an optional **trailing piece**: the line's
last piece when the line has more than one piece and that last piece holds no `GridToken.Bar`. (Every piece but the
last ends on a bar line by construction, so only the last can be trailing; and since `ChordProSyntax.parseGridTokens`
turns every word after a line's last bar line into `GridToken.Text`, a trailing piece is always text — a repeat count, a
note, or the chord of an unclosed last bar, which the parser already treats as a comment.) A line with no bar lines at
all (`N.C.`, parsed as chords) is one piece and stays a real bar, aligned as today.

- Compute `barCount`, `marginWidths`, `openingWidths`, `cellWidths` and `closingWidths` over the real bars only.
- Build each line's aligned real bars exactly as now, then, if the line has a trailing piece, append it as one more
  `List<GridCell>`: each token as `GridCell(token = it, text = textOf(it) + " ")`, no padding. It is never aligned with
  anything — the right margin of a chart has nothing to line up with.
- Apply `trimmedAtTheEnd()` to the whole result as now; it strips the trailing piece's final space. The last real bar
  before a trailing piece keeps its trailing space, which is the gap before the note (as today).

Nothing else changes: the two consumers join each bar's cells into one text (`SongGridLine` as one `Text` per bar in a
`FlowRow`, `gridRows` as one string per bar for `gridLineRows`), so the trailing piece stays one wrap unit there, as it
is today. `LayoutBudget` counts raw token lengths and does not use the alignment.

Update the KDoc of `alignedGridBars` to say that the piece after a line's last bar line is drawn after it as written,
not aligned, and add a clause to the grid sentence of `presentation/CLAUDE.md` ("The lines of a grid run are aligned
…") saying the same.

## Tests

In `GridColumnsTest` (expected values computed by hand and by a port of the algorithm that reproduces the current
tests' expectations exactly):
- `the bar lines of a run stand under each other whatever the chords are`: the expectation becomes
  `"|: Am . . | C  . . | D  . .  | F . . |"` (one space after `F` instead of two) over the unchanged
  `"|  Am . . | E7 . . | Am     :| x2"`.
- `a closing repeat ends where the bar lines above it do` and `a margin label pushes …` are unchanged.
- Add: `"| Am | C | D | F | G |"` over `"| Am | C | D | F | x2 (fade out)"` gives exactly
  `"| Am | C | D | F | G |"` and `"| Am | C | D | F | x2 (fade out)"`.
- Add: `"Coda | G . | x2"` over `"Intro | Am . | C . |"` (a margin and a trailing note on one line) gives
  `"Coda  | G  . | x2"` and `"Intro | Am . | C . |"` (today the second line's `C` is padded to `C  .`).
- Add: `"| Am | C |"` over `"N.C."` (a line with no bar line) gives `"| Am   | C |"` and `"  N.C."`, which is today's
  output.

## Manual check

A song with a grid whose lines end in `x2` or a longer note, on a phone and in the PDF preview: the bars line up and no
bar has a gap after its chord.
