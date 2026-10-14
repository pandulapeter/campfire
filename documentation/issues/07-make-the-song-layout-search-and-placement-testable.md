<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Take the song layout's grid search and placement out of the measure lambda into pure code tested with fake measurements

**Kind:** testability (behaviour-preserving refactor) · **Severity:** medium · **Platforms:** all
**Challenged:** amended — the class shape exposes what the placement still reads (`units`, `unitsOf`, the extension
`columnWidthOf`) and the layout keeps its own `unitCount`/`sectionCount`; `SongGridSearch.kt` is ~490 lines as
specified, so a fallback file is named (the size check cannot add an allowance); two tests rewritten that would fail
against today's code (a one-column search does measure heights, for its page count; a one-row song may take more
columns than the fewest, `balancedAmong`). The dual-run and measure-lambda analysis held: nothing captured is lost.
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongSectionsLayout.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongGridSearch.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionPlacement.kt` (new),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/FakeSectionMetrics.kt` (new),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongGridSearchTest.kt` (new),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SectionPlacementTest.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`,
`.github/scripts/size_allowances.json`

The riskiest plan of the review: this code is what keeps the pedal promise (every line reachable with Up and Down), and
it has no test above its building blocks. Land it after plan 06 and before plan 11 (which moves these files, new ones
included, to `songView.grid`).

## Problem

`SongSectionsLayout.kt` is 842 lines, and `SongSectionsLayout` one top-level function of 668 (allowances 842 and 668,
against limits of 500 and 120). Nearly all of it is one `Layout { allMeasurables, constraints -> … }` lambda that does
two pure jobs inside the measure pass, where no test can reach them:

1. **The search** for the grid: `widthColumnCountFor`, `narrowColumnCountFor`, `columnWidthFor`, `unitWidthFor`,
   `wideWidthFor`, `isNarrowSection`, `SectionGrid.columnWidthOf`, then `searchSegment` (≈210 lines of local functions:
   the single-column paging, `searchColumnCount`, the magazine flow, narrow columns), `searchGrid` (one search per tempo
   stretch) and `searchFitted` (the fit scales), run from

   ```kotlin
   val decidedGrid = sectionMeasurements.grid(gridKey, canFitText) { canFit ->
       val insetSearch = if (endInsetPx > 0) searchFitted(settledWidth - endInsetPx, canFit) else null
       when { … }
   }
   ```

   Every input is pixels, flags, `SongUnits` (already a plain class) and three measurements per unit, which reach the
   search only through `measurables`:

   ```kotlin
   sectionMeasurements.height(index = index, width = width, measure = measurables[index]::maxIntrinsicHeight)
   sectionMeasurements.minWidth(it, measurables[it]::minIntrinsicWidth)
   sectionMeasurements.maxWidth(unit, measurables[unit]::maxIntrinsicWidth)
   ```

2. **The placement** of the decided grid: the column, cell and unit widths (`columnWidths`, `cellWidths` with the
   `sharesKeyline` pass, `unitWidths`), whether the song is scrolled by row, `spacedEvenly` / `startsOf` / the keyline
   block (`cellStarts`), the row viewport arithmetic (`readableRowHeight`, `wholeSongHeight`), `grid.arrange(…)`, the
   positions, the card bounds, and the `SongRows` handed to `onRowsPlaced`. All of it is arithmetic over the grid, the
   measured unit heights and the divider height.

The building blocks (`flowIntoRows`, `flowLikeAMagazine`, `flowIntoPages`, `searchColumnCount`, `arrange`, `score`,
`chooseFitScale`, `withNarrowColumns`) are tested; the orchestration that decides which of them runs, at which widths,
and how their results are combined (the inset decision, stretches, fitting, the songbook cut-off, the wide rows, the
centering) is not, and a refactor or a tweak of it is checked today only by eye.

## Fix

