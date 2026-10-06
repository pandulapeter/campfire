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
 * The song flowed into rows of [columnCount] columns the way a magazine sets an article: every column filled down to
 * [maxRowHeight], the height of the screen, a section that does not fit what is left of a column running on at the top
 * of the next one - and from the last column of a row into the first one of the next row. A row is a page wherever the
 * rows are read one at a time, and turning one is the thing a reader without a pedal has to reach for the screen to do
 * and a reader with one has to keep up with, so every page is made to hold as much of the song as it can, and the empty
 * space is left where it does the least harm: at the end of the song.
 *
 * The grid is one of chunks: the units of section `s` are `sectionStarts[s]` to `sectionStarts[s + 1]`, [heightAt] the
 * height of a unit in a row of a given number of columns, and a section may only be cut in front of a unit
 * [isCuttableBefore] says so of (see [sectionChunkStarts], which keeps a line on either side of every cut) and that has
 * a height at that width - a slot of a run of tablature that the run has no row for there is no line to start a piece
 * with. A section
 * on a card has [piecePadding] at both sides of every cut, as [arrange] places them.
 *
 * Where the rows end is decided by filling the columns as full as they go, which is what makes the first pages as full
 * as they can be. What is in a row is then shared out between its columns again, as evenly as it goes, so that their
 * bottoms line up rather than the last one ending short: under the lowest height that still holds the row in its
 * columns, cutting a section wherever that lowers it, since a section is no reason to leave a column short. The last
 * row, and a row that ends early because what follows it cannot be cut to fit a column, gets the fewest columns that
 * hold it, which wrap the least, as a song that fits the screen does. A stretch of a section that cannot be cut and is
 * taller than the screen on its own - a single system of tablature at a large text size - is a single column row of
 * its own, paged through.
 */
internal fun flowLikeAMagazine(
    sectionStarts: IntArray,
    columnCount: Int,
    heightAt: (unit: Int, columnCount: Int) -> Int,
    isCuttableBefore: (unit: Int) -> Boolean,
    piecePadding: IntArray,
    sectionGap: Int,
    maxRowHeight: Int,
): SectionGrid {
    val sectionCount = sectionStarts.size - 1
    if (sectionCount <= 0) return emptyGrid()
    val unitCount = sectionStarts[sectionCount]
    // Looked up for every unit of every column tried, which a scan of the section starts would make quadratic.
    val unitSections = IntArray(unitCount).also { table ->
        for (section in 0 until sectionCount) table.fill(section, sectionStarts[section], sectionStarts[section + 1])
    }
    // Measured only for the column counts a row is actually tried in, since every width is a pass over the song.
    val heightsByColumnCount = arrayOfNulls<IntArray>(columnCount)
    fun heightsAt(columns: Int) = heightsByColumnCount[columns - 1] ?: IntArray(unitCount) { heightAt(it, columns) }.also { heightsByColumnCount[columns - 1] = it }
    fun isSectionStart(unit: Int) = unit >= unitCount || sectionStarts[unitSections[unit]] == unit
    fun isBreak(heights: IntArray, unit: Int) = isSectionStart(unit) || isCuttableBefore(unit) && heights[unit] > 0

    // Where the column that starts at from ends when it is filled as far as cap allows, no further than until, or from
    // itself where not even the first piece fits. A column that starts inside a section starts with the padding of its
    // card, and one that ends inside a section ends with it.
    fun columnEnd(heights: IntArray, from: Int, until: Int, cap: Int): Int {
        var used = if (isSectionStart(from)) 0L else piecePadding[unitSections[from]].toLong()
        var end = from
        for (unit in from until until) {
            if (unit > from && isSectionStart(unit)) used += sectionGap
            used += heights[unit]
            if (used > cap) break
            val next = unit + 1
            if (next == until || isBreak(heights, next)) {
                val bottomPadding = if (isSectionStart(next)) 0 else piecePadding[unitSections[unit]]
                if (used + bottomPadding <= cap) end = next
            }
        }
        return end
    }

    // The column of every unit in [from, until) when the columns are filled in their order under cap, and how many
    // columns that takes, or null where something does not fit an empty column at all.
    fun fill(heights: IntArray, from: Int, until: Int, cap: Int): Pair<IntArray, Int>? {
        val cells = IntArray(until - from)
        var start = from
        var cell = 0
        while (start < until) {
            val end = columnEnd(heights, start, until, cap)
            if (end == start) return null
            for (unit in start until end) cells[unit - from] = cell
            cell++
            start = end
        }
        return cells to cell
    }

    // The lowest cap under which [from, until) fills no more than columns columns, or null where not even the screen does.
    fun lowestCap(heights: IntArray, from: Int, until: Int, columns: Int): Int? {
        val highestFill = fill(heights, from, until, maxRowHeight) ?: return null
        if (highestFill.second > columns) return null
        var tooLow = 0
        var enough = maxRowHeight
        while (enough - tooLow > 1) {
            val cap = tooLow + (enough - tooLow) / 2
            val cells = fill(heights, from, until, cap)?.second
            if (cells != null && cells <= columns) enough = cap else tooLow = cap
        }
        return enough
    }

    // The columns of [from, until) laid out in at most columns columns of that width, evened out, and how many columns
    // that is, or null where they do not fit the screen in that many.
    fun rowOf(from: Int, until: Int, columns: Int): Pair<IntArray, Int>? {
        val heights = heightsAt(columns)
        return fill(heights, from, until, lowestCap(heights, from, until, columns) ?: return null)
    }

    val rows = IntArray(unitCount)
    val columns = IntArray(unitCount)
    val columnCounts = mutableListOf<Int>()
    val fullHeights = heightsAt(columnCount)
    var start = 0
    while (start < unitCount) {
        var end = start
        var filledColumns = 0
        while (filledColumns < columnCount && end < unitCount) {
            val next = columnEnd(fullHeights, end, unitCount, maxRowHeight)
            if (next == end) break
            end = next
            filledColumns++
        }
        val (cells, rowColumnCount) = when {
            filledColumns == 0 -> {
                end = (start + 1 until unitCount).firstOrNull { isBreak(fullHeights, it) } ?: unitCount
                IntArray(end - start) to 1
            }
            filledColumns == columnCount && end < unitCount -> rowOf(start, end, columnCount)!!
            // A row of the same columns as the rest would leave a hole where its last ones go, so it takes as few as hold
            // it, which are wider. A lower count only makes its sections shorter, so the one the columns were filled in
            // always fits.
            else -> (1..filledColumns).firstNotNullOfOrNull { rowOf(start, end, it) } ?: rowOf(start, end, columnCount)!!
        }
        val row = columnCounts.size
        for (unit in start until end) {
            rows[unit] = row
            columns[unit] = cells[unit - start]
        }
        columnCounts += rowColumnCount
        start = end
    }
    return SectionGrid(rows = rows, columns = columns, columnCounts = columnCounts.toIntArray())
}

