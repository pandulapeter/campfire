# Count the gaps between columns in the width a single-row page fills, and work out the fewest columns' wrapping once

**Challenged:** amended — the gap is named: `columnGapPx` (`SongSectionsLayout.kt`, `columnGap.roundToPx()`). `totalWidth` is the whole width, gaps included (`columnWidthFor` subtracts them from it), so adding them to `filledWidth` measures both the same way.

**Kind:** bug / performance  ·  **Severity:** low  ·  **Platforms:** all (wide windows, tablets)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongSectionsLayout.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md` (only if its `balancedAmong` wording needs it)
**Decision:** D1 in the README (awaiting the user) — whether to change the measure at all.

## Problem

`SongSectionsLayout.kt`, inside the grid search:

```kotlin
fun SectionGrid.imbalance(height: Int, fewest: SectionGrid): Double? {
    if (this !== fewest && wrappedLines() > fewest.wrappedLines()) return null
    ...
    val filledWidth = cellWidths.maxOf { it.sum() }
    if (filledWidth <= 0 || height <= 0 || totalWidth <= 0) return null
    return abs(ln(filledWidth.toDouble() / totalWidth) - ln(height.toDouble() / availableHeightPx))
}
```

1. `filledWidth` sums the column widths but not the `columnCount − 1` gaps between them, while `totalWidth` is the
   whole width. The share filled is understated more the more columns a grid has (about 0.08 in ln for five columns
   with 24dp gaps on a 1200dp window, against a `BALANCE_MARGIN` of 0.15), so the comparison between counts is skewed.
2. `fewest.wrappedLines()` is summed over every unit again for every candidate count, on pinch frames too.

## Fix

- `filledWidth = (0 until columnCounts.size).maxOf { row -> cellWidths[row].sum() + columnGapPx * (columnCounts[row] - 1) }`
  `columnGapPx` is the gap `columnWidthFor` subtracts from `totalWidth`, and the least that `spacedEvenly` places
  between drawn columns. Use it, not the wider gap a row spread across the screen is drawn with, so that the width
  filled is the block the columns would make set side by side. Every cell of a single row holds a section
  (`balanceIntoCells` leaves none empty), so `columnCounts[row] - 1` is the gap count.
- Compute the fewest grid's wrapped lines once per search (a `lazy` or a local computed before `balancedAmong`, passed
  through the `imbalanceOf` lambda) instead of inside `imbalance`.
- Only if D1 is "keep the measure as it is": do the second bullet alone.

## Tests

`imbalance` is a local function over Compose measurements; no unit test without extracting it. The existing
`SectionGridTest` cases of `balancedAmong` must still pass.

## Manual check

On the desktop at the default window and maximized: Bad Moon Rising at 80% (three columns on the LAPTOP device since
2026-10-12), House of the Rising Sun at the default size, and a short song on a maximized window. Compare the column
counts with the previous build; any count that changed must look at least as balanced.
