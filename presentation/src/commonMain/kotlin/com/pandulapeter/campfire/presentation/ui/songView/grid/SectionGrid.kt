/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.grid

import com.pandulapeter.campfire.presentation.ui.songView.ColumnCenteringHold
import com.pandulapeter.campfire.presentation.ui.songView.SongSectionsLayout
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.places
import com.pandulapeter.campfire.presentation.ui.songView.model.sectionChunkStarts
import kotlin.math.ceil
import kotlin.math.max

/**
 * Which cell every section goes into: the row it is in, its column within that row, and the number of columns of
 * every row (which decides how wide the columns of that row are). The sections of a cell are stacked in their order.
 *
 * The layout places the chunks sections are composed as (see [sectionChunkStarts]) rather than whole sections, so the
 * grid it places is one of chunks, every chunk of a section in its section's cell ([expandedTo]) unless the section was
 * cut into pieces (see [flowLikeAMagazine]).
 *
 * A row is read on a screen of its own, a page, unless it [joinsPrevious]: then it is stacked under the row before it,
 * a section gap below it, and read on the same page (see [flowIntoPages]), the rows of one page sharing one start edge
 * ([sharesKeyline]).
 *
 * This is what is decided for the settled width, while the positions are only worked out by [arrange] from the
 * heights the sections have at the width the layout is actually given.
 */
internal class SectionGrid(
    val rows: IntArray,
    val columns: IntArray,
    val columnCounts: IntArray,
    /** The rows of a single section that are as wide as it needs rather than as a column, see [flowIntoRows]. */
    val wideRows: BooleanArray = BooleanArray(columnCounts.size),
    /** The rows read on the same page as the row above them rather than on one of their own. */
    val joinsPrevious: BooleanArray = BooleanArray(columnCounts.size),
    /** Whether every row starts at the same edge, the way the lines of one column do, rather than centered on its own. */
    val sharesKeyline: Boolean = false,
) {

    /** How many pages the rows are read on, each the stop of a step. */
    val pageCount get() = columnCounts.size - joinsPrevious.count { it }

    /** Whether the row at [row] starts a page of its own. */
    fun startsPage(row: Int) = !joinsPrevious[row]
}

/** Whether [other] puts every section into the same cell as this grid, in rows of the same widths. */
internal fun SectionGrid.hasSameCellsAs(other: SectionGrid) = this === other || (
    rows.contentEquals(other.rows) &&
        columns.contentEquals(other.columns) &&
        columnCounts.contentEquals(other.columnCounts) &&
        wideRows.contentEquals(other.wideRows) &&
        joinsPrevious.contentEquals(other.joinsPrevious) &&
        sharesKeyline == other.sharesKeyline
    )

/**
 * This grid of sections as a grid of the chunks they are composed as, where [unitSections] names the section of every
 * chunk: each of them in its section's cell.
 */
internal fun SectionGrid.expandedTo(unitSections: IntArray) = SectionGrid(
    rows = IntArray(unitSections.size) { rows[unitSections[it]] },
    columns = IntArray(unitSections.size) { columns[unitSections[it]] },
    columnCounts = columnCounts,
    wideRows = wideRows,
    joinsPrevious = joinsPrevious,
    sharesKeyline = sharesKeyline,
)

/**
 * These grids, each of the chunks of one stretch of a song, as one grid of all of them in their order: the rows of each
 * after the rows of the ones before it. The first row of every grid starts a page, whatever it was laid out to join,
 * since a stretch is laid out as a song of its own. They are laid out at one width, so their rows agree on whether
 * they share a start edge.
 */
internal fun List<SectionGrid>.concatenated(): SectionGrid {
    if (size == 1) return single()
    val rows = IntArray(sumOf { it.rows.size })
    val columns = IntArray(rows.size)
    val columnCounts = IntArray(sumOf { it.columnCounts.size })
    val wideRows = BooleanArray(columnCounts.size)
    val joinsPrevious = BooleanArray(columnCounts.size)
    var unitOffset = 0
    var rowOffset = 0
    forEach { grid ->
        grid.rows.forEachIndexed { unit, row ->
            rows[unitOffset + unit] = row + rowOffset
            columns[unitOffset + unit] = grid.columns[unit]
        }
        grid.columnCounts.forEachIndexed { row, columnCount ->
            columnCounts[rowOffset + row] = columnCount
            wideRows[rowOffset + row] = grid.wideRows[row]
            joinsPrevious[rowOffset + row] = row > 0 && grid.joinsPrevious[row]
        }
        unitOffset += grid.rows.size
        rowOffset += grid.columnCounts.size
    }
    return SectionGrid(
        rows = rows,
        columns = columns,
        columnCounts = columnCounts,
        wideRows = wideRows,
        joinsPrevious = joinsPrevious,
        sharesKeyline = all { it.sharesKeyline },
    )
}

