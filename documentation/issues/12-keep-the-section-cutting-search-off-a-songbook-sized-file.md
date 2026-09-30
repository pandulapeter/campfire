# Keep the section-cutting search off a songbook-sized file, and look a unit's section up in constant time

**Challenged:** amended — one detail only: `sectionOf` is called inside `rowOf` alone (its two uses at `SectionCutting.kt`
lines 156 and 162), not in `fill` or `lowestCap`; the step now names those. The rest holds: `sectionCount` in
`searchGrid` counts sections (`units.sectionStarts.size - 1`), not chunks; a song past the cap keeps its uncut grid, in
which a section taller than the screen has a single-column row of its own and is paged through line by line
(`SongRows.lineTops` lists it), so the pedal still reaches every line; and the pool of sizes is keyed on the font scale
(`sectionSizesPool`), so a pinch does search again on every frame, as the plan says.

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** desktop, web, tablets (cutting needs two or more columns)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt` (`searchGrid`),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionCutting.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionCuttingTest.kt`,
`presentation/CLAUDE.md` where it describes the cutting

## Problem

`searchGrid` in `SongLyrics.kt` (at 9ab7ca54e) tries, for every song that scrolls on a wide screen, to cut its sections
into side-by-side columns, twice:

```kotlin
cutGrid(cutsEverySection = true)?.let { grid -> if (grid.height() <= availableHeightPx) return SearchedGrid(grid, fits = true) }
return cutGrid(cutsEverySection = false)?.let { grid -> SearchedGrid(grid, fits = false) } ?: searched
```

`flowIntoRowsCuttingSections` (`SectionCutting.kt`) is superlinear in the number of sections: `rowOf` calls
`sectionOf(sectionStarts, unit)` per unit,

```kotlin
return Triple(IntArray(units) { unit -> sectionCells[sectionOf(sectionStarts, sectionStarts[start] + unit) - start] }, whole, false)
```

and `sectionOf` (line 234) scans `sectionStarts` from the front. Measured in a throwaway probe of the verbatim
functions (JVM, warm): 50 sections 20 ms, 250 sections 50–65 ms, 500 sections 107–146 ms, 1 000 sections (3 000
units, which is `LayoutBudget.MAX_LINES`) 275 ms for the oversized-only pass plus 465 ms for the every-section pass:
0.74 s per search on the main thread, on top of `flowIntoRows`. A font scale change makes a new `SectionMeasurements`,
so the search runs again on every frame of a pinch; a phone is three to five times slower. A real song of 20–60
sections costs single-digit milliseconds, so this only hurts the songbook-sized files `LayoutBudget` exists for — but
those are exactly the ones that reach the budget without being cut off, and a pinch on one freezes the screen.

The live run measured it on the desktop build: with a 300-section song in the setlist, 1400 × 800 and text size 0.5,
the UI froze for 3.1 s opening the song before it, 3.0 s opening it, 3.9 s opening the song after it, and 4.2 s on
the Up hand-off into it — every time the song was composed as the current page or as a neighbour
(`beyondViewportPageCount = 1`). A pedal press during those seconds waits. At text size 1.0 the same song cost 405 ms,
and the peak JVM RSS of the run was 878 MB. The grid search for many columns (`gridFor` per candidate, then the two
cutting passes) is what runs on the main thread there, so the cap below is the first thing to try; if a 300-section
song still freezes for more than a second after it, note the measurement in the lane report rather than reaching
further.

## Fix

Two changes, both in the pure code:

1. In `SectionCutting.kt`, look a unit's section up from a precomputed table rather than a scan: `flowIntoRowsCuttingSections`
   already receives `sectionStarts`; build `val unitSections = IntArray(unitCount)` once at the top (fill by walking
   `sectionStarts`) and read it in `rowOf`, the one place that calls `sectionOf(sectionStarts, …)` (twice: the balanced cells of an
   uncut row, and the `isCut` check).
   Keep the public `sectionOf` for its other callers, or have it take the table.
2. In `searchGrid`, do not search cuts at all past a size no song has: `if (sectionCount > MAX_CUT_SECTION_COUNT) return searched`
   next to the existing `!canCutSections` check, with `private const val MAX_CUT_SECTION_COUNT = 200` in `SongLyrics.kt`
   and a comment saying why (a file of that many sections is a songbook, is read by paging through it, and would be
   searched again on every pinch frame). The pedal still reaches every line through the paging of tall sections.

Say in `presentation/CLAUDE.md`'s cutting sentence that a song of more than 200 sections is never cut.

## Tests

`SectionCuttingTest`: a 400-section input with a section taller than `maxRowHeight` is cut the same with the table as
before (run the existing cases; they are the pin). Add one timing-free case: 1 000 sections of three units, four
columns, `cutsEverySection = true`, asserting the invariants the existing tests assert (rows ascend, no section
crosses a row, every multi-column row is at most `maxRowHeight`), so that the table lookup is covered on a large input.
The `searchGrid` cap has no pure test; it is one comparison.

## Manual check

Desktop build: a generated `.cho` of 1 000 four-line sections, opened at 1400 × 800. Pinching on the touchpad (or
Ctrl + wheel) follows the fingers without freezing; before the change each frame took most of a second.