Two moves, each verified by a temporary dual run before it is committed, then the tests. Every move is verbatim (the
`code-style` skill's rule for a behaviour-preserving split), apart from the seams named here; check each with
`git diff --color-moved=dimmed-zebra`.

### Commit 1 — the search: `SongGridSearch.kt`

```kotlin
/** What the search knows of a unit of the song: its size at the reader's own text size, measured once and kept. */
internal interface SectionMetrics {
    /** The height of the unit at [index] laid out [width] wide. */
    fun height(index: Int, width: Int): Int
    /** The narrowest the unit at [index] can be laid out without breaking a line that does not wrap. */
    fun minWidth(index: Int): Int
    /** The width of the widest line of the unit at [index], unwrapped. */
    fun maxWidth(index: Int): Int
}

/** Every dimension and switch the search is decided by, in pixels. */
internal data class SongGridSpec(
    val minColumnWidth: Int, val narrowColumnWidth: Int, val maxColumnWidth: Int,
    val columnGap: Int, val sectionGap: Int, val rowGap: Int,
    val availableHeight: Int, val maxRowHeight: Int, val endInset: Int, val keepsEndInset: Boolean,
    val cardPadding: Int, val lyricsLineHeight: Int, val fitScales: List<Float>,
    val canCutSections: Boolean, val isSingleColumn: Boolean,
)

internal class SongGridSearch(val units: SongUnits, private val metrics: SectionMetrics, private val spec: SongGridSpec) {
    fun gridKey(settledWidth: Int): SectionGridKey
    fun decide(settledWidth: Int, canFit: Boolean): DecidedGrid
    fun SectionGrid.columnWidthOf(unit: Int, totalWidth: Int, fitScale: Float): Int  // stays an extension: searchFitted calls it so
    fun unitWidthFor(unit: Int, columnWidth: Int, fitScale: Float): Int
    fun maxWidthOf(unit: Int): Int
    fun unitsOf(section: Int): IntRange  // the placement's unitWidths reads it
    val piecePadding: IntArray
    // private: widthColumnCountFor, narrowColumnCountFor, columnWidthFor, wideWidthFor, isNarrowSection,
    // searchSegment, searchGrid, searchFitted
}
```

(The trailing-comma and one-declaration-per-line style of the real file, of course; the block above is the shape.)

- Move into the class, verbatim, the lambda's lines from `val unitCount = units.unitSections.size` /
  `val sectionCount = …` and `piecePadding` through `searchFitted`, and the body of the `sectionMeasurements.grid { }`
  lambda as `decide`; with them `SegmentLayout`, `FittedLayout`, `Int.scaledBy`, `measuredWidthOf` and
  `MAX_CUT_SECTION_COUNT` (file-private in the new file; `scaledBy` and `measuredWidthOf` become `internal` because the
  placement and the layout use them too).
- The seams, and only these change: every `x.roundToPx()` becomes the matching `spec` field (rounded once in the
  layout; `minColumnWidth.roundToPx()` was rounded on every call to the same value); the three measurement calls become
  `metrics.height(index, width)`, `metrics.minWidth(i)`, `metrics.maxWidth(i)`; `maxRowHeight`'s
  `if (isSpecified && > 0.dp) … else Int.MAX_VALUE` and `availableHeight`'s `isSpecified` stay in the layout, which
  hands the search the resulting ints.
- The layout still needs `unitCount` (to split `allMeasurables`, before anything else) and `sectionCount`
  (`onRowsPlaced`'s step offsets): it keeps its own two lines for them, and the class its own copies. The placement
  code left in the layout until commit 2 calls `grid.columnWidthOf(…)` inside `with(search) { … }` and
  `search.unitsOf`, `search.unitWidthFor`, `search.maxWidthOf`, `search.piecePadding`.
- **Size**: the moved lines (≈350), the moved KDoc (≈40), `SegmentLayout`, `FittedLayout`, the two helpers, the
  constant, and the interface and spec written one declaration per line put `SongGridSearch.kt` at about 490 of its
  500 lines, and `check_sizes.py --update` never adds an allowance for a new file. If it is over, move `SectionMetrics`
  and `SongGridSpec` to `SongGridSpec.kt` (same package, same commit) rather than trimming the moved code.
- **Keep the shadowing exactly as it is**: `searchSegment` has parameters and locals named like the outer values
  (`availableHeightPx`, `sectionCount`, `unitCount`, `piecePadding`), and `searchGrid` passes `maxRowHeightPx` as a
  stretch's `availableHeightPx`. Rename nothing inside the moved code.
- The layout keeps, in the measure lambda: the split of `allMeasurables`, the spec and an adapter
  `object : SectionMetrics { override fun height(index: Int, width: Int) = sectionMeasurements.height(index, width,
  measurables[index]::maxIntrinsicHeight) … }` built **per pass** (measurables are only valid inside it, so neither the
  adapter nor the search may be remembered), `val decidedGrid = sectionMeasurements.grid(search.gridKey(settledWidth),
  canFitText) { canFit -> search.decide(settledWidth, canFit) }`, and the `fitDeferral` line.
- The composable's KDoc describes both jobs; move its paragraphs about the search (columns as wide as possible, the
  magazine, narrow columns and fit scales, the candidate counts and the single-column shortcut, the inset decision) to
  `SongGridSearch`'s KDoc verbatim, and keep the rest (rows read across, glides, dividers, viewport space,
  `onRowsPlaced`, `lyricsLineHeight`) on the composable.

