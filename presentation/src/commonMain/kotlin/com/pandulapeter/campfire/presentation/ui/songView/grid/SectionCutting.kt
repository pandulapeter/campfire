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

import com.pandulapeter.campfire.presentation.ui.songView.firstSection.places
import com.pandulapeter.campfire.presentation.ui.songView.model.sectionChunkStarts
import kotlin.math.max

/**
 * The song flowed into rows of [columnCount] columns the way a magazine sets an article: every column filled down to
 * [maxRowHeight], the height of the screen, a section that does not fit what is left of a column running on at the top
 * of the next one - and from the last column of a row into the first one of the next row. A row is a page wherever the
 * rows are read one at a time, and turning one is the thing a reader without a pedal has to reach for the screen to do
 * and a reader with one has to keep up with, so the song takes as few pages as the columns can hold it on, and the
 * empty space is left where it does the least harm: at the end of the song.
 *
 * The grid is one of chunks: the units of section `s` are `sectionStarts[s]` to `sectionStarts[s + 1]`, [heightAt] the
 * height of a unit in a row of a given number of columns, and a section may only be cut in front of a unit
 * [isCuttableBefore] says so of (see [sectionChunkStarts], which keeps a line on either side of every cut) and that has
 * a height at that width - a slot of a run of tablature that the run has no row for there is no line to start a piece
 * with. A section on a card has [piecePadding] at both sides of every cut, as [arrange] places them.
 *
 * How many pages that takes is what filling every column as full as it goes reaches, which no other setting beats.
 * Where the columns of those pages end is then decided by the score of [LayoutScoring] ([lineHeight], [lineCount] and
 * [isStanzaBreakBefore] being what it is counted by): a dynamic program over the ends of every column that pays for
 * every cut, every stub a cut leaves and the empty space at the end of every column but the last page's, so a verse is
 * moved to the next column whole rather than leaving a line of itself behind, unless that costs a page. Every page is
 * then evened out on its own (see [ColumnFlow.evenOut]) so that its columns end close together. The last page, and one
 * ending before a stretch that cannot be cut and is taller than the screen on its own - a single system of tablature at
 * a large text size, which is a single column row of its own, paged through - takes the fewest columns that hold it,
 * which wrap the least, as a song that fits the screen does.
 */