/**
 * The song flowed onto pages of a single column, the way [flowLikeAMagazine] flows it into rows of several: every page
 * filled down to [maxRowHeight], the height of the screen, a section that does not fit what is left of one running on
 * at the top of the next, so that a step turns a whole screen of the song rather than stopping at every section. A
 * window with room for one column is a phone held upright, which is where the reader has the least of the song in view
 * and the most steps to take.
 *
 * Two sections next to each other that are both [isNarrow] - as narrow as half the column, a run of chords, a short
 * refrain, a section folded down to its header - are set side by side instead, in a row of two columns joining the
 * page ([SectionGrid.joinsPrevious]), wherever that is lower than stacking them: a column wide enough for a line of
 * lyrics leaves most of itself empty beside them otherwise. Such a pair is never cut: where it does not fit what is
 * left of a page, the two are stacked instead wherever the first one can start on it, which fills the page, and moved
 * to the next page side by side only where it cannot.
 *
 * The units are those of [flowLikeAMagazine], [heightAt] being a unit's height in a row of one or two columns, and a
 * section may only be cut in front of a unit [isCuttableBefore] says so of and that has a height, with [piecePadding] at
 * both sides of a cut.
 * What cannot be cut to fit what is left of a page - a staff of tablature, a pair - starts the next page only where this
 * one is at least half full: a page mostly empty because the next thing on it is long is worse than a page that runs
 * on past the screen, which is paged through like a stretch too tall for any screen, and ends there.
 */
