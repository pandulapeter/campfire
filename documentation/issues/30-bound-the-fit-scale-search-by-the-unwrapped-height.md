# Bound the fit-scale search by the song's unwrapped height, so a scale that unwraps lines and saves a page is not skipped

**Challenged:** amended — the bound now reads each unit at the plain layout's own width where nothing wrapped there (a cache hit) rather than measuring every unit again at its widest on every pinch frame, and counts a section `flowIntoPages` may pair beside another at half, since a one-column page holds two of them side by side and the bound otherwise still rejects page-saving scales; the pure function takes section heights and a pairable test, and gains a pair test that fails without the halving.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongSectionsLayout.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/TextFitting.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/TextFittingTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`

## Problem

`searchFitted` (`SongSectionsLayout.kt` ~L428–442 at d650f3cb0) only lays a song out at a fit scale where it "could fit
one page fewer even with no gap between two sections":

```kotlin
val plain = searchGrid(totalWidth, fitScale = 1f)
...
val pageCapacity = narrowColumnCountFor(totalWidth).toLong() * maxRowHeightPx
val fitted = HashMap<Float, SegmentLayout>()
val fitScale = chooseFitScale(plain.pageCount, fitScales) { scale ->
    if (plain.contentHeight * scale > (plain.pageCount - 1) * pageCapacity) {
        null
    } else {
        searchGrid(totalWidth, scale).also { fitted[scale] = it }.pageCount
    }
}
```

`plain.contentHeight` is not a lower bound of the fitted height. It is built in `SearchedGrid.laidOut` (~L318):

```kotlin
fun SearchedGrid.laidOut(pageCount: Int = grid.pageCount()) = SegmentLayout(this, pageCount, grid.unitHeights().sumOf { it.toLong() })
```

