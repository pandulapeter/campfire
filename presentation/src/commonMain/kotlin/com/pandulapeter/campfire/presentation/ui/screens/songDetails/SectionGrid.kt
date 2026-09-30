/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import kotlin.math.max

/**
 * Which cell every section goes into: the row it is in, its column within that row, and the number of columns of
 * every row (which decides how wide the columns of that row are). The sections of a cell are stacked in their order.
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
)

/** Whether [other] puts every section into the same cell as this grid, in rows of the same widths. */
internal fun SectionGrid.hasSameCellsAs(other: SectionGrid) = this === other || (
    rows.contentEquals(other.rows) &&
        columns.contentEquals(other.columns) &&
        columnCounts.contentEquals(other.columnCounts) &&
        wideRows.contentEquals(other.wideRows)
    )

/** The grid of no sections at all. */
internal fun emptyGrid() = SectionGrid(rows = IntArray(0), columns = IntArray(0), columnCounts = IntArray(0))

/**
 * The y position of every section, the total height of the layout, the y positions (centers) of the row gaps and where
 * the content of every row ends, which is above the empty space a row may be followed by.
 */
internal class SongArrangement(
    val tops: IntArray,
    val height: Int,
    val dividerTops: List<Int>,
    val rowBottoms: List<Int>,
)

/**
 * Positions the sections of the grid, given their [heights] at the width of their own row: the sections of a cell
 * are stacked [sectionGap] apart, a row is as tall as its tallest cell, and the rows are [rowGap] apart.
 *
 * The top of every row is at least [minRowPitch] below the top of the one before it, and the last row takes up at
 * least [minLastRowHeight], the difference being left empty under a row that is shorter than that: a song whose
 * scroll comes to rest on the rows is then never shown with a second row under the one it rests on (see
 * `snappedScrollTarget`), and its last row can be brought to the top like the others.
 */