/** The grid of no sections at all. */
internal fun emptyGrid() = SectionGrid(rows = IntArray(0), columns = IntArray(0), columnCounts = IntArray(0))

/**
 * Whether a grid read on [pageCount] pages, found to fit the height available to it ([fits]), is read without stepping
 * through it - which is when the song does not scroll, and so has no buttons that its lines would have to leave room
 * for. Only a single page can: several rows read across are stepped through however short they are.
 */
internal fun isReadWithoutStepping(fits: Boolean, pageCount: Int) = fits && pageCount == 1

/**
 * A grid searched for, whether the whole song [fits] into the height available to it, and how tall it is where that
 * was worked out - [Int.MAX_VALUE] where it was not, which is only where it does not fit.
 */
internal data class SearchedGrid(
    val grid: SectionGrid,
    val fits: Boolean,
    val height: Int,
)

/**
 * The grid of the fewest columns, up to [maxColumnCount], that fits the song into [availableHeight] in a single row,
 * [gridFor] being the grid of a given number of columns at most and [heightOf] its height - or, given [imbalanceOf],
 * of the counts from that one on that fit it in a single row too, the one that fills the page most evenly (see
 * [balancedAmong]): a short song on a large screen is not left in two tall columns in the middle of it.
 *
 * A grid of several rows can be as short as that too, and it may need fewer columns than one of a single row, since
 * its rows are packed tighter than the columns of one: but its rows are stepped through, each on a screen of its own,
 * where a single row is read without a single scroll (see [isReadWithoutStepping]). So it is only taken where no
 * number of columns fits the song in one row - the fewest columns that fit it in several, which wrap the least - and
 * where nothing fits at all, the grid of the most columns, which scrolls the least.
 *
 * Where [maxColumnCount] is one, that single column is not measured at all (see `SongSectionsLayout`), since it is what
 * a window with room for nothing wider takes whatever the heights are, so it is taken as a grid that does not fit - which
 * on such a window is nearly every song anyway. Where a number of columns does not fit
 * the song, the next one tried is at least what even a perfectly even split of [stackedHeight], the height of every
 * section stacked at the width of a single column, would need: the rows may be narrower than the candidate, down to a
 * single column, so the sections are only as tall there as they are in the widest column.
 */
internal fun searchColumnCount(
    maxColumnCount: Int,
    availableHeight: Int,
    stackedHeight: () -> Int,
    gridFor: (columnCount: Int) -> SectionGrid,
    heightOf: (SectionGrid) -> Int,
    imbalanceOf: ((grid: SectionGrid, height: Int, fewest: SectionGrid) -> Double?)? = null,
): SearchedGrid {
    var candidate = 1
    var candidateGrid = gridFor(candidate)
    var candidateHeight = Int.MAX_VALUE
    var fitInRows: SearchedGrid? = null
    while (candidate < maxColumnCount) {
        candidateHeight = heightOf(candidateGrid)
        if (candidateHeight <= availableHeight) {
            if (candidateGrid.pageCount == 1) {
                val fewest = SearchedGrid(candidateGrid, fits = true, height = candidateHeight)
                return if (imbalanceOf == null) fewest else balancedAmong(fewest, maxColumnCount, availableHeight, gridFor, heightOf, imbalanceOf)
            }
            if (fitInRows == null) fitInRows = SearchedGrid(candidateGrid, fits = true, height = candidateHeight)
            candidate++
        } else {
            candidate = maxOf(candidate + 1, ceil(stackedHeight().toDouble() / availableHeight).toInt()).coerceAtMost(maxColumnCount)
        }
        candidateGrid = gridFor(candidate)
    }
    if (candidate > 1) candidateHeight = heightOf(candidateGrid)
    val widest = SearchedGrid(candidateGrid, fits = candidateHeight <= availableHeight, height = candidateHeight)
    return if (isReadWithoutStepping(widest.fits, candidateGrid.pageCount)) widest else fitInRows ?: widest
}

/**
 * Of the grids of a single row that fit the song into [availableHeight], from [fewest] - the one of the fewest columns
 * - on, the one whose page is set most evenly by [imbalanceOf]: how far apart the share of the width its columns fill
 * and the share of the height they fill are, or null for a grid that is no candidate at all (one that wraps more of the
 * song's lines than [fewest] does). Two tall columns in the middle of a wide screen and five short ones across it are
 * both a page a designer would not have set where three fill it as much one way as the other.
 *
 * A grid of more columns is only taken where it is better balanced by at least [BALANCE_MARGIN], since every column
 * more is one more jump of the reader's eye, and the search stops at the first count that is less balanced than the one
 * before it: more columns only ever fill more of the width and less of the height.
 */