### Commit 2 — the placement: `SectionPlacement.kt`

- `internal class SectionWidths(val columnWidths: IntArray, val cellWidths: Array<IntArray>, val unitWidths: IntArray)`
  and `internal fun SongGridSearch.sectionWidthsOf(decided: DecidedGrid, layoutWidth: Int): SectionWidths`: the lines
  from `val columnWidths = …` through `val unitWidths = …` (they run before the units are measured).
- `internal fun placeSections(…): SectionPlacement` taking the decided grid, the widths, the measured `unitHeights`
  (already `scaledBy(fitScale)`), the divider height, the units, `piecePadding`, the pixel values (width, layout width,
  `isInset`, end inset, gaps, available height, `rowViewportHeight` as `Int?` for unspecified, its top and bottom
  paddings), `layoutDirection`, `lineHeightPx`, and the held spreads and page shifts the layout read from
  `centeringHold`; returning `isScrolledByRow`, `isPaddedToViewport`, the `SongArrangement`, `positions` and `cards`.
  It holds `isScrolledByRow` through the arrangement and positions. Split it so no top-level function passes 120 lines:
  `cellStartsOf(…)` (`spacedEvenly`, `startsOf`, the keyline block) and `cardBoundsOf(…)` (the card loop) are their
  own `internal` functions in the file.
- `internal fun songRowsOf(…): SongRows`, the body of the `onRowsPlaced(SongRows(…))` call with `fadePx`
  (`EDGE_FADE_SIZE.roundToPx()`) passed in; still called **only** `if (isLookingAhead)`, as today, so a frame of the
  approach pass allocates nothing more than before.