`unitHeights()` is every unit's height at the plain grid's own column widths at scale 1 (`unitHeightAt(it, unitColumnWidth(it))`),
so every line that wraps in those columns is counted wrapped. At a fit scale `unitHeightAt` measures the text at
`measuredWidthOf(width, fitScale)` = `ceil(width / fitScale)` — 2.6% wider at 0.975 and 5.3% wider at 0.95 — and scales the
height down, so a line that overflowed its column by less than that stops wrapping, and a chorded line that stops wrapping
loses two lines' height. The real fitted height can therefore be much lower than `contentHeight * scale`, and the check
throws away exactly the scales the feature is for. The `SegmentLayout` KDoc (~L713) says the opposite ("how tall all of its
units are together, which is the least room any setting of it could take"), which is how the bug got in. The plain grid
may also be one of narrow columns that wrap more than the grid a fit scale would win with, which makes the bound looser
still.

Scenario: a 360 × 640dp phone (one column: `NARROW_COLUMN_WIDTH` is 288dp, so `narrowColumnCountFor` is 1), about 21 lines
to a page. A song of 17 written lines, three of them chorded lines that wrap by one short word: those add 6 lines, 23 in
all, 2 pages. At 0.95 the check reads 23 × 0.95 = 21.85 > 21 and skips the scale; laid out at 0.95 the three lines no longer
wrap and the song is about 17 × 0.95 ≈ 16 lines, one page. The reader steps through two pages where the designed behavior
was one.

## Fix

Bound with each unit's **unwrapped** height — its intrinsic height at a width where nothing in it wraps, which no
column it could be set in makes lower — times the scale, and count a section that may be set beside another at half its
height, since a page of one column holds two such sections side by side.

1. In `searchFitted`, past the existing early return, compute each section's unwrapped height once, from the plain
   layout's own widths so that it costs (almost) no new text measurement on the per-frame pinch path:

   ```kotlin
   val plainGrid = plain.searched.grid
   val unwrappedSectionHeights = LongArray(sectionCount)
   for (unit in 0 until unitCount) {
       // The width the plain layout measured the unit at, where nothing in it wrapped there: the same key, so a cache hit.
       // Its own widest line where something did, which wrappedLinesAt has measured already wherever the grid was scored.
       val measuredWidth = unitWidthFor(unit, plainGrid.columnWidthOf(unit, totalWidth, fitScale = 1f), fitScale = 1f)
       unwrappedSectionHeights[units.unitSections[unit]] +=
           sectionMeasurements.height(index = unit, width = maxOf(measuredWidth, maxWidthOf(unit)), measure = measurables[unit]::maxIntrinsicHeight)
   }
   ```

   A text's intrinsic height is the same at every width from its maximum intrinsic width up (nothing wraps), so
   `maxOf(measuredWidth, maxWidthOf(unit))` is the unwrapped height whichever it picks; it is only chosen this way so
   that the key is one `sectionMeasurements` already holds. Measuring every unit at `maxWidthOf(unit)` instead (the
   first version of this plan) is equally correct but lays out every unit that does not wrap once more, on every frame
   of a pinch (a pinch builds new section sizes each frame), which the old `contentHeight` never did. If the plain
   grid's widths ever differ from the ones its segment measured at (a timing stretch, a narrow-column grid), the cost
   is one extra measurement of that unit, never a wrong bound.

   Why it is a true lower bound: at scale `s` every unit is measured at some width (`ceil(columnWidth / s)`, a card's
   width, a wide row's width, half a column for a pair), and a text's intrinsic height at any width is at least its
   height where nothing wraps; the drawn height is that times `s` (`scaledBy` rounds each unit, so allow for half a pixel
   per unit — see 2); section gaps, card padding, and row gaps only add; and a page holds at most
   `narrowColumnCountFor(totalWidth)` columns of `maxRowHeightPx` (`pageCapacity`), **except** that `flowIntoPages`
   (the one-column paged path, a phone held upright) sets two `isNarrowSection` sections side by side in one column, as
   tall as the taller of the two (`pairHeights`). Such a pair takes at least half of the two sections' heights together,
   so each section that `isNarrowSection(section, columnWidthFor(totalWidth, 2), scale)` says could be paired counts at
   half. Without that, a song whose page saving comes from pairing chord-only sections is rejected (the old bound had
   the same hole). Counting every narrow section at half on every path is looser than needed, never wrong. A timing
   stretch starting a page of its own only makes more pages.

2. Extract the test as a pure function in `TextFitting.kt`, next to `chooseFitScale`, so it can be unit tested:

   ```kotlin
   /**
    * Whether a song whose sections are [sectionHeights] tall where nothing in them wraps could take fewer than
    * [pageCount] pages of [pageCapacity] when set at [fitScale] - its text laid out that much wider can only wrap less,
    * never less than not at all - so that a scale which cannot save a page is not laid out on every frame of a pinch.
    * A section [isPairable] may be set beside another, as tall as the taller of the two, so it counts at half; and
    * [unitCount] allows for each unit's height being rounded on its own.
    */
   internal fun couldSaveAPage(
       sectionHeights: LongArray,
       isPairable: (section: Int) -> Boolean,
       unitCount: Int,
       fitScale: Float,
       pageCount: Int,
       pageCapacity: Long,
   ): Boolean {
       val height = sectionHeights.indices.sumOf { if (isPairable(it)) sectionHeights[it] / 2.0 else sectionHeights[it].toDouble() }
       return height * fitScale - unitCount <= (pageCount - 1).toDouble() * pageCapacity
   }
   ```

   (Name and exact rounding allowance are the executor's choice; the allowance must keep it a lower bound, never make it
   tighter. Do the arithmetic in `Double`: a `Long * Float` is a `Float`.) `searchFitted` then reads, with
   `val halfColumnWidth = columnWidthFor(totalWidth, 2)` computed once:
   `if (!couldSaveAPage(unwrappedSectionHeights, { isNarrowSection(it, halfColumnWidth, scale) }, unitCount, scale, plain.pageCount, pageCapacity)) null else searchGrid(...)...`.
   Per scale this is a sum over the sections of cached values, nothing measured.

3. `SegmentLayout.contentHeight` is then unused (its only reader is `searchFitted`): remove the field, its computation in
   `SearchedGrid.laidOut` (~L318: `grid.unitHeights().sumOf { it.toLong() }`), and the
   `contentHeight = segments.sumOf { it.contentHeight }` in `searchGrid` (~L423), and drop the "how tall all of its units
   are together…" clause from its KDoc. Reword the comment above `searchFitted` (~L425–428: "A scale is only searched at
   where the song could fit one page fewer even with no gap between two sections…") to say the bound is the song's
   height with no line wrapped, no gap, and two narrow sections side by side.

4. `ui/screens/songDetails/CLAUDE.md` (~L354–356) says "searched only where the song could fit a page fewer even without
   its gaps"; make it "searched only where the song could fit a page fewer even with none of its lines wrapped, none of
   its gaps, and every narrow section beside another". Match it by its text; plan 46 edits a later sentence of the same
   paragraph ("the stepper, the menu and the app bar…"), which is not this one.

## Tests

In `TextFittingTest.kt`, for the extracted function (a line is any fixed pixel height, e.g. 100):

- the issue's scenario: one section of 17 unwrapped lines, not pairable, `pageCapacity` of 21 lines, `pageCount = 2`,
  scale 0.95 → true. Say in a comment that the old bound read the wrapped 23 lines (23 × 0.95 > 21) and would have been
  false: which height is passed in is decided in the layout and needs Compose text measurement, so this test pins the
  comparison, not that choice.
- a song a whole page over (unwrapped 50 lines, capacity 21 lines, `pageCount = 3`) at 0.95 → false (47.5 > 42);
- the boundary: an unwrapped height exactly `(pageCount - 1) * pageCapacity / fitScale` → true (a lower bound may not
  reject a scale that could fit exactly);
- pairs: two sections of 12 lines each, capacity 14 lines, `pageCount = 2`, scale 0.975 → true where both are pairable
  (11.7 ≤ 14) and false where neither is (23.4 > 14). The pairable case fails without the halving.

The layout itself (whether a scale really unwraps a line) needs Compose text measurement and is not unit tested.

## Manual check

On a 360 × 640dp phone (the smallest-screen AVD), open a song a couple of lines over one page whose overflow comes from
two or three chorded lines that wrap by a single short word: it should now open on one page, drawn slightly smaller,
with no step buttons. Pinch the text size up and down across that point and check the pinch stays smooth and the fitted
and unfitted settings swap at the expected sizes. A song a whole page too long still shows its pages at full size.