private fun balancedAmong(
    fewest: SearchedGrid,
    maxColumnCount: Int,
    availableHeight: Int,
    gridFor: (columnCount: Int) -> SectionGrid,
    heightOf: (SectionGrid) -> Int,
    imbalanceOf: (grid: SectionGrid, height: Int, fewest: SectionGrid) -> Double?,
): SearchedGrid {
    var best = fewest
    var bestImbalance = imbalanceOf(fewest.grid, fewest.height, fewest.grid) ?: return fewest
    var previousImbalance = bestImbalance
    var previousColumnCount = fewest.grid.columnCounts.maxOrNull() ?: return fewest
    for (columnCount in previousColumnCount + 1..maxColumnCount) {
        val grid = gridFor(columnCount)
        // Fewer sections than columns leave the grid of the count before it, which was weighed already.
        val gridColumnCount = grid.columnCounts.maxOrNull() ?: break
        if (grid.pageCount != 1 || gridColumnCount <= previousColumnCount) break
        previousColumnCount = gridColumnCount
        val height = heightOf(grid)
        if (height > availableHeight) break
        val imbalance = imbalanceOf(grid, height, fewest.grid) ?: break
        if (imbalance > previousImbalance) break
        previousImbalance = imbalance
        if (imbalance < bestImbalance - BALANCE_MARGIN) {
            best = SearchedGrid(grid, fits = true, height = height)
            bestImbalance = imbalance
        }
    }
    return best
}

/** How much better balanced a page of more columns has to be, as a natural log of the ratio of its two shares, to be taken. */
private const val BALANCE_MARGIN = 0.15

/**
 * The y position of every section, the total height of the layout, the y positions (centers) of the gaps between the
 * pages and where the content of every page ends, which is above the empty space a page may be followed by. Where the
 * pages were centered, [columnSpreads] is where the columns of every page of a single row were placed and [pageShifts]
 * how far every page of several rows was moved down, by the row that starts it.
 */
internal class SongArrangement(
    val tops: IntArray,
    val height: Int,
    val dividerTops: List<Int>,
    val pageBottoms: List<Int>,
    val columnSpreads: ColumnSpreads = emptyMap(),
    val pageShifts: Map<Int, Int> = emptyMap(),
)

/**
 * Positions the sections of the grid, given their [heights] at the width of their own row: the sections of a cell
 * are stacked [sectionGap] apart, a row is as tall as its tallest cell, a row that [SectionGrid.joinsPrevious] is
 * [sectionGap] under the one before it, and the pages - the rows that do not, with the ones joining them - are
 * [rowGap] apart.
 *
 * Where the grid places chunks, [unitSections] names the section of each: the chunks of one section follow each other
 * with no gap. A section on a card has the card's padding at its top and bottom inside its first and last chunk, so a
 * piece of it cut off from the rest ends in that padding ([piecePadding], by section, nothing for a section drawn
 * without a card) where it is cut, and the piece continuing it in the next column starts with it.
 *
 * The top of every page is at least [minRowPitch] below the top of the one before it, and the last page takes up at
 * least [minLastRowHeight], the difference being left empty under a page that is shorter than that: a song whose
 * scroll comes to rest on the pages is then never shown with a second page under the one it rests on (see
 * `snappedScrollTarget`), and its last page can be brought to the top like the others.
 *
 * A page that is a single row is set the way a designer sets a page with room to spare, wherever there is a
 * [centeredRowHeight] to set it in: every column of it is centered on its own in the page's height - the height of its
 * tallest column, or [centeredRowHeight] where that is more - the way the columns of a row are centered in its width.
 * The space a column leaves first widens the gaps between its sections, by no more than [maxExtraSectionGap] (sections
 * pulled further apart than that read as strangers rather than as one song) and no more than it keeps above and below
 * them, and the rest is shared out above and below, a little less of it above, since what is centered exactly reads
 * as sitting low - a column holding a piece of a section cut across two as well, which held against the cut would
 * lean away from the column beside it. Where [heldSpreads] has a column, it keeps the top and the gaps it was placed at
 * there instead, as far as they still fit (see [ColumnCenteringHold]). A page of several rows is moved down whole by a
 * little less than half of what it leaves of [centeredRowHeight] instead, or where [heldPageShifts] has the row that
 * starts it, by what it was moved down by there, as far as the page still fits. The divider above a page stays where
 * it is either way, so the scroll still comes to rest on the same spot.
 */
