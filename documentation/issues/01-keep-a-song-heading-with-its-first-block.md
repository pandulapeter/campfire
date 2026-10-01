# Keep a song's heading together with the first block under it

**Kind:** bug  ·  **Severity:** high  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`
**Challenged:** amended — covers a missing song's body and a first block that only fits a column without the heading (which stranded the heading in a column of its own), and names the heading gap as one value plan 13 changes.

## Problem

`layoutPrintDocument` reserves a fixed three text lines under a heading:

```kotlin
// Keep the title and its metadata with at least one body row.
val headingHeight = heading.sumOf { it.height.toDouble() }.toFloat()
if (headingHeight + options.fontSize * 3 < capacity && y + headingHeight + options.fontSize * 3 > bottom) nextColumn()
place(heading)
```

`place()` then moves a first section that fits a column, but not the room left, whole into the next column — so the
heading stays behind alone. Seen in the live run (landscape, two columns, "start each song on a new page" off, and
also two columns with it on when the overview ends mid-page): "1. Long lines / Probe / Key: E" alone in the left
column with the whole song in the right one; "4. Wide tab" as the last thing on a page with its tab on the next.
A song that starts with `{column_break}` strands its heading the same way (`if (y > margin) nextColumn()`).
The same happens at the top of an empty column when heading + first section is taller than a column although the
section alone is not: the heading is placed, the section is sent whole to the next column, and the heading is left
alone in a column of its own.

## Fix

- Name the gap under the heading once (`val headingGap = options.fontSize / 2f` today; plan 13 changes its value) and
  use it both for `space(headingGap)` and in the check below.
- Before placing the heading, compute the body's first rows: for a missing song (`entry.song == null`) the
  `wrapped(labels.missing)` rows, otherwise the rows of the first block that yields any (`rowsFor`), skipping a leading
  `ChordProBlock.Break` (and any block with no rows before the first one that has some), since the heading has just
  started the song where it is. Reuse those rows for that block instead of computing them twice. No such block (every
  block hidden): place the heading on its own as today.
- Give `place()` a `keepWhole: Boolean = true` parameter; with `false` it applies only the first-two-rows rule.
- `first` = the block's whole height when `heading + headingGap + whole <= capacity`; else (the block does not fit a
  column together with its heading, whether or not it fits one alone) its first two rows, and that block is then placed
  with `keepWhole = false` so it starts under its heading and flows on.
- Move to the next column before the heading when `y + heading + headingGap + first > bottom` and
  `heading + headingGap + first <= capacity`. Remove the three-line rule.

## Tests

`PrintLayoutTest`, with the fake measurer: a setlist of two songs, `startSongsOnNewPage = false`, the first song sized
so the second heading would land 4 lines above the bottom and its first section is 10 lines — the heading's page and
column (by its x) equal those of the section's first row. A second test for a song starting with a break (heading and
first lyric row in the same column), a third for a single section of `capacity - 1` lines (its first row is in the
heading's column), and a missing song whose heading would land on the last line (heading and "Missing" together).

## Manual check

Export a three-song setlist in landscape with two columns and "Start each song on a new page" off; no title may be the
last thing in a column.
