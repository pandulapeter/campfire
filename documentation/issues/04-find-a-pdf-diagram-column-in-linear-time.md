# Find a PDF's column of chord diagrams without re-reading the page at every gap

**Challenged:** amended — the recommended option ("per-row prefix data", "stop after the first failing row") was not
implementable as written: each try first reads every row of the *other* side for the song's type size, and a side's rows
are grouped afresh from its own glyphs, so neither a shared row list nor an early exit makes a try cheap. Replaced with a
cap that leaves every real page's result unchanged (try at most the 8 gaps nearest each edge, in today's order), and the
test now names the module's `assertTakesTimeInProportion`.

**Kind:** performance (hostile input)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/formats/src/commonMain/kotlin/com/pandulapeter/campfire/data/formats/document/PdfChordDiagrams.kt`,
`data/formats/src/commonTest/kotlin/com/pandulapeter/campfire/data/formats/document/PdfChordDiagramsTest.kt`,
`data/formats/CLAUDE.md` (its diagram-column bullet, if the cap is worth a clause)

## Problem

`aecebb4a6` (after 5.6.0) tries every vertical band of two ems that no glyph crosses as the edge of a diagram column,
and for each one partitions the page and reads both halves' rows (`isDiagramColumn` → `inks` → `rows`, a sort each):

```kotlin
var reach = ink.first().span.end
for (glyph in ink.drop(1)) {
    if (glyph.span.start - reach > em * GUTTER) {
        val (left, right) = glyphs.partition { it.span.start < glyph.span.start }
        if (isDiagramColumn(right, besides = left)) return left
        if (isDiagramColumn(left, besides = right)) return right
    }
    reach = maxOf(reach, glyph.span.end)
}
```

`reach` is the right edge of everything left of the glyph, over all rows, so a gap is a band empty on every row; `em` is
the page's median glyph size. A real page has a handful (a single diagram's marks sit closer than two ems; diagrams side
by side and the song's own columns add one each). A hostile page has one per glyph: glyphs of a tiny median size spaced
more than two of their ems apart, which nothing clips (the content interpreter keeps glyphs at any size and position).
Each try costs O(n log n) whatever the outcome, since `isDiagramColumn` sorts the other side's rows before looking at
the column, so 100k glyphs is ~10¹⁰ steps, a hung import.

## Fix

1. Collect the gaps first: walk `ink` as now and record each gap glyph's `span.start` in a list, without trying it.
2. If there are more than `2 * EDGE_GAPS` (`EDGE_GAPS = 8`, a named constant with a KDoc saying why), keep only the
   first `EDGE_GAPS` and the last `EDGE_GAPS`.
3. Try the kept gaps left to right exactly as today (right side first, then left, returning on the first success).

A page with 16 gaps or fewer, every real page and every existing test, is tried exactly as before. A diagram column
hangs off one edge, so its edge is among the gaps nearest that edge; it is passed over only if more than eight bands
separate diagrams within the column, in which case a narrower column (its outer diagrams) is still found. Work is at
most 32 tries of O(n log n). Do not try only the outermost gap on each side: diagrams side by side have inner gaps, and
the column's edge is the innermost that holds.

## Tests

- The existing `PdfChordDiagramsTest` cases still pass.
- New, with `assertTakesTimeInProportion` (as `a row of one long word takes time in proportion to its length` uses it):
  one row of `wholeSize = 64_000` one-glyph words, `Span("a", it * 3.0, it * 3.0 + 0.6, 1.0)` (1 pt type, each three
  points apart: a gap per glyph), all of which `withoutDiagrams` keeps. Confirm it fails on the current code before the
  fix.
