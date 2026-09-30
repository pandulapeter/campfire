# Count the header when deciding whether a song leaves room for the step buttons

**Challenged:** amended — the plan checked the header against the *inset* search only, but where that fits the layout
takes a different grid, `searchGrid(settledWidth)`, which picks the fewest columns that fit the full width and can be
taller than the inset one (three columns 500 dp tall at the inset width, two columns 690 dp tall at the full width):
header plus that grid scrolls, and the buttons are back over the lines. The full-width grid is now held to the same
test, one row included. The header term is `headerHeight()` alone, which is what `isScrolledByRow` adds for a single row
(the only grid that reaches this test), rather than the plan's contradictory half gap. `headerHeight()` is a measured
value there (the header is measured first, in the same pass) and changes only when the header does, so the key costs one
search per header change.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** every build with room for two or more columns (desktop, web, tablets, large phones in landscape)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionGrid.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionGridTest.kt`,
`presentation/CLAUDE.md` where it describes the step buttons' inset

## Problem

The step buttons sit at the end edge of the screen, and a song that has to be scrolled is laid out narrower so that
they never cover a line. `SongLyrics.kt` (at 9ab7ca54e) decides that from whether the song *fits*:

```kotlin
val decidedGrid = sectionMeasurements.grid(gridKey) {
    // The buttons that step through the song sit at the end of the screen, so a song that has to be scrolled leaves
    // them that edge. One that fits the screen there has no buttons to leave room for, and is laid out across the
    // whole width, where it fits all the more. …
    val insetSearch = if (endInsetPx > 0) searchGrid(settledWidth - endInsetPx) else null
    when {
        insetSearch == null -> DecidedGrid(searchGrid(settledWidth).grid, isInset = false)
        keepsStepButtonInset || !insetSearch.fits || insetSearch.grid.columnCounts.size > 1 ->
            DecidedGrid(insetSearch.grid, isInset = true)
        else -> DecidedGrid(searchGrid(settledWidth).grid, isInset = false)
    }
}
```

`SearchedGrid.fits` is `grid.height() <= availableHeightPx`, the grid alone; the header (cover, tags, languages, links,
the key line) is not in it, since the rows are meant to have the whole screen. But whether the song scrolls, and so
whether the buttons are shown, does count the header:

```kotlin
val isScrolledByRow = (hasSeveralRows || grid.columnCounts.any { it > 1 }) && (
    headerHeight() + (if (hasRowsUnderHeader) rowGapPx / 2 else 0) +
        grid.arrange(unitHeights, sectionGapPx, rowGapPx, unitSections = units.unitSections, piecePadding = piecePadding).height > availableHeightPx ||
        hasSeveralRows && isOneRowAtATime && rowViewportHeight.isSpecified
    )
```

Scenario: a 1000 × 800 dp desktop window (about 696 dp available), a song that lays out as one row of two columns
650 dp tall, and a header of 200 dp (a cover, a few tags, a link). `fits` is true (650 ≤ 696) and there is one row, so
`isInset = false` and the two columns are spread over the whole width. `isScrolledByRow` is true (200 + 650 > 696), so
the song has stops, `canStepForward` is true, and the Next row button is drawn at the bottom end corner; after the first
step, Previous row at the top end corner. Where the end column is as wide as its cell — long lines, or a text size
that leaves no margin — the button covers the last 40 dp of the lines under it, which is exactly what the inset
exists to prevent. Short-lined songs are spared only because `spacedEvenly` leaves wide margins. The same happens
when the winning grid is a cut one that "fits once something is cut".

## Fix

Treat a grid as fitting only when it fits *under the header*, since that is when it has no buttons, and hold the
full-width grid the layout falls back to to the same test. In `SongLyrics.kt`:

1. Give `SearchedGrid` the height `searchGrid` worked out (`val height: Int`) at each of its construction sites: the
   `grid.height()` already computed there (computed once where the `fits` expression computes it), and `Int.MAX_VALUE`
   where `fits` is `false` without a height (the `availableHeightPx <= 0` branch), since only a grid that fits is ever
   asked for it.
2. Pull the test into `SectionGrid.kt` as a pure function so that it can be pinned:
   `internal fun fitsUnderHeader(fits: Boolean, rowCount: Int, headerHeight: Int, gridHeight: Int, availableHeight: Int) = fits && rowCount == 1 && headerHeight + gridHeight <= availableHeight`.
   `headerHeight` is `headerHeight()` as it is, with no half row gap: that is the term `isScrolledByRow` adds for a
   single row (`hasRowsUnderHeader` is false there), and a grid of several rows never reaches this test. Say so in its KDoc.
3. `decidedGrid` becomes:

   ```kotlin
   val insetSearch = if (endInsetPx > 0) searchGrid(settledWidth - endInsetPx) else null
   fun SearchedGrid.fitsUnderHeader() = fitsUnderHeader(fits, grid.columnCounts.size, headerHeight(), height, availableHeightPx)
   when {
       insetSearch == null -> DecidedGrid(searchGrid(settledWidth).grid, isInset = false)
       keepsStepButtonInset || !insetSearch.fitsUnderHeader() -> DecidedGrid(insetSearch.grid, isInset = true)
       else -> searchGrid(settledWidth).let { full ->
           if (full.fitsUnderHeader()) DecidedGrid(full.grid, isInset = false) else DecidedGrid(insetSearch.grid, isInset = true)
       }
   }
   ```

   The last line is the case the plan missed: the inset grid fits under the header and does not scroll, so taking it
   costs nothing but the width the buttons would have had; the full-width grid would scroll with its buttons over it.
4. `SectionGridKey` gains `val headerHeight: Int` (`headerHeight()` where the key is built): without it
   `SectionMeasurements.grid(gridKey)` keeps the old decision when a tag, a link or a cover is added to the header. The
   header has no animation of its own, so its height changes once per change of what it shows, and on every frame of a
   pinch, which already makes a new `SectionMeasurements` per frame.

Update the comment above `decidedGrid` ("One that fits the screen under its header has no buttons to leave room for")
and the sentence in `presentation/CLAUDE.md` that describes the inset.

## Tests

`SectionGridTest`: `fitsUnderHeader` with header 200, grid 650, available 696 → false; header 0 → true; header 40,
grid 650 → true; two rows, everything fitting → false; `fits = false` → false. The decision in `decidedGrid` is
Compose-side and pinned by the manual check.

## Manual check

Desktop build at 1000 × 800: a song with a cover, three tags and a link whose text, at a text size that makes two
columns about 480 dp wide with long lines, is one row of two columns just short of the window's height. The Next row
button no longer sits over the end of any line; a song without the header still uses the whole width. Then a
window where that song takes three columns at the inset width and two at the full one: no button over a line either.