internal fun SectionGrid.arrange(
    heights: IntArray,
    sectionGap: Int,
    rowGap: Int,
    minRowPitch: Int = 0,
    minLastRowHeight: Int = 0,
    centeredRowHeight: Int = 0,
    maxExtraSectionGap: Int = 0,
    heldSpreads: ColumnSpreads? = null,
    heldPageShifts: Map<Int, Int>? = null,
    unitSections: IntArray? = null,
    piecePadding: IntArray? = null,
): SongArrangement {
    fun sectionOf(index: Int) = unitSections?.get(index) ?: index
    fun paddingOf(index: Int) = piecePadding?.get(sectionOf(index)) ?: 0
    val tops = IntArray(heights.size)
    val bottoms = IntArray(heights.size)
    val pages = IntArray(heights.size)
    val dividerTops = mutableListOf<Int>()
    val pageBottoms = mutableListOf<Int>()
    val pageTops = mutableListOf<Int>()
    var pageTop = 0
    var pageHeight = 0
    var rowTop = 0
    var cellBottom = 0
    for (index in heights.indices) {
        val isNewRow = index > 0 && rows[index] != rows[index - 1]
        if (isNewRow) {
            if (startsPage(rows[index])) {
                pageBottoms += pageTop + pageHeight
                pageTop += max(pageHeight + rowGap, minRowPitch)
                dividerTops += pageTop - rowGap / 2
                pageHeight = 0
                rowTop = pageTop
            } else {
                rowTop = pageTop + pageHeight + sectionGap
            }
        }
        if (index == 0 || isNewRow && startsPage(rows[index])) pageTops += pageTop
        pages[index] = pageTops.lastIndex
        val isNewCell = index == 0 || isNewRow || columns[index] != columns[index - 1]
        val continuesSection = index > 0 && sectionOf(index) == sectionOf(index - 1)
        tops[index] = when {
            isNewCell -> rowTop + if (continuesSection) paddingOf(index) else 0
            continuesSection -> cellBottom
            else -> cellBottom + sectionGap
        }
        val isCut = index < heights.lastIndex && sectionOf(index + 1) == sectionOf(index) &&
            (rows[index + 1] != rows[index] || columns[index + 1] != columns[index])
        cellBottom = tops[index] + heights[index] + if (isCut) paddingOf(index) else 0
        bottoms[index] = cellBottom
        pageHeight = max(pageHeight, cellBottom - pageTop)
    }
    if (heights.isNotEmpty()) pageBottoms += pageTop + pageHeight
    // Nothing is moved where nothing is centered: every shift would be 0, and every page would end where it does now.
    if (centeredRowHeight <= 0) {
        return SongArrangement(
            tops = tops,
            height = max(pageBottoms.lastOrNull() ?: 0, pageTop + minLastRowHeight),
            dividerTops = dividerTops,
            pageBottoms = pageBottoms,
        )
    }
    val isSingleRowPage = BooleanArray(pageTops.size) { true }
    for (index in heights.indices) if (joinsPrevious[rows[index]]) isSingleRowPage[pages[index]] = false
    val pageFirstRows = IntArray(pageTops.size)
    for (index in heights.indices.reversed()) pageFirstRows[pages[index]] = rows[index]
    val pageShifts = IntArray(pageTops.size) { page ->
        val spaceLeft = centeredRowHeight - (pageBottoms[page] - pageTops[page])
        val held = heldPageShifts?.get(pageFirstRows[page])
        when {
            isSingleRowPage[page] -> 0
            // A page held where it was keeps its shift for as long as it still fits the room it is centered in, so it
            // ends no lower than a page centered there would, and Up and Down still reach every line of it.
            held != null -> held.coerceIn(0, spaceLeft.coerceAtLeast(0))
            else -> (spaceLeft * SPACE_ABOVE_PERCENT / 100).coerceAtLeast(0)
        }
    }
    // A cell is the sections of one column of one row, from where it starts until where the next one does.
    fun startsCell(index: Int) = index == 0 || rows[index] != rows[index - 1] || columns[index] != columns[index - 1]
    var cellCount = 0
    for (index in heights.indices) if (startsCell(index)) cellCount++
    val cellStarts = IntArray(cellCount)
    cellCount = 0
    for (index in heights.indices) if (startsCell(index)) cellStarts[cellCount++] = index
    val cellEnds = IntArray(cellStarts.size) { cell -> if (cell < cellStarts.lastIndex) cellStarts[cell + 1] else heights.size }
    val columnSpreads = HashMap<Long, ColumnSpread>()
    for (cell in cellStarts.indices) {
        val cellStart = cellStarts[cell]
        val cellEnd = cellEnds[cell]
        val page = pages[cellStart]
        if (!isSingleRowPage[page]) continue
        val gapCount = (cellStart + 1 until cellEnd).count { sectionOf(it) != sectionOf(it - 1) }
        val freeSpace = max(pageBottoms[page] - pageTops[page], centeredRowHeight) - (bottoms[cellEnd - 1] - pageTops[page])
        val key = columnKey(rows[cellStart], columns[cellStart])
        val held = heldSpreads?.get(key)
        val extraGap = when {
            // A column held where it was keeps its gaps and its top for as long as they still fit its page.
            held != null -> minOf(held.extraGap, if (gapCount == 0) 0 else freeSpace / gapCount)
            // The space above and below the column takes at least as much as every gap in it, or a full column
            // would be justified, its first section against the top of the screen and its last against the bottom.
            else -> minOf(maxExtraSectionGap, freeSpace / (gapCount + 2))
        }
        val spaceLeft = freeSpace - extraGap * gapCount
        // A column ending or starting with a piece of a section cut across two is centered like any other: held
        // against the cut, the two columns would lean away from each other across the page.
        val columnTop = if (held != null) held.top.coerceIn(0, spaceLeft.coerceAtLeast(0)) else spaceLeft * SPACE_ABOVE_PERCENT / 100
        columnSpreads[key] = ColumnSpread(top = columnTop, extraGap = extraGap)
        var gapsAbove = 0
        for (index in cellStart until cellEnd) {
            if (index > cellStart && sectionOf(index) != sectionOf(index - 1)) gapsAbove++
            val shift = columnTop + gapsAbove * extraGap
            tops[index] += shift
            bottoms[index] += shift
        }
    }
    for (index in heights.indices) {
        tops[index] += pageShifts[pages[index]]
        bottoms[index] += pageShifts[pages[index]]
    }
    // What a page holds ends where its lowest column now does, which spreading its sections out may have moved.
    val shiftedPageBottoms = pageBottoms.mapIndexed { page, bottom -> if (isSingleRowPage[page]) pageTops[page] else bottom + pageShifts[page] }.toMutableList()
    for (index in heights.indices) {
        if (isSingleRowPage[pages[index]]) shiftedPageBottoms[pages[index]] = max(shiftedPageBottoms[pages[index]], bottoms[index])
    }
    return SongArrangement(
        tops = tops,
        height = max(shiftedPageBottoms.lastOrNull() ?: 0, pageTop + minLastRowHeight),
        dividerTops = dividerTops,
        pageBottoms = shiftedPageBottoms,
        columnSpreads = columnSpreads,
        pageShifts = pageTops.indices.filter { !isSingleRowPage[it] }.associate { pageFirstRows[it] to pageShifts[it] },
    )
}

