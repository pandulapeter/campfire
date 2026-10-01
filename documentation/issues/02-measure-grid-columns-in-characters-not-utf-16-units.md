# Measure grid columns without the combining marks, so a decomposed accent in a label keeps the bars in line

**Kind:** bug (layout)  ·  **Severity:** low  ·  **Platforms:** all (song viewer and PDF)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/GridColumns.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/GridColumnsTest.kt`
**Challenged:** amended — dropped the surrogate-pair half: an emoji is a wide (two-column) glyph from a fallback font, so counting it as one column would be no more right than the two UTF-16 units it counts as today; only combining marks are left out of the count. Runs after 01, whose trailing piece is unpadded and needs no width.

## Problem

Every width in `alignedGridBars` is `String.length`:

```kotlin
openingWidths[index] = maxOf(openingWidths[index], bar.opening?.let(textOf)?.length ?: 0)
...
val width = textOf(cell).length
...
private fun List<GridToken>.textWidth(textOf: (GridToken) -> String) = if (isEmpty()) 0 else sumOf { textOf(it).length } + size - 1
```

and `addAligned` pads with `padStart` / `padEnd`, which also count UTF-16 units. `textOf` is the viewer's
`displayText()` or the PDF's `printText()`: the bar, chord, repeat and text tokens as the file wrote them, plus a beat
symbol (`·` or `.`). So the user's own text reaches the count unchanged, and a margin label written with a combining
accent (`Refrén`, decomposed — pasted text and some editors produce it) counts 7 but draws 6 columns: its first bar
line stands one column right of the line below (`Chorus | Am |`).

## Fix

Add a private `String.columns` in `GridColumns.kt`: `length` minus the number of chars whose `category` is
`CharCategory.NON_SPACING_MARK` or `CharCategory.ENCLOSING_MARK` (`Char.category` is common stdlib and works on Wasm).
Surrogate pairs keep counting as two: the characters outside the BMP a label might hold are emoji, which a monospace
face draws from a fallback font at about two columns, so two is the closer guess; wide CJK is out of scope for the same
reason (no column count is right for a fallback glyph). A variation selector (U+FE0F) is a non-spacing mark and drops
out, which is right since it draws nothing.

Use `columns` for every width the alignment compares — `textWidth` (and so `marginPadding`), the opening, cell and
closing widths — and pad in `addAligned` with `" ".repeat(width - text.columns)` before or after the text instead of
`padStart` / `padEnd`. No other code compares these widths: `gridLineRows` in the PDF measures the joined text with the
font, and the viewer draws it as one `Text`.

## Tests

In `GridColumnsTest`: `"Refrén | Am |"` over `"Chorus | C |"` gives exactly `"Refrén | Am |"` and
`"Chorus | C  |"` (today the second line is `"Chorus  | C  |"`).

## Manual check

None beyond the test.