internal fun SectionGrid.arrange(
    heights: IntArray,
    sectionGap: Int,
    rowGap: Int,
    minRowPitch: Int = 0,
    minLastRowHeight: Int = 0,
): SongArrangement {
    val tops = IntArray(heights.size)
    val dividerTops = mutableListOf<Int>()
    val rowBottoms = mutableListOf<Int>()
    var rowTop = 0
    var rowHeight = 0
    for (index in heights.indices) {
        val isNewRow = index > 0 && rows[index] != rows[index - 1]
        if (isNewRow) {
            rowBottoms += rowTop + rowHeight
            rowTop += max(rowHeight + rowGap, minRowPitch)
            dividerTops += rowTop - rowGap / 2
            rowHeight = 0
        }
        tops[index] = if (index == 0 || isNewRow || columns[index] != columns[index - 1]) rowTop else tops[index - 1] + heights[index - 1] + sectionGap
        rowHeight = max(rowHeight, tops[index] + heights[index] - rowTop)
    }
    if (heights.isNotEmpty()) rowBottoms += rowTop + rowHeight
    return SongArrangement(tops = tops, height = rowTop + max(rowHeight, minLastRowHeight), dividerTops = dividerTops, rowBottoms = rowBottoms)
}

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
private fun IntArray.balanceIntoCells(from: Int, until: Int, cellCount: Int, sectionGap: Int, maxCellHeight: Int): IntArray {
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
 * the rows are [rowGap] apart. The rows are chosen to make the song as short as possible, which is what decides how
 * much of it is on the screen at once and how much has to be scrolled.
 *
 * **A row of more than one column is never taller than [maxRowHeight]**, the height of the screen: its columns are
 * read one after the other, and a column that runs past the bottom of the screen sends the reader back up to the top
 * of the next one, which is the very thing the rows exist to avoid. Up to that height a row is free to be as tall as its
 * columns need, so a song that fits the screen in columns is a single row of them, filled top to bottom, since nothing
 * is scrolled past there to be sent back to. The song's header is not part of
 * any row: the rows start below it, so every row, the first one included, has the whole screen.
 *
 * **A section that is taller than the screen on its own lets its row be as tall as it is**, up to half a screen more
 * ([maxRowHeight] and a half): it has to be scrolled through anyway, and the columns beside it only send the reader
 * back up by what it overflows the screen with, which is less than what they would be scrolled by as rows of one column
 * each under it. A short screen - a phone held sideways - has verses and choruses a little taller than itself, and
 * they would otherwise all be stacked one per row in a single column with most of the width empty. A section taller
 * than that gets a row of its own, which is read from top to bottom like any other scrolling text. A stack in a single
 * column is held to the screen alone, since stacking more under a section that has to be scrolled anyway saves nothing
 * but the gap between two rows.
 *
 * A row has exactly as many columns as its sections fill, so no row is left with a hole in it: a hole in the middle
 * of a song looks like a mistake, and even at its end it is width the sections could have used to wrap less. Where a
 * row ends and how many columns it has decide how well the rest of the song can be packed, so both are chosen by a
 * dynamic program (from the last section backwards) that minimizes the total height. A candidate row of a given
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
 * more candidate for the row starting at that section, taken wherever it makes the song shorter, so a tab that fits a
 * column anyway, or one too long for the window as well, stays where it was.
 */
internal fun flowIntoRows(
    sectionCount: Int,
    maxColumnCount: Int,
    heightAt: (index: Int, columnCount: Int) -> Int,
    wideHeightAt: (index: Int) -> Int?,
    sectionGap: Int,
    rowGap: Int,
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

    // costs[i] is the smallest total height of the sections from i onwards, rowEnds[i] where their first row ends,
    // rowColumnCounts[i] how many columns that row has, rowHeights[i] how tall it is and isRowWide[i] whether it is a
    // wide row of section i alone.
    val costs = LongArray(sectionCount + 1)
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
    val maxOverflowingRowHeight = if (maxRowHeight == Int.MAX_VALUE) maxRowHeight else maxRowHeight + maxRowHeight / 2
    for (start in sectionCount - 1 downTo 0) {
        var best = Long.MAX_VALUE
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
                    val ceiling = if (columnCount == 1) maxRowHeight else maxOverflowingRowHeight
                    val maxHeight = if (columnCount == 1) maxRowHeight else max(maxRowHeight, tallest[column])
                    // Every longer row holds this section too, and no longer row fits under the ceiling that this one
                    // does not, so none of them can have this many columns either. Under the cap of this row alone it
                    // may still not fit where a longer one would, since a taller section raises the cap.
                    val height = if (tallest[column] > ceiling) null else rowHeight(columnCount, start, end, max(lowestHeights[column], tallest[column]), maxHeight)
                    if (height == null) {
                        if (tallest[column] > ceiling || cellCount(sectionHeights, start, end, ceiling) > columnCount) {
                            isExhausted[column] = true
                            exhaustedCount++
                        }
                        continue
                    }
                    height
                }
                lowestHeights[column] = height
                val cost = height + if (end < sectionCount) rowGap + costs[end] else 0L
                // Ties go to the longer row, so that the slack ends up at the bottom of the song rather than in its
                // middle, and then to the fewer, wider columns, which wrap less.
                if (cost < best || (cost == best && end > rowEnds[start])) {
                    best = cost
                    rowEnds[start] = end
                    rowColumnCounts[start] = columnCount
                    rowHeights[start] = height
                }
            }
            end++
        }
        val wideHeight = wideHeightAt(start)
        if (wideHeight != null) {
            val cost = wideHeight + if (start + 1 < sectionCount) rowGap + costs[start + 1] else 0L
            // Only where it is strictly shorter, since a row wider than a column has lines longer than a column's.
            if (cost < best) {
                best = cost
                rowEnds[start] = start + 1
                rowColumnCounts[start] = 1
                rowHeights[start] = wideHeight
                isRowWide[start] = true
            }
        }
        costs[start] = best
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
