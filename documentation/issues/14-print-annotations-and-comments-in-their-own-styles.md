# Print annotations and comments in their own styles

**Kind:** output quality  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintRenderer.kt` (`draw` only), `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`
**Challenged:** amended — the frame is built per row (sides on every row, top on the first, bottom on the last) inside the rows' own heights, so a box that `place()` splits across a column or page stays an open frame on both sides and never draws above or below its rows; pages count as empty by their texts.

Runs after plan 13 (uses `PrintStyle`).

## Problem

An annotation (`[*Everybody]`) is drawn bold like a chord name (`Part(name, …, bold = true)`), so on the page
"Everybody D7 G C" reads as four chords. `{comment}`, `{comment_italic}` and `{comment_box}` all print as plain lyric
text (`is ChordProBlock.Comment -> … wrapped(block.text)`; `block.style` is ignored), so "Repeat twice" is
indistinguishable from a sung line.

## Fix

- Annotations: italic, regular weight (`chord.isAnnotation`), measured with that style in the padding and the chord
  loop.
- Comments: `CommentStyle.PLAIN` and `ITALIC` italic gray 90; `CommentStyle.BOX` regular with a 0.75 pt frame.
- For the frame, `PrintPage` gains `rules: List<PrintRule> = emptyList()` (`data class PrintRule(val x: Float, val y:
  Float, val width: Float, val height: Float, val gray: Int = 0)`, a filled rectangle). `Row` carries `rules` beside
  its parts; `place()` offsets both by the column's x and the cursor's y, and the page collects both. A boxed comment's
  text is indented by 5 pt and wrapped to `columnWidth - 10`; its first row is 3 pt taller with its text moved down
  3 pt, its last row 3 pt taller below the text (one row: both). Every row of it carries the two sides
  (`x = 0` and `x = columnWidth - 0.75`, full row height), the first row the top edge at its `y = 0`, the last row the
  bottom edge at its `height - 0.75` — all inside the rows, so nothing is drawn over the content before or after, and
  a box split by `place()` continues as an open frame in the next column. `PrintRenderer.draw` fills the rules before
  the texts. `finishPrintDocument` still drops a page with no texts and still adds the page number as a text.

## Tests

`PrintLayoutTest`: an annotation's text has `italic` and not `bold`; a one-row boxed comment emits four rules enclosing its
text and inside the column (`x ≥ columnX`, `x + width ≤ columnX + columnWidth`); a boxed comment split across two
columns has sides in both and its top and bottom edges once each; a plain comment is italic.

## Manual check

Export the demo songs: "Everybody" is italic over the chorus line; a `{comment_box}` is framed.
