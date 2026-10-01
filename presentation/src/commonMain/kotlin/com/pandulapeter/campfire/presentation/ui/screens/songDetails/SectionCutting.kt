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

/**
 * [flowIntoRows] for a song it could not fit the screen, where a section may be cut into pieces put side by side in
 * the columns of one row, the way a newspaper runs an article on from the bottom of one column to the top of the next.
 * It is the last resort, since a cut asks the reader to find their way from one column into the next in the middle of a
 * verse, so the layout only asks for it once the song has to be scrolled without it (see `SongSectionsLayout`), and
 * only two things are worth one: a section taller than the screen, which is cut wherever it is found, and - only where
 * [cutsEverySection] - a song that fits the screen once something is cut, and not before.
 *
 * A piece never continues in another row: a section cut across rows would be split by a divider and a scroll, and read
 * no better than one scrolled through whole. What a cut buys is a section that did not fit the screen fitting it in
 * several columns - a verse a little taller than a phone held sideways, which would otherwise have a row to itself
 * with the rest of the width empty - or a row of sections that only balance into its columns once one of them is cut.
 *
 * The grid is one of chunks: the units of section `s` are `sectionStarts[s]` to `sectionStarts[s + 1]`, [heightAt] the
 * height of a unit in a row of a given number of columns, and a section may only be cut in front of a unit
 * [isCuttableBefore] says so of. A
 * section on a card has [piecePadding] at both sides of every cut, as [arrange] places them. Within a row the cells are filled in their order, each piece as tall as the height of the
 * row allows, the lowest such height being searched for. A row prefers no cut at all, and then cuts only the
 * sections taller than [maxRowHeight], unless cutting more saves at least [minCutSaving] of its height; every row with a
 * cut in it also counts that much taller while the rows are chosen, so that fewer cuts win. Everything else - where
 * the rows end, how many columns each has, the ties, the wide rows ([wideHeightAt]) - is
 * decided the way [flowIntoRows] decides it, a row of several columns never being taller than [maxRowHeight].
 */