internal fun flowLikeAMagazine(
    sectionStarts: IntArray,
    columnCount: Int,
    heightAt: (unit: Int, columnCount: Int) -> Int,
    isCuttableBefore: (unit: Int) -> Boolean,
    piecePadding: IntArray,
    sectionGap: Int,
    maxRowHeight: Int,
    lineHeight: Int,
    lineCount: (unit: Int) -> Int,
    isStanzaBreakBefore: (unit: Int) -> Boolean,
): SectionGrid {
    if (sectionStarts.size < 2) return emptyGrid()
    val scoring = LayoutScoring(sectionStarts, lineHeight, lineCount, isStanzaBreakBefore)
    val unitCount = scoring.unitCount
    // Measured only for the column counts a row is actually tried in, since every width is a pass over the song.
    val flows = arrayOfNulls<ColumnFlow>(columnCount)
    fun flowAt(columns: Int) = flows[columns - 1] ?: ColumnFlow(
        scoring = scoring,
        heights = IntArray(unitCount) { heightAt(it, columns) },
        isCuttableBefore = isCuttableBefore,
        piecePadding = piecePadding,
        sectionGap = sectionGap,
    ).also { flows[columns - 1] = it }
    val fullFlow = flowAt(columnCount)
    val choices = fullFlow.choices(0, unitCount, maxRowHeight)
    // A stretch from one place a column may start to the next that not even an empty column holds can be in no column.
    fun isTooTall(unit: Int) = fullFlow.isBreak(unit) && choices.endsAt(unit).isEmpty()

    val rows = IntArray(unitCount)
    val columns = IntArray(unitCount)
    val columnCounts = mutableListOf<Int>()
    fun addRow(from: Int, cells: IntArray, rowColumnCount: Int) {
        cells.forEachIndexed { index, cell ->
            rows[from + index] = columnCounts.size
            columns[from + index] = cell
        }
        columnCounts += rowColumnCount
    }

    var start = 0
    while (start < unitCount) {
        if (isTooTall(start)) {
            val end = (start + 1 until unitCount).firstOrNull { fullFlow.isBreak(it) } ?: unitCount
            addRow(start, IntArray(end - start), 1)
            start = end
            continue
        }
        var end = start + 1
        while (end < unitCount && !isTooTall(end)) end++
        val stretchChoices = fullFlow.choices(start, end, maxRowHeight)
        val pageCount = (fullFlow.fill(stretchChoices, end)!! + columnCount - 1) / columnCount
        val paidColumns = (pageCount - 1) * columnCount
        val placement = fullFlow.program(stretchChoices, end, paidColumns + 1..pageCount * columnCount, paidColumns, maxRowHeight)!!
        var pageStart = start
        for (page in 0 until pageCount) {
            val pageEnd = if (page == pageCount - 1) end else start + placement.cells.indexOfFirst { it >= (page + 1) * columnCount }
            val evened = if (page < pageCount - 1) {
                fullFlow.evenOut(pageStart, pageEnd, columnCount, maxRowHeight, lineHeight)
            } else {
                // A page of the same columns as the rest would leave a hole where its last ones go, so it takes as few
                // as hold it, which are wider.
                (1..columnCount).firstNotNullOfOrNull { flowAt(it).evenOut(pageStart, pageEnd, it, maxRowHeight, lineHeight) }
            }
            if (evened != null) {
                addRow(pageStart, evened.cells, evened.columnCount)
            } else {
                // Only a last page holding fewer places to cut than it has columns, set as the whole stretch was.
                val pageCells = IntArray(pageEnd - pageStart) { placement.cells[pageStart - start + it] - page * columnCount }
                addRow(pageStart, pageCells, pageCells.max() + 1)
            }
            pageStart = pageEnd
        }
        start = end
    }
    return SectionGrid(rows = rows, columns = columns, columnCounts = columnCounts.toIntArray())
}

/**
 * The song flowed onto pages of a single column, the way [flowLikeAMagazine] flows it into rows of several: every page
 * as full as the fewest pages need, down to [maxRowHeight], the height of the screen, a section that does not fit what
 * is left of one running on at the top of the next, so that a step turns a whole screen of the song rather than stopping
 * at every section. A window with room for one column is a phone held upright, which is where the reader has the least
 * of the song in view and the most steps to take. Where the pages end is decided by the same score (see
 * [LayoutScoring]), each page but the last paying for the empty space it leaves, so a page ends a line or two short
 * rather than strand a line of a verse on either side of the cut.
 *
 * Two sections next to each other that are both [isNarrow] - as narrow as half the column, a run of chords, a short
 * refrain, a section folded down to its header - are set side by side instead, in a row of two columns joining the
 * page ([SectionGrid.joinsPrevious]), wherever that is lower than stacking them: a column wide enough for a line of
 * lyrics leaves most of itself empty beside them otherwise. Such a pair is never cut: where it does not fit what is
 * left of a page, the two are stacked instead wherever the first one can start on it, and the pages are then ended
 * where the score says, which is the next page for the pair where the stub a stacked cut leaves costs more than the
 * space it saves.
 *
 * The units are those of [flowLikeAMagazine], [heightAt] being a unit's height in a row of one or two columns, and a
 * section may only be cut in front of a unit [isCuttableBefore] says so of and that has a height, with [piecePadding] at
 * both sides of a cut.
 * What cannot be cut to fit what is left of a page - a staff of tablature, a pair - may start the next page only where
 * this one is at least half full: a page mostly empty because the next thing on it is long is worse than a page that
 * runs on past the screen, which is paged through like a stretch too tall for any screen, and ends there.
 */