/**
 * How much of the space left around something centered in a page goes above it, in percent: a little less than half,
 * since what is centered exactly reads as sitting low.
 */
private const val SPACE_ABOVE_PERCENT = 45

/** The grid of a layout one column wide: every section in the one cell of the one row, in their order. */
internal fun singleColumnGrid(sectionCount: Int) = SectionGrid(
    rows = IntArray(sectionCount),
    columns = IntArray(sectionCount),
    columnCounts = if (sectionCount == 0) IntArray(0) else intArrayOf(1),
)

/**
 * The cell of every section in [from, until) when they are stacked into [cellCount] consecutive cells, [sectionGap]
 * apart, none of them taller than [maxCellHeight] and all of them as close to equally tall as possible.
 *
 * A greedy fill would leave every cell a little short of the ideal height and dump all of the accumulated slack on the
 * last one, so instead this is a dynamic program over the split points that minimizes the squared deviation of the
 * cells from the ideal height. Every cell gets at least one section, so the cells always span the full width of their
 * row. The caller makes sure that such a split exists.
 */
internal fun IntArray.balanceIntoCells(from: Int, until: Int, cellCount: Int, sectionGap: Int, maxCellHeight: Int): IntArray {
    val size = until - from
    if (cellCount == 1) return IntArray(size)
    val prefixHeights = LongArray(size + 1)
    for (index in 0 until size) prefixHeights[index + 1] = prefixHeights[index] + this[from + index] + sectionGap
    fun heightOf(start: Int, end: Int) = prefixHeights[end] - prefixHeights[start] - sectionGap
    val idealHeight = heightOf(0, size).toDouble() / cellCount
    val splits = Array(cellCount) { IntArray(size + 1) }
    // costs[i] holds the cost of the best distribution of the first i sections into the cells processed so far.
    var costs = DoubleArray(size + 1) { Double.MAX_VALUE }
    costs[0] = 0.0
    for (cell in 0 until cellCount) {
        val nextCosts = DoubleArray(size + 1) { Double.MAX_VALUE }
        // The first `cell` sections are taken by the previous cells and the last ones are still needed by the
        // remaining cells, since no cell may be left empty.
        for (end in cell + 1..size - (cellCount - cell - 1)) {
            for (start in cell until end) {
                if (costs[start] == Double.MAX_VALUE) continue
                val height = heightOf(start, end)
                if (height > maxCellHeight) continue
                val deviation = height - idealHeight
                val cost = costs[start] + deviation * deviation
                if (cost < nextCosts[end]) {
                    nextCosts[end] = cost
                    splits[cell][end] = start
                }
            }
        }
        costs = nextCosts
    }
    val cells = IntArray(size)
    var end = size
    for (cell in cellCount - 1 downTo 0) {
        val start = splits[cell][end]
        for (index in start until end) cells[index] = cell
        end = start
    }
    return cells
}

