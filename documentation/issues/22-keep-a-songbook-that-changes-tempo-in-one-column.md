# Keep a songbook of more than 200 sections that changes its tempo or time in one column on a wide window

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all (seen on wide windows: desktop, web, tablets)
**Challenged:** amended — the test stays `units.timingStarts.size > 1` after plan 20 rather than "any section is a `Timing`": a change before the first line is in force from section 0 in every layout, so it needs no single column; land after 20.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`,
`presentation/CLAUDE.md`, `CLAUDE.md` (Metronome section, wording only)

## Problem

The root `CLAUDE.md` (Metronome, "A later `{tempo}` or `{time}` is a change from where it stands") says "a songbook of
more than 200 sections keeps its one column, the click following the change scrolled past", and
`presentation/CLAUDE.md` says "Where nothing is paged (the editor's preview, a songbook past 200 sections) the changes
are lines of the one column". That only holds on a window with room for one column.

For such a song `searchGrid` skips the per-stretch layout (`SongLyrics.kt:1610` at 1c52e5347):

```kotlin
if (timingStarts.size < 2 || !canCutSections || availableHeightPx <= 0 || sectionCount > MAX_CUT_SECTION_COUNT) {
    return searchSegment(totalWidth, units, sectionOffset = 0, availableHeightPx = availableHeightPx)
}
```

and `searchSegment` skips the one-column paging (`sectionCount <= MAX_CUT_SECTION_COUNT`, `SongLyrics.kt:1541`) and the
magazine flow (`SongLyrics.kt:1573`), but not `searchColumnCount` (`SongLyrics.kt:1555-1563`), whose
`maxColumnCount = widthColumnCountFor(totalWidth)` is several on a wide window. A songbook does not fit the screen in
any number of columns, so `searchColumnCount` (`SectionGrid.kt:147`) ends on `gridFor(maxColumnCount)`, which is
`flowIntoRows` — rows of several columns read across. Such a grid is stepped by rows (`isSteppedByRow`), and
`stepSections` names each row by its first section (`SongLyrics.kt:1797-1798`):

```kotlin
grid.columnCounts.indices.filter(grid::startsPage).map { row -> units.unitSections[grid.rows.indexOfFirst { it == row }] }
```

so `timingIndexAt` (`RowSnapping.kt:109`) only sees a change that heads a row: one that lands in a row's second or
third column is not heard until the next row, while the reader is already playing what follows it. A row holding
sections on both sides of a change cannot be played at one tempo anyway.

## Fix

Options:

1. **One column for a songbook that has a change (recommended).** In `SongSectionsLayout`,
   `widthColumnCountFor` (`SongLyrics.kt:1429`) answers 1 also where `sectionCount > MAX_CUT_SECTION_COUNT` and the song
   holds a change some line of the song comes before: `units.timingStarts.size > 1`, which after plan 20 is exactly
   that. A change written before the first line (which plan 20 takes out of `timingStarts`) is reported at section 0
   and `timingIndexAt` finds it from the first stop of any grid, rows of several columns included, so it is no reason
   to give up the columns; do not switch to "any section is a `Timing`". Land after plan 20. The single
   column is never measured (`gridFor(1)` is `singleColumnGrid`), has no dividers, so it is stepped by its sections and
   `stepSections` is every section: the click follows the change scrolled past, as the docs say. Songbooks without a
   change keep their current layout. Docs: make the two statements above precise — "a songbook of more than 200
   sections that changes its tempo or time is one column whatever the width, the click following the change scrolled
   past".
2. **One column for every songbook**, matching the docs' "keeps its one column" literally and removing the
   multi-column search on >200 sections too. A visible layout change for songbooks without any change on wide
   windows (one centered column at the maximum column width instead of rows), so it is the user's call; recommended
   only if they want songbooks to look the same everywhere.
3. Compute the timing from the top visible *section* rather than the row's first. Rejected: a row of several columns
   still holds sections of two tempos side by side, and what the reader reads in column two is not at the top.

Recommended: 1 (decision the user can flip to 2).

## Tests

None: the condition lives inside the `Layout` measure lambda, and the grid it picks (`singleColumnGrid`) is already
tested. If the executor extracts the column-count decision into a pure function, test that >200 sections with a change
gives 1 at a width that fits four.

## Manual check

On the desktop maximised (wide enough for three or more columns), open a file of more than 200 short sections with
`{tempo: 60}` written before one in the middle of the file and the metronome panel open: the song is one centered
column; stepping down with the step buttons or Down, the click and the app bar's tempo switch to 60 when the section
after the change reaches the top. The same file without the change looks as it did before (option 1).