- Stays in the layout: measuring the placeables (it needs `Constraints`, `layoutId`, `animations`), measuring the
  dividers (their count is `grid.pageCount - 1`, known before the placement, so the order is unchanged: widths →
  placeables → divider placeables → placement), the `centeringHold` record, `onRowsPlaced`, the card placeables,
  `sectionGlides.follow`, and the `layout { }` block. Aim for `SongSectionsLayout` ≤ 120 lines; whatever it ends at,
  run `python3 .github/scripts/check_sizes.py --update` in this commit (the file falls under 500 and loses its
  allowance; the function's shrinks or goes), and in commit 1 too.

### The dual run (how each commit is checked, never committed)

For each commit, before deleting the old code, keep the HEAD version of what moved in the lambda under a `legacy`
name and compare on every pass, so the old and the new code read the same cached measurements:

- Commit 1: inside the `sectionMeasurements.grid { canFit -> }` lambda compute both and
  `check(legacy.hasSameCellsAs(new) && legacy.isFitDeferred == new.isFitDeferred) { "grid differs: …" }`
  (`hasSameCellsAs` compares `isInset`, `fitScale` and every array of the grid).
- Commit 2: compare `positions`, `unitWidths`, `cards`, the arrangement's `tops`, `height`, `dividerTops`,
  `pageBottoms`, `columnSpreads`, `pageShifts`, and, on lookahead passes, the `SongRows` (a data class).

Exercise it with: `./gradlew :tools:screenshots:run` (every platform's song details shots, offscreen); the desktop app
(`:app:desktop:run`, see the desktop verification notes) on the demo songs and a few long ones (a tab-heavy song, one
with `{tempo}` changes, a songbook of over 200 sections), at window widths of about 360, 600, 900 and 1,400 dp, at the
smallest and largest text size and while pinching, with a section folded, with the metronome panel opening, inside a
setlist (`keepsStepButtonInset`) and in the editor's preview (`canCutSections` false); and the whole `desktopTest`
suite. A failed `check` throws out of the measure pass with its message (on the desktop also kept in `campfire.log`);
fix the move, never the check. Remove the legacy code and the checks,
then commit.

Option considered and not recommended: record the inputs and measurements of these runs as fixtures and replay them in
a test forever. It would pin today's choices, so every intended layout change would rewrite fixtures nobody can read;
the dual run proves this refactor, and the tests below pin the promises instead.

### On the pedal coverage test (`documentation/plans/pedal-coverage-test.md`)

**Do not wait for it.** It needs the UI test harness (`documentation/plans/ui-test-harness.md`, not built yet, a day or
more), and it checks that every line stays reachable, not that the layout is unchanged — a refactor could move every
section and still pass it. The dual run checks the stronger property on every layout it reaches. If the harness and the
pedal walk have landed by the time this plan runs, run the walk before commit 1 and after commit 2 as well.

### Commit 3 — the tests (below) and the docs

`screens/songDetails/CLAUDE.md`, Layout heading: name `SongGridSearch.kt` and `SectionPlacement.kt` beside
`SongSectionsLayout`, and add one sentence: "The search and the placement are pure (`SongGridSearch` over
`SectionMetrics`, `placeSections`); the layout only measures and places." The file is over budget on an allowance: cut
as many words elsewhere in it as this adds.

## Tests

`FakeSectionMetrics` (test fixture beside `SectionGridFixtures.kt`): each unit is a list of line widths in pixels;
`height(unit, width)` is the sum over its lines of `ceil(lineWidth / width) * LINE_HEIGHT / 2` (a lyric line of the
fixtures' chorded `LINE_HEIGHT`), `maxWidth` the widest line, `minWidth` the widest line for a unit marked as tablature
and its longest word (a fixed fraction) otherwise; it counts `height` calls. A helper builds `SongUnits` directly from its
constructor (per-section unit counts, `isCuttableBefore`, `lineCount`, `timingStarts`), and a `spec(…)` helper fills
`SongGridSpec` with the screen's proportions (columns 384–560, narrow 288, gaps as the fixtures'), a finite
`maxRowHeight` (`searchFitted` does not fit where it is `Int.MAX_VALUE`) and `fitScales = listOf(0.975f, 0.95f)` unless
a test says otherwise.

`SongGridSearchTest` — one test per promise of the KDoc, asserting properties rather than whole grids:

- `a song that fits one row takes the fewest columns that hold it` (`columnCounts` has one row; one column fewer would
  not fit `availableHeight`), with a fixture whose columns fill the height as they fill the width, since `balancedAmong`
  may take more columns where they fill the page more evenly by `BALANCE_MARGIN`; a second test pins that: a short,
  wide song on a wide screen takes more than the fewest, and never more lines wrapped than the fewest.
- `a window with room for one column that is not cut searches no other count` (`canCutSections = false`, empty
  `fitScales` as `SongLyrics` passes there, narrow width: every row has one column, and every `height` call is at the
  single column's width — or a card's narrower one — never at another). Not "no height at all": `searched.laidOut()`
  takes `grid.pageCount()`, which measures every unit once at that width, and the KDoc's "asks for none of them" means
  the candidate counts.
- `the editor's preview never cuts a section` (every unit of a section in one cell).
- `a song too long for one row is cut like a magazine, in no more pages than with its sections whole`.
- `every stretch of a song that changes its tempo starts a page` (`timingStarts = [0, k]`: the row of section `k`'s
  first unit `startsPage`).
- `a songbook with tempo changes is one column` (over 200 sections, two stretches).
- `a staff wider than a column gets a row as wide as it` (`wideRows` has a true).
- `narrow columns are taken only where they save a page`.
- `a song a few lines over is fitted only once fitting is allowed` (`canFit = false`: `fitScale == 1f`,
  `isFitDeferred`; `canFit = true`: `fitScale == 0.975f`, a page fewer).
- `the step buttons' edge is kept only where the song scrolls` (`endInset > 0`: a short song `isInset == false`, a long
  one `true`, and `keepsEndInset` always `true`).

`SectionPlacementTest`:

- `the columns of a row are spread evenly across the width`, and mirrored in `LayoutDirection.Rtl`.
- `an inset row keeps out of the step buttons' edge only where it would reach under them`.
- `the pages of a single column share one start edge` (`sharesKeyline`), and a pair of narrow sections puts the second
  at the middle of the block.
- `a card cut across two columns is two cards, each padded at the cut`.
- `a song stepped by its sections stops a fade above each one` (`songRowsOf`: `stepOffsets[i] == tops[start of i] - fadePx`)
  and `a song read in rows rests at the top and under every divider`.

Verification: the root `CLAUDE.md` test command, `:app:desktop:compileKotlin`, `:app:android:compileDebugKotlin`,
`:app:web:compileKotlinWasmJs`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`, `python3 .github/scripts/check_sizes.py`.
