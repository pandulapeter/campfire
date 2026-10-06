# Keep a PDF chord diagram's name and second name inside its column

**Challenged:** amended — a name line can hold two runs in two styles (the name and its ` second name`), so a cell's lines are lists of runs with their x, and a line is as tall as its largest style (1.45 × size), not the sum of its runs; the test's "below every name" check now uses that line height.

**Kind:** bug (PDF layout)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`, `presentation/CLAUDE.md`

## Problem

`PrintLayouter.chordDiagramRows` (PrintLayout.kt at dac1d9d59) sizes a cell by its name and caps it at the column, but
places the name and the second name at their full width:

```kotlin
val nameWidth = measure(chord.name, chordStyle)
val secondaryWidth = chord.secondaryName?.let { measure(" $it", detailStyle) } ?: 0f
DiagramCell(chord, diagramWidth, diagramHeight, maxOf(diagramWidth, nameWidth + secondaryWidth).coerceAtMost(columnWidth), nameWidth)
...
parts += Part(cell.chord.name, x = x, style = chordStyle, isSelectable = false)
cell.chord.secondaryName?.let { parts += Part(" $it", x = x + cell.nameWidth, style = detailStyle, isSelectable = false) }
```

A text that is wider than `columnWidth` therefore runs past the column's end, over the 18pt gutter and into the next
column's lyrics. With the layout tests' own measure (`0.6 × size` per character), A4 portrait, 25mm margins, 4 columns
and 20pt text: `columnWidth = (595.28 − 2·70.87 − 3·18) / 4 ≈ 99.9pt`. A Nashville chart's `b7sus4` (6 · 12 = 72pt)
with its letters ` Bbsus4` (7 · 11.4 ≈ 79.8pt) is ≈ 152pt — 52pt over; even a single long name such as `C#m7b5/G#`
(9 · 12 = 108pt) is over on its own. The second name exists wherever the page counts chords (Nashville / Roman, letters
as the second name) or a capo is drawn on the keyboard (the sounding chord), which is when names get long. Every other
text on the page is wrapped to its column (`wrapPrintText`), and `PrintLayoutTest`'s first test asserts as much for
lyrics, but no test covers diagram names (`chordDiagramsWrapIntoRowsInsideTheColumn` checks only the diagrams' boxes).

## Fix

In `chordDiagramRows`, give each cell a list of name lines that each fit the column, and let a row's name block be as
tall as its tallest cell's:

1. Per cell: if `nameWidth + secondaryWidth <= columnWidth`, one line as today (name, then ` secondary` after it). Else
   the second name goes on a line of its own under the name, without the leading space (`secondary` in `detailStyle`).
   Any single line still wider than `columnWidth` is broken with the existing
   `wrapPrintText(text, columnWidth) { measure(it, style) }` into as many lines as it needs (it cuts at grapheme
   clusters, never clipping, which is the rule the rest of the page follows: "flowing long songs … without reducing
   the requested size or clipping").
2. Extend `DiagramCell` with the lines it prints: `val nameLines: List<List<NamePart>>`, where
   `private data class NamePart(val text: String, val x: Float, val style: PrintStyle)` (x from the cell's start). The
   one-line case is `listOf(listOf(NamePart(name, 0f, chordStyle), NamePart(" $secondary", nameWidth, detailStyle)))`;
   the split case is the name's line(s) in `chordStyle`, then the second name's line(s) in `detailStyle`, each at x 0.
   A line's height is `line.maxOf { it.style.size } * 1.45f` (the same factor as `nameHeight` today), so a one-line
   cell is exactly `chordStyle.size * 1.45f` tall as before. `width = maxOf(diagramWidth, widest line's end).coerceAtMost(columnWidth)`;
   `nameWidth` can go.
3. Row geometry: `val rowNameHeight = row.maxOf { cell -> cell.nameLines.sumOf { line -> line.maxOf { it.style.size } * 1.45f } }`.
   Place each cell's lines from `y = 0` down, each run as `Part(text, x = cellX + run.x, y = lineTop, style, isSelectable = false)`;
   the diagram at `y = rowNameHeight`; the row's `height = rowNameHeight + row.maxOf { it.diagramHeight } + gap-if-not-last`.
   A row whose cells all have one line is laid out exactly as today. The rows are still the units `place` keeps with
   the heading (`place(firstBlockRows, keepWhole = ...)` around line 343 is unchanged); a taller row only makes that
   unit taller.
4. Keep every name `isSelectable = false` (unchanged).
5. `presentation/CLAUDE.md`, the Layout step's diagram sentence ("a song's diagrams follow its heading as rows of cells
   sized by the text size (the chord's name over its diagram …)"): add "a name and its second name too wide for the
   column together are set on two lines, and one wider than the column on its own is broken across lines like any
   other text".

## Tests

In `PrintLayoutTest` (pure, measured with the test's arithmetic):
- `chordDiagramNamesStayInsideTheirColumn`: `PrintSettings(showChordDiagrams = true, columns = 4, fontSize = 20, marginMm = 25)`,
  a song whose chords are `PrintChord("b7sus4", secondaryName = "Bbsus4", geometry = …)`, `PrintChord("C#m7b5/G#", geometry = …)`
  and two short ones; for every non-selectable text on the page, `x >= margin` and
  `x + measure(text, style) <= margin + columnWidth + 0.01f` for the column it starts in (with one column the whole
  text area; simplest is to put the song in column 1 and assert against the first column's bounds), and every diagram
  starts below every name of its row (`diagram.y >= name.y + name.style.size * 1.45f - 0.01f`, which the one-line
  case meets with equality).
- `aNameThatFitsKeepsItsSecondNameOnItsLine`: at the default settings `PrintChord("5", secondaryName = "G")` prints
  `"5"` and `" G"` with the same `y`.

## Manual check

Export a Nashville-numbered song (Settings → Songs → Nashville numbers) with chords such as `Bb7sus4`, `C#m7b5/G#` and
`F#6/9` in a key of C, as a PDF at 20pt, 4 columns, 25mm margins, Chord diagrams ticked: no name runs into the next
column; the letters sit under the number where they do not fit beside it. At the default 12pt / 1 column the page looks
as it did.