internal fun flowIntoPages(
    sectionStarts: IntArray,
    heightAt: (unit: Int, columnCount: Int) -> Int,
    isNarrow: (section: Int) -> Boolean,
    isCuttableBefore: (unit: Int) -> Boolean,
    piecePadding: IntArray,
    sectionGap: Int,
    maxRowHeight: Int,
): SectionGrid {
    val sectionCount = sectionStarts.size - 1
    if (sectionCount <= 0) return emptyGrid()
    val unitCount = sectionStarts[sectionCount]
    val heights = IntArray(unitCount) { heightAt(it, 1) }
    fun canCutBefore(unit: Int) = isCuttableBefore(unit) && heights[unit] > 0
    fun sectionHeight(section: Int, columnCount: Int) =
        (sectionStarts[section] until sectionStarts[section + 1]).sumOf { if (columnCount == 1) heights[it].toLong() else heightAt(it, columnCount).toLong() }
    // The height of the first piece the section can be cut into, the padding at its cut included.
    fun firstPieceHeight(section: Int): Long {
        val start = sectionStarts[section]
        val end = sectionStarts[section + 1]
        val cut = (start + 1 until end).firstOrNull { canCutBefore(it) } ?: return sectionHeight(section, 1)
        return (start until cut).sumOf { heights[it].toLong() } + piecePadding[section]
    }

    val rows = IntArray(unitCount)
    val columns = IntArray(unitCount)
    val columnCounts = mutableListOf<Int>()
    val joinsPrevious = mutableListOf<Boolean>()
    var used = 0L
    var isPageEmpty = true
    // Whether the last row of the page is a single column the next section can be stacked in.
    var isColumnOpen = false
    fun startPage() {
        used = 0L
        isPageEmpty = true
        isColumnOpen = false
    }
    fun openRow(columnCount: Int) {
        joinsPrevious += !isPageEmpty
        columnCounts += columnCount
        isPageEmpty = false
    }
    // Whether moving what does not fit on to the next page would leave this one more than half empty.
    fun isMostlyEmpty() = used * 2 < maxRowHeight
    fun place(from: Int, until: Int, column: Int) {
        for (unit in from until until) {
            rows[unit] = columnCounts.lastIndex
            columns[unit] = column
        }
    }

    var section = 0
    while (section < sectionCount) {
        if (section + 1 < sectionCount && isNarrow(section) && isNarrow(section + 1)) {
            val pairHeight = max(sectionHeight(section, 2), sectionHeight(section + 1, 2))
            val stackedHeight = sectionHeight(section, 1) + sectionGap + sectionHeight(section + 1, 1)
            val fitsPage = isPageEmpty || used + sectionGap + pairHeight <= maxRowHeight
            val startsStackedOnPage = !fitsPage && used + sectionGap + firstPieceHeight(section) <= maxRowHeight
            if (pairHeight < stackedHeight && pairHeight <= maxRowHeight && !startsStackedOnPage) {
                val runsOver = !fitsPage && isMostlyEmpty()
                if (!fitsPage && !runsOver) startPage()
                used += (if (isPageEmpty) 0 else sectionGap) + pairHeight
                openRow(columnCount = 2)
                place(sectionStarts[section], sectionStarts[section + 1], column = 0)
                place(sectionStarts[section + 1], sectionStarts[section + 2], column = 1)
                isColumnOpen = false
                if (runsOver) startPage()
                section += 2
                continue
            }
        }
        val end = sectionStarts[section + 1]
        var unit = sectionStarts[section]
        while (unit < end) {
            val padding = piecePadding[section].toLong()
            val topPadding = if (unit > sectionStarts[section]) padding else 0L
            val gap = if (isPageEmpty) 0L else sectionGap.toLong()
            val room = maxRowHeight - used - gap - topPadding
            // The furthest place the section may be cut at, or its end, that what is left of the page still holds.
            var fitsUntil = -1
            var fitHeight = 0L
            var height = 0L
            for (next in unit + 1..end) {
                height += heights[next - 1]
                if (height > room) break
                if (next == end || canCutBefore(next)) {
                    val bottomPadding = if (next == end) 0L else padding
                    if (height + bottomPadding <= room) {
                        fitsUntil = next
                        fitHeight = height + bottomPadding
                    }
                }
            }
            if (fitsUntil < 0 && !isPageEmpty && !isMostlyEmpty()) {
                startPage()
                continue
            }
            val isTooTall = fitsUntil < 0
            if (isTooTall) fitsUntil = (unit + 1 until end).firstOrNull { canCutBefore(it) } ?: end
            if (!isColumnOpen) openRow(columnCount = 1)
            used += gap + topPadding + fitHeight
            isColumnOpen = true
            place(unit, fitsUntil, column = 0)
            unit = fitsUntil
            if (isTooTall || unit < end) startPage()
        }
        section++
    }
    return SectionGrid(
        rows = rows,
        columns = columns,
        columnCounts = columnCounts.toIntArray(),
        joinsPrevious = joinsPrevious.toBooleanArray(),
        sharesKeyline = true,
    )
}
