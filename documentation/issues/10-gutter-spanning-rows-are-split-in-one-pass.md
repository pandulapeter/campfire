# Cut a page at its full-width rows in one sorted pass instead of re-filtering every glyph per row
**Challenged:** amended — the timing test is resized so it is not flaky (measured by the challenge on HEAD: 9,000 rows 4.0 s against the plan's 2.5 s bound; 14,000 rows 6.6 s, while the same 14,000 rows without full-width lines, i.e. the post-fix cost, take 0.39 s, so 14,000 rows with a 3 s bound leaves a factor of two on either side), and the fix keeps plan 02's `charge` parameter. Equivalence checked: the slices partition exactly as the filters did, and `column()`'s stable sort by y makes the y-sorted input order indistinguishable from content order.

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractor.kt`, `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt`

## Problem
In `buildLines`, once a gutter is found, each full-width row (`spanning`) re-filters the whole remaining glyph list three times:

```kotlin
for (y in spanning) {
    result += columns(remaining.filter { it.y < y - size * 0.22 })
    result += column(remaining.filter { abs(it.y - y) <= size * 0.22 })
    remaining = remaining.filter { it.y > y + size * 0.22 }
}
```

so cost is spanning rows x glyphs, and `spanning` may be a fifth of all rows (that is the gutter test's own rule). No yield. Measured on
HEAD (probe: every sixth line a full-width 80-character line, the rest two 30-character columns, 10 pt Courier): 4,500 rows
(about 270,000 glyphs) 1.44 s, 9,000 rows (about 540,000) **3.8 s**; superlinear, so about 15 s near the 1,000,000 glyph cap on
the JVM, longer on a phone, with no cancellation point. Real songbooks are one page at a time (a few thousand glyphs), so this
only matters for a single huge page; low severity.

## Fix
Sort the glyphs by `y` once (`val sorted = glyphs.sortedBy { it.y }`; `column()` sorts again anyway), take `spanning` rows in ascending
order and keep one index `from`. For each `y`: `before = sorted.slice(from until lowerBound(y - size*0.22))` (never less than `from`),
`at` = the slice up to `upperBound(y + size*0.22)`, then `from` moves past it. This is the same partition as the filters because
the thresholds are monotonic in `y`; the glyphs between rows go to `columns(...)` exactly as before. Binary search on a
`DoubleArray` of the sorted `y`s (the file already has `countAtMost`/`countBelow` helpers for `List<Double>`; reuse or generalize them).
Do not change the ordering of `result`. `buildLines`, `columns` and `column` carry the `charge` parameter that
`02-monospace-gap-padding-is-charged-to-the-text-budget.md` added; keep passing it through.

## Tests
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt`: `manyFullWidthRowsSplitQuickly` generates the probe's content with **14,000** rows, 12 pt apart (every
sixth a full-width 80-character line at x = 50, the rest a 30-character column at x = 50 and another at x = 300,
10 pt Courier, about 890,000 glyphs, under `MAX_GLYPHS` and under the text budget with plan 02's padding; raise the
`/MediaBox` height to fit) and asserts `measureTime < 3.seconds`. Measured on HEAD: 6.6 s; the same rows without the
full-width lines (no spanning rows, which is what the one-pass split costs) 0.39 s. The correctness regression is
`DocumentGoldenTest` (the `campfire-columns*.pdf`, `two-column.pdf` goldens): they must be unchanged.

## Manual check
None.