internal fun flowIntoPages(
    sectionStarts: IntArray,
    heightAt: (unit: Int, columnCount: Int) -> Int,
    isNarrow: (section: Int) -> Boolean,
    isCuttableBefore: (unit: Int) -> Boolean,
    piecePadding: IntArray,
    sectionGap: Int,
    maxRowHeight: Int,
    lineHeight: Int,
    lineCount: (unit: Int) -> Int,
    isStanzaBreakBefore: (unit: Int) -> Boolean,
): SectionGrid {
    val sectionCount = sectionStarts.size - 1
    if (sectionCount <= 0) return emptyGrid()
    val scoring = LayoutScoring(sectionStarts, lineHeight, lineCount, isStanzaBreakBefore)
    val unitCount = scoring.unitCount
    val unitSections = scoring.unitSections
    val heights = IntArray(unitCount) { heightAt(it, 1) }
    fun isBreak(unit: Int) = scoring.isSectionStart(unit) || isCuttableBefore(unit) && heights[unit] > 0
    val nextBreaks = IntArray(unitCount).also { table ->
        var next = unitCount
        for (unit in unitCount - 1 downTo 0) {
            table[unit] = next
            if (isBreak(unit)) next = unit
        }
    }
    fun sectionHeight(section: Int, columnCount: Int) =
        (sectionStarts[section] until sectionStarts[section + 1]).sumOf { if (columnCount == 1) heights[it].toLong() else heightAt(it, columnCount).toLong() }
    // How tall a section and the one after it are side by side, where they are set that way, or -1.
    val pairHeights = LongArray(sectionCount) { section ->
        if (section + 1 < sectionCount && isNarrow(section) && isNarrow(section + 1)) {
            val pairHeight = max(sectionHeight(section, 2), sectionHeight(section + 1, 2))
            val stackedHeight = sectionHeight(section, 1) + sectionGap + sectionHeight(section + 1, 1)
            if (pairHeight < stackedHeight && pairHeight <= maxRowHeight) pairHeight else -1
        } else {
            -1
        }
    }
    fun bottomPaddingAt(unit: Int) = if (scoring.isSectionStart(unit)) 0 else piecePadding[unitSections[unit - 1]]

    // Walks the page starting at from, handing onBlock every block it is stacked of in its order - a pair, or the units
    // from one place a page may end to the next - and onEnd every place the page may end, with its height, until the
    // page is full. A block that does not fit what is left of a page that is mostly empty runs it on past the screen,
    // which is an end of its own, the last one. So does a page whose blocks fit but whose cut's padding does not, when it
    // has no other end: it ends at that cut, running over, rather than leaving the rest of the song without a page.
    // Nothing branches: a pair that fits is always set side by side.
    fun walk(from: Int, onBlock: (isPair: Boolean, start: Int, end: Int) -> Unit, onEnd: (end: Int, height: Long, runsOver: Boolean) -> Unit) {
        var used = 0L
        var unit = from
        var hasEnded = false
        // The end reported only once the page is full, since an end after it from the same start may still fit.
        var pendingEnd = -1
        var pendingHeight = 0L
        fun endPending() {
            if (!hasEnded && pendingEnd >= 0) onEnd(pendingEnd, pendingHeight, true)
        }
        while (unit < unitCount) {
            val section = unitSections[unit]
            val isSectionStart = sectionStarts[section] == unit
            val isPageEmpty = unit == from
            val gap = if (isPageEmpty || !isSectionStart) 0L else sectionGap.toLong()
            fun isMostlyEmpty() = isPageEmpty || used * 2 < maxRowHeight
            val pairHeight = if (isSectionStart) pairHeights[section] else -1
            if (pairHeight >= 0) {
                val pairEnd = sectionStarts[section + 2]
                if (used + gap + pairHeight <= maxRowHeight) {
                    used += gap + pairHeight
                    unit = pairEnd
                    onBlock(true, sectionStarts[section], pairEnd)
                    onEnd(unit, used, false)
                    hasEnded = true
                    continue
                }
                val firstPieceEnd = nextBreaks[unit]
                val firstPiece = (unit until firstPieceEnd).sumOf { heights[it].toLong() } + bottomPaddingAt(firstPieceEnd)
                if (used + gap + firstPiece > maxRowHeight) {
                    if (isMostlyEmpty()) {
                        onBlock(true, sectionStarts[section], pairEnd)
                        onEnd(pairEnd, used + gap + pairHeight, true)
                    } else {
                        endPending()
                    }
                    return
                }
            }
            val blockEnd = nextBreaks[unit]
            val topPadding = if (isPageEmpty && !isSectionStart) piecePadding[section].toLong() else 0L
            val added = gap + topPadding + (unit until blockEnd).sumOf { heights[it].toLong() }
            if (used + added > maxRowHeight) {
                if (isMostlyEmpty()) {
                    onBlock(false, unit, blockEnd)
                    onEnd(blockEnd, used + added + bottomPaddingAt(blockEnd), true)
                } else {
                    endPending()
                }
                return
            }
            used += added
            onBlock(false, unit, blockEnd)
            unit = blockEnd
            val height = used + bottomPaddingAt(unit)
            if (height <= maxRowHeight) {
                onEnd(unit, height, false)
                hasEnded = true
            } else {
                pendingEnd = unit
                pendingHeight = height
            }
        }
    }

    // The fewest pages, and then the lowest score, any setting of the units before each place a page may end takes, and
    // where its last page starts. A page that runs on past the screen counts as every page it is paged through in.
    val pageCounts = IntArray(unitCount + 1) { Int.MAX_VALUE }.also { it[0] = 0 }
    val costs = DoubleArray(unitCount + 1) { Double.POSITIVE_INFINITY }.also { it[0] = 0.0 }
    val pageStarts = IntArray(unitCount + 1) { -1 }
    for (from in 0 until unitCount) {
        if (pageCounts[from] == Int.MAX_VALUE) continue
        walk(from, onBlock = { _, _, _ -> }) { end, height, runsOver ->
            val pageCount = pageCounts[from] + if (runsOver) pagesOf(height.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), maxRowHeight) else 1
            // The last page leaves the rest of the screen to the end of the song, which costs nothing.
            val emptyCost = if (runsOver || end == unitCount) 0.0 else scoring.emptyCost((maxRowHeight - height).toInt())
            val cost = costs[from] + scoring.columnCost(from, end) + emptyCost
            // Of two that cost the same, the one whose first pages hold more.
            if (pageCount < pageCounts[end] || pageCount == pageCounts[end] && cost <= costs[end]) {
                pageCounts[end] = pageCount
                costs[end] = cost
                pageStarts[end] = from
            }
        }
    }
    val pageEnds = generateSequence(unitCount) { end -> pageStarts[end].takeIf { it > 0 } }.toList().reversed()

    val rows = IntArray(unitCount)
    val columns = IntArray(unitCount)
    val columnCounts = mutableListOf<Int>()
    val joinsPrevious = mutableListOf<Boolean>()
    var pageStart = 0
    for (pageEnd in pageEnds) {
        var isColumnOpen = false
        walk(
            from = pageStart,
            onBlock = { isPair, start, end ->
                if (start < pageEnd) {
                    if (isPair || !isColumnOpen) {
                        joinsPrevious += start != pageStart
                        columnCounts += if (isPair) 2 else 1
                    }
                    for (unit in start until end) {
                        rows[unit] = columnCounts.lastIndex
                        columns[unit] = if (isPair && unitSections[unit] != unitSections[start]) 1 else 0
                    }
                    isColumnOpen = !isPair
                }
            },
            onEnd = { _, _, _ -> },
        )
        pageStart = pageEnd
    }
    return SectionGrid(
        rows = rows,
        columns = columns,
        columnCounts = columnCounts.toIntArray(),
        joinsPrevious = joinsPrevious.toBooleanArray(),
        sharesKeyline = true,
    )
}