/**
 * Packs [sectionCount] sections into rows that are read across, then downwards, each row having between one and
 * [maxColumnCount] columns: [heightAt] tells how tall a section is in a row of a given number of columns, since fewer
 * columns are wider ones. Within a row the sections fill its columns top to bottom, stacked [sectionGap] apart, and
 * the rows are laid out apart from each other by [arrange].
 *
 * **The rows are chosen to take as few pages as possible, and then to fill the song's first ones as full as they can
 * be.** A row is a stop the song is read at, a page of its own wherever the rows are read one at a time, and a row
 * taller than the screen is as many pages as it takes to page through it ([pagesOf]): that is how many times the reader
 * has to move on, while a row shorter than the screen saves nothing but leaves the rest of its page empty. So of the
 * ways of packing the song into the fewest pages, the one taken is the one whose first row reaches furthest into the
 * song, and so on for every row after it, which leaves whatever slack there is on its last page, where it reads as the
 * end of the song - rather than leaving the first page half empty because the short sections a song opens with (its
 * info card, an intro) pack lowest together. Only then does a row's height decide, the lowest way of laying out the
 * same sections winning (see [isBetterRow]).
 *
 * **A row of more than one column is never taller than [maxRowHeight]**, the height of the screen: its columns are
 * read one after the other, and a column that runs past the bottom of the screen sends the reader back up to the top
 * of the next one, which is the very thing the rows exist to avoid - and which a reader stepping through the song with
 * a pedal, who can only go on or go back, cannot do without going back over what they have just played. Up to that
 * height a row is free to be as tall as its columns need, so a song that fits the screen in columns is a single row of
 * them, filled top to bottom, since nothing is scrolled past there to be sent back to. The song's header is not part of
 * any row: the rows start below it, so every row, the first one included, has the whole screen.
 *
 * **A section that is taller than the screen on its own gets a row of its own**, which is read from top to bottom like
 * any other scrolling text, however little it overflows the screen by. A stack in a single column is held to the screen
 * too, since stacking more under a section that has to be scrolled anyway saves nothing but the gap between two rows.
 *
 * A row has exactly as many columns as its sections fill, so no row is left with a hole in it: a hole in the middle
 * of a song looks like a mistake, and even at its end it is width the sections could have used to wrap less. Where a
 * row ends and how many columns it has decide how well the rest of the song can be packed, so both are chosen by a
 * dynamic program (from the last section backwards), which is what makes the first row the longest that still leaves the
 * fewest pages for the rest: what the rest is packed into is already decided by where the row ends. A candidate row of a given
 * number of columns is as tall as the lowest cap under which a first-fit stacking of its sections takes no more cells
 * than that, first-fit being what keeps consecutive sections in the fewest cells: the cells can always be split further
 * to make up the count, and a split never makes one taller. That height only grows with the row, so it is carried from
 * one end of the row to the next, and a column count is given up for a row once not even the whole screen holds its
 * sections in that many columns. A single section always fits in a row of its own, so there is always a way to pack the
 * song. The row that wins is then balanced under its height (see [balanceIntoCells]).
 *
 * **A section whose lines do not wrap may have a row of its own as wide as it needs**, the whole width at most, where
 * [wideHeightAt] gives its height in one: a staff of tablature longer than a column is cut into systems there, and
 * the column beside it grows by as many staves, while in a row of its own it can be read as it was written. It is one
 * more candidate for the row starting at that section, taken wherever it is strictly better by the same order - fewer
 * pages, a longer row, or the same row lower - so a tab that fits a column anyway, or one too long for the window as
 * well, stays where it was. The sections around it may be stacked in
 * that row too, each at its own width ([forEachWideRow]), so a short section next to it is not left with a row of its
 * own: a row that reaches further into the song is what the rows are chosen for, so that is taken wherever it fits.
 */