internal fun flowIntoRowsCuttingSections(
    sectionStarts: IntArray,
    maxColumnCount: Int,
    heightAt: (unit: Int, columnCount: Int) -> Int,
    isCuttableBefore: (unit: Int) -> Boolean,
    wideHeightAt: (section: Int) -> Int?,
    piecePadding: IntArray,
    sectionGap: Int,
    rowGap: Int,
    maxRowHeight: Int,
    minCutSaving: Int,
    cutsEverySection: Boolean,
): SectionGrid {
    val sectionCount = sectionStarts.size - 1
    if (sectionCount <= 0) return emptyGrid()
    val unitCount = sectionStarts[sectionCount]
    // Looked up for every unit of every row tried, which a scan of the section starts made quadratic in a long song.
    val unitSections = IntArray(unitCount).also { table ->
        for (section in 0 until sectionCount) table.fill(section, sectionStarts[section], sectionStarts[section + 1])
    }
    val heights = Array(maxColumnCount) { column -> IntArray(unitCount) { heightAt(it, column + 1) } }
    val sectionHeights = Array(maxColumnCount) { column ->
        IntArray(sectionCount) { section -> (sectionStarts[section] until sectionStarts[section + 1]).sumOf { heights[column][it] } }
    }

    fun stackedHeight(columnCount: Int, start: Int, end: Int) =
        (start until end).sumOf { sectionHeights[columnCount - 1][it].toLong() } + sectionGap.toLong() * (end - start - 1)

    // The cell of every unit of the sections in [start, end) when the cells are filled in their order under cap, and how
    // many cells that takes, or null where something does not fit an empty cell at all. A section that is not cut goes
    // whole into the next cell once it no longer fits the one being filled.
    fun fill(columnCount: Int, start: Int, end: Int, cap: Int, cuts: Cuts): Pair<IntArray, Int>? {
        val unitHeights = heights[columnCount - 1]
        val firstUnit = sectionStarts[start]
        val cells = IntArray(sectionStarts[end] - firstUnit)
        var cell = 0
        var used = 0
        var isEmpty = true
        for (section in start until end) {
            val padding = piecePadding[section]
            val sectionHeight = sectionHeights[columnCount - 1][section]
            val isCut = when (cuts) {
                Cuts.NONE -> false
                Cuts.OVERSIZED -> sectionHeight > maxRowHeight
                Cuts.EVERY -> true
            }
            if (!isCut && sectionHeight > cap) return null
            var from = sectionStarts[section]
            val until = sectionStarts[section + 1]
            var isContinuation = false
            while (from < until) {
                val base = if (isEmpty) 0 else used + sectionGap
                val top = if (isContinuation) padding else 0
                // The longest piece that fits, ending where the section ends or where it may be cut; one that ends before
                // the section does ends in the padding of the cut.
                var to = from
                var height = 0
                var end = from
                var stackedHeight = 0
                while (end < until && base + top + stackedHeight + unitHeights[end] <= cap) {
                    stackedHeight += unitHeights[end]
                    end++
                    val endsSection = end == until
                    if ((endsSection || (isCut && isCuttableBefore(end))) && base + top + stackedHeight + (if (endsSection) 0 else padding) <= cap) {
                        to = end
                        height = stackedHeight
                    }
                }
                if (to == from || (to < until && !isCut && !isEmpty)) {
                    if (isEmpty) return null
                    cell++
                    used = 0
                    isEmpty = true
                    continue
                }
                for (unit in from until to) cells[unit - firstUnit] = cell
                if (to < until) {
                    cell++
                    used = 0
                    isEmpty = true
                    isContinuation = true
                } else {
                    used = base + top + height
                    isEmpty = false
                }
                from = to
            }
        }
        return cells to cell + 1
    }

    // The lowest cap under which filling [start, end) takes no more than columnCount cells, or null where not even the
    // highest row allowed does.
    fun lowestCap(columnCount: Int, start: Int, end: Int, cuts: Cuts): Int? {
        val highest = minOf(maxRowHeight.toLong(), stackedHeight(columnCount, start, end)).toInt()
        val highestFill = fill(columnCount, start, end, highest, cuts) ?: return null
        if (highestFill.second > columnCount) return null
        var tooLow = 0
        var enough = highest
        while (enough - tooLow > 1) {
            val cap = tooLow + (enough - tooLow) / 2
            val cells = fill(columnCount, start, end, cap, cuts)?.second
            if (cells != null && cells <= columnCount) enough = cap else tooLow = cap
        }
        return enough
    }

    // How a row of [start, end) in columnCount columns is laid out, how tall it is and whether it cuts a section: the
    // cell of every unit, or null where no such row fits.
    fun rowOf(columnCount: Int, start: Int, end: Int): Triple<IntArray, Int, Boolean>? {
        val units = sectionStarts[end] - sectionStarts[start]
        if (columnCount == 1) {
            val height = stackedHeight(1, start, end)
            return if (end - start == 1 || height <= maxRowHeight) Triple(IntArray(units), height.toInt(), false) else null
        }
        val sectionHeightsAtWidth = sectionHeights[columnCount - 1]
        // A row has exactly as many columns as it fills, as in flowIntoRows, so a way of filling it that leaves one empty
        // is no way of laying it out in that many columns.
        fun lowestFullCap(cuts: Cuts) = lowestCap(columnCount, start, end, cuts)?.takeIf { fill(columnCount, start, end, it, cuts)?.second == columnCount }
        val whole = if (end - start >= columnCount) lowestFullCap(Cuts.NONE) else null
        val oversizedCut = lowestFullCap(Cuts.OVERSIZED)
        val everyCut = if (cutsEverySection) lowestFullCap(Cuts.EVERY) else null
        val lowest = listOfNotNull(whole, oversizedCut, everyCut).minOrNull() ?: return null
        if (whole != null && whole - lowest < minCutSaving) {
            val sectionCells = sectionHeightsAtWidth.balanceIntoCells(start, end, columnCount, sectionGap, whole)
            return Triple(IntArray(units) { unit -> sectionCells[unitSections[sectionStarts[start] + unit] - start] }, whole, false)
        }
        val (cuts, cap) = if (oversizedCut != null && oversizedCut - lowest < minCutSaving) Cuts.OVERSIZED to oversizedCut else Cuts.EVERY to everyCut!!
        val cells = fill(columnCount, start, end, cap, cuts)!!.first
        val isCut = (1 until units).any { unit ->
            val first = sectionStarts[start]
            cells[unit] != cells[unit - 1] && unitSections[first + unit] == unitSections[first + unit - 1]
        }
        return Triple(cells, cap, isCut)
    }

    // As in flowIntoRows: costs[i] is the smallest total height of the sections from i onwards, and the rest describes
    // the first row of that.
    val costs = LongArray(sectionCount + 1)
    val rowEnds = IntArray(sectionCount + 1)
    val rowColumnCounts = IntArray(sectionCount + 1)
    val rowCells = arrayOfNulls<IntArray>(sectionCount + 1)
    val isRowWide = BooleanArray(sectionCount + 1)
    for (start in sectionCount - 1 downTo 0) {
        var best = Long.MAX_VALUE
        for (end in start + 1..sectionCount) {
            // The lowest the sections could be stacked in is the widest single column, and no row holds more than the
            // screen in every column: a longer row only holds more.
            if (end - start > 1 && stackedHeight(1, start, end) > maxRowHeight.toLong() * maxColumnCount) break
            for (columnCount in 1..maxColumnCount) {
                val (cells, height, isCut) = rowOf(columnCount, start, end) ?: continue
                val cost = height + (if (isCut) minCutSaving else 0) + if (end < sectionCount) rowGap + costs[end] else 0L
                if (cost < best || (cost == best && end > rowEnds[start])) {
                    best = cost
                    rowEnds[start] = end
                    rowColumnCounts[start] = columnCount
                    rowCells[start] = cells
                    isRowWide[start] = false
                }
            }
        }
        forEachWideRow(start, sectionCount, sectionHeights[0], wideHeightAt, sectionGap, maxRowHeight) { end, height ->
            val cost = height + if (end < sectionCount) rowGap + costs[end] else 0L
            if (cost < best) {
                best = cost
                rowEnds[start] = end
                rowColumnCounts[start] = 1
                rowCells[start] = IntArray(sectionStarts[end] - sectionStarts[start])
                isRowWide[start] = true
            }
        }
        costs[start] = best
    }
    val rows = IntArray(unitCount)
    val columns = IntArray(unitCount)
    val columnCounts = mutableListOf<Int>()
    val wideRows = mutableListOf<Boolean>()
    var start = 0
    while (start < sectionCount) {
        val row = columnCounts.size
        val cells = rowCells[start]!!
        for (unit in sectionStarts[start] until sectionStarts[rowEnds[start]]) {
            rows[unit] = row
            columns[unit] = cells[unit - sectionStarts[start]]
        }
        columnCounts += rowColumnCounts[start]
        wideRows += isRowWide[start]
        start = rowEnds[start]
    }
    return SectionGrid(rows = rows, columns = columns, columnCounts = columnCounts.toIntArray(), wideRows = wideRows.toBooleanArray())
}

/** Which sections [flowIntoRowsCuttingSections] may cut while it fills a row. */
private enum class Cuts {
    NONE,

    /** Only a section taller than the screen, which does not fit a row whole however the row is laid out. */
    OVERSIZED,
    EVERY,
}

/** Whether [grid], a grid of chunks, puts any section into more than one cell. */
internal fun cutsAnySection(grid: SectionGrid, unitSections: IntArray) = (1 until unitSections.size).any { unit ->
    unitSections[unit] == unitSections[unit - 1] && grid.columns[unit] != grid.columns[unit - 1]
}