internal fun flowIntoRows(
    sectionCount: Int,
    maxColumnCount: Int,
    heightAt: (index: Int, columnCount: Int) -> Int,
    wideHeightAt: (index: Int) -> Int?,
    sectionGap: Int,
    maxRowHeight: Int,
): SectionGrid {
    if (sectionCount == 0) return emptyGrid()
    // heights[k - 1][i] is the height of section i in a row of k columns, heightSums[k - 1][i] the total height of
    // the sections before i at that width.
    val heights = Array(maxColumnCount) { column -> IntArray(sectionCount) { heightAt(it, column + 1) } }
    val heightSums = Array(maxColumnCount) { column ->
        LongArray(sectionCount + 1).also { sums -> for (index in 0 until sectionCount) sums[index + 1] = sums[index] + heights[column][index] }
    }

    // The number of cells the sections in [start, end) take when they are stacked first-fit into cells no taller than
    // cap, which is the fewest any stacking of them can. None of them is taller than cap on its own.
    fun cellCount(sectionHeights: IntArray, start: Int, end: Int, cap: Int): Int {
        var cells = 1
        var cellHeight = sectionHeights[start].toLong()
        for (index in start + 1 until end) {
            if (cellHeight + sectionGap + sectionHeights[index] <= cap) {
                cellHeight += sectionGap + sectionHeights[index]
            } else {
                cells++
                cellHeight = sectionHeights[index].toLong()
            }
        }
        return cells
    }

    // The height of the lowest row of columnCount columns that holds the sections in [start, end), at least atLeast
    // (which is no lower than the tallest of them), or null where not even maxHeight is tall enough for one.
    fun rowHeight(columnCount: Int, start: Int, end: Int, atLeast: Int, maxHeight: Int): Int? {
        val sectionHeights = heights[columnCount - 1]
        if (cellCount(sectionHeights, start, end, atLeast) <= columnCount) return atLeast
        val stackedHeight = heightSums[columnCount - 1][end] - heightSums[columnCount - 1][start] + sectionGap.toLong() * (end - start - 1)
        val highest = minOf(maxHeight.toLong(), stackedHeight).toInt()
        if (highest <= atLeast || cellCount(sectionHeights, start, end, highest) > columnCount) return null
        var tooLow = atLeast
        var enough = highest
        while (enough - tooLow > 1) {
            val cap = tooLow + (enough - tooLow) / 2
            if (cellCount(sectionHeights, start, end, cap) <= columnCount) enough = cap else tooLow = cap
        }
        return enough
    }

    // pageCounts[i] is the fewest pages the sections from i onwards fit, rowEnds[i] where their first row ends,
    // rowColumnCounts[i] how many columns that row has, rowHeights[i] how tall it is and isRowWide[i] whether it is a
    // wide row.
    val pageCounts = IntArray(sectionCount + 1)
    val rowEnds = IntArray(sectionCount + 1)
    val rowColumnCounts = IntArray(sectionCount + 1)
    val rowHeights = IntArray(sectionCount + 1)
    val isRowWide = BooleanArray(sectionCount + 1)
    // The tallest section and the height of the candidate row so far, per column count, carried from one end of the
    // row to the next, since neither can fall as the row grows: the search runs on every frame of a window being
    // resized, and it only has to look further up from where the shorter row left off.
    val tallest = IntArray(maxColumnCount)
    val lowestHeights = IntArray(maxColumnCount)
    val isExhausted = BooleanArray(maxColumnCount)
    for (start in sectionCount - 1 downTo 0) {
        var bestPageCount = Int.MAX_VALUE
        tallest.fill(0)
        lowestHeights.fill(0)
        isExhausted.fill(false)
        var exhaustedCount = 0
        var end = start + 1
        while (end <= sectionCount && exhaustedCount < maxColumnCount) {
            for (columnCount in 1..maxColumnCount) {
                val column = columnCount - 1
                if (isExhausted[column]) continue
                val sectionHeights = heights[column]
                tallest[column] = max(tallest[column], sectionHeights[end - 1])
                if (end - start < columnCount) continue
                val height = if (end - start == 1) {
                    sectionHeights[start]
                } else {
                    // Every longer row holds this section too, and no longer row fits under the screen where this one does
                    // not, so none of them can have this many columns either.
                    val height = if (tallest[column] > maxRowHeight) null else rowHeight(columnCount, start, end, max(lowestHeights[column], tallest[column]), maxRowHeight)
                    if (height == null) {
                        if (tallest[column] > maxRowHeight || cellCount(sectionHeights, start, end, maxRowHeight) > columnCount) {
                            isExhausted[column] = true
                            exhaustedCount++
                        }
                        continue
                    }
                    height
                }
                lowestHeights[column] = height
                // A full tie goes to the fewer, wider columns, which wrap less, since they are tried first.
                val pageCount = pagesOf(height, maxRowHeight) + pageCounts[end]
                if (isBetterRow(pageCount, end, height, bestPageCount, rowEnds[start], rowHeights[start])) {
                    bestPageCount = pageCount
                    rowEnds[start] = end
                    rowColumnCounts[start] = columnCount
                    rowHeights[start] = height
                }
            }
            end++
        }
        forEachWideRow(start, sectionCount, heights[0], wideHeightAt, sectionGap, maxRowHeight) { end, height ->
            // Only where it is strictly better, since a row wider than a column has lines longer than a column's.
            val pageCount = pagesOf(height, maxRowHeight) + pageCounts[end]
            if (isBetterRow(pageCount, end, height, bestPageCount, rowEnds[start], rowHeights[start])) {
                bestPageCount = pageCount
                rowEnds[start] = end
                rowColumnCounts[start] = 1
                rowHeights[start] = height
                isRowWide[start] = true
            }
        }
        pageCounts[start] = bestPageCount
    }
    val rows = IntArray(sectionCount)
    val columns = IntArray(sectionCount)
    val columnCounts = mutableListOf<Int>()
    val wideRows = mutableListOf<Boolean>()
    var start = 0
    while (start < sectionCount) {
        wideRows += isRowWide[start]
        val end = rowEnds[start]
        val columnCount = rowColumnCounts[start]
        val row = columnCounts.size
        val cells = heights[columnCount - 1].balanceIntoCells(
            from = start,
            until = end,
            cellCount = columnCount,
            sectionGap = sectionGap,
            maxCellHeight = rowHeights[start],
        )
        for (index in start until end) {
            rows[index] = row
            columns[index] = cells[index - start]
        }
        columnCounts += columnCount
        start = end
    }
    return SectionGrid(rows = rows, columns = columns, columnCounts = columnCounts.toIntArray(), wideRows = wideRows.toBooleanArray())
}

/**
 * Calls [onRow] with the end and the height of every wide row that may start at [start]: the sections from there
 * stacked in a single column, at least one of them needing more than a column ([wideHeightAt] being its height in a row
 * as wide as it needs, null for one that does not), each of the others as tall as in a single column ([columnHeights]),
 * since it is laid out at a column's width.
 *
 * Without the sections around it a wide row would leave a short section next to it with a row of its own, and so
 * with a screen of its own wherever the rows are read one at a time: a song's info card above a long staff of
 * tablature. A stack of several is held to [maxRowHeight], as a stack in a single column is in [flowIntoRows].
 */
internal inline fun forEachWideRow(
    start: Int,
    sectionCount: Int,
    columnHeights: IntArray,
    wideHeightAt: (index: Int) -> Int?,
    sectionGap: Int,
    maxRowHeight: Int,
    onRow: (end: Int, height: Int) -> Unit,
) {
    var height = -sectionGap.toLong()
    var isWide = false
    for (index in start until sectionCount) {
        val wideHeight = wideHeightAt(index)
        height += sectionGap + (wideHeight ?: columnHeights[index])
        if (index > start && height > maxRowHeight) return
        isWide = isWide || wideHeight != null
        if (isWide) onRow(index + 1, height.toInt())
    }
}

/**
 * How many pages a row [height] tall takes on a screen [maxRowHeight] high: one for a row that fits it, and as many as it
 * takes to page through one that does not.
 */
internal fun pagesOf(height: Int, maxRowHeight: Int) =
    if (height <= maxRowHeight) 1 else ((height - 1L) / maxRowHeight.coerceAtLeast(1) + 1).toInt()

/**
 * How many pages this grid takes on a screen [maxRowHeight] high, every page of rows read on pages of its own (see
 * [pagesOf]), given the [heights] of what it places, as [arrange] takes them.
 */
internal fun SectionGrid.pageCount(heights: IntArray, sectionGap: Int, maxRowHeight: Int, unitSections: IntArray? = null, piecePadding: IntArray? = null): Int {
    val pageBottoms = arrange(heights, sectionGap, rowGap = 0, unitSections = unitSections, piecePadding = piecePadding).pageBottoms
    return pageBottoms.indices.sumOf { row -> pagesOf(pageBottoms[row] - (pageBottoms.getOrNull(row - 1) ?: 0), maxRowHeight) }
}

/**
 * Whether a first row ending at [end], [height] tall and leaving the song [pageCount] pages in all, is a better start
 * for the sections from where it starts than the best one found so far, which leaves [bestPageCount] pages, ends at
 * [bestEnd] and is [bestHeight] tall: fewer pages first, then the row that reaches further into the song, then the
 * lower one.
 *
 * The rows after the first are already the best packing of what is left from where it ends, so preferring the
 * longest first row in a backward pass fills every row as full as the fewest pages allow, from the top of the song down.
 */
internal fun isBetterRow(pageCount: Int, end: Int, height: Int, bestPageCount: Int, bestEnd: Int, bestHeight: Int) =
    pageCount < bestPageCount || pageCount == bestPageCount && (end > bestEnd || end == bestEnd && height < bestHeight)
