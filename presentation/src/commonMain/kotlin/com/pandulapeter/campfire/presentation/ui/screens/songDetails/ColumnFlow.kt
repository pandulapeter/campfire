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
 * The units of a song as they stack in columns of one width, [heights] being every unit's height there: where a column
 * may end, how tall it is, and the cheapest way of setting a stretch of the song in a number of columns by the score of
 * [scoring]. A section may only be cut in front of a unit [isCuttableBefore] says so of and that has a height at this
 * width, a section on a card has [piecePadding] at both sides of every cut, and two sections of a column are [sectionGap]
 * apart, as [arrange] places them.
 */
internal class ColumnFlow(
    private val scoring: LayoutScoring,
    private val heights: IntArray,
    private val isCuttableBefore: (unit: Int) -> Boolean,
    private val piecePadding: IntArray,
    private val sectionGap: Int,
) {

    /** Whether a column may start at [unit]: a section starts there, or the section may be cut in front of it. */
    fun isBreak(unit: Int) = scoring.isSectionStart(unit) || isCuttableBefore(unit) && heights[unit] > 0

    /**
     * Every place a column starting at a unit from [from] until [until] may end, no further than [until], and how tall
     * the column is then, as long as that is no more than [cap]. A column starting inside a section starts with the
     * padding of its card, and one ending inside a section ends with it. Only the units a column may start at have any.
     */
    fun choices(from: Int, until: Int, cap: Int): ColumnChoices {
        val size = until - from
        val ends = Array(size) { NO_CHOICES }
        val endHeights = Array(size) { NO_CHOICES }
        val endBuffer = IntArray(size)
        val heightBuffer = IntArray(size)
        for (start in from until until) {
            if (start > from && !isBreak(start)) continue
            var count = 0
            var used = if (scoring.isSectionStart(start)) 0L else piecePadding[scoring.unitSections[start]].toLong()
            for (unit in start until until) {
                if (unit > start && scoring.isSectionStart(unit)) used += sectionGap
                used += heights[unit]
                if (used > cap) break
                val next = unit + 1
                if (next == until || isBreak(next)) {
                    val bottomPadding = if (scoring.isSectionStart(next)) 0 else piecePadding[scoring.unitSections[unit]]
                    if (used + bottomPadding <= cap) {
                        endBuffer[count] = next
                        heightBuffer[count] = (used + bottomPadding).toInt()
                        count++
                    }
                }
            }
            ends[start - from] = endBuffer.copyOf(count)
            endHeights[start - from] = heightBuffer.copyOf(count)
        }
        return ColumnChoices(from = from, ends = ends, heights = endHeights)
    }

    /**
     * How many columns [choices] fill from where they start until [until] when every column is filled as full as it
     * goes, which is the fewest any setting of them can, or null where something does not fit an empty column at all.
     */
    fun fill(choices: ColumnChoices, until: Int): Int? {
        var start = choices.from
        var columns = 0
        while (start < until) {
            start = choices.endsAt(start).lastOrNull { it <= until } ?: return null
            columns++
        }
        return columns
    }

    /**
     * The cheapest setting of the units from where [choices] start until [until] in a number of columns within
     * [columnCounts], or null where there is none: a dynamic program over where every column ends, the way a paragraph
     * is broken into lines, every column paying for its cuts and its pieces and the first [paidColumns] of them for the
     * empty space they leave under [cap] too, which is what fills them and leaves the slack to the ones after. Of two
     * settings that cost the same, the one whose first columns hold more is taken.
     */
    fun program(choices: ColumnChoices, until: Int, columnCounts: IntRange, paidColumns: Int, cap: Int): ColumnPlacement? {
        val from = choices.from
        val size = until - from
        var costs = DoubleArray(size + 1) { Double.POSITIVE_INFINITY }.also { it[0] = 0.0 }
        var nextCosts = DoubleArray(size + 1)
        // starts[k][i] is where the column k + 1 starts in the cheapest setting of the first i units in k + 1 columns.
        val starts = ArrayList<IntArray>(columnCounts.last)
        var bestCount = -1
        var bestCost = Double.POSITIVE_INFINITY
        for (column in 0 until columnCounts.last) {
            nextCosts.fill(Double.POSITIVE_INFINITY)
            val columnStarts = IntArray(size + 1) { -1 }
            var isReachable = false
            for (start in 0 until size) {
                val base = costs[start]
                if (base == Double.POSITIVE_INFINITY) continue
                val ends = choices.ends[start]
                val heights = choices.heights[start]
                for (choice in ends.indices) {
                    val end = ends[choice]
                    if (end > until) break
                    val emptyCost = if (column < paidColumns) scoring.emptyCost(cap - heights[choice]) else 0.0
                    val cost = base + scoring.columnCost(from + start, end) + emptyCost
                    if (cost <= nextCosts[end - from]) {
                        nextCosts[end - from] = cost
                        columnStarts[end - from] = start
                        isReachable = true
                    }
                }
            }
            starts += columnStarts
            if (column + 1 in columnCounts && nextCosts[size] < bestCost) {
                bestCost = nextCosts[size]
                bestCount = column + 1
            }
            if (!isReachable) break
            costs = nextCosts.also { nextCosts = costs }
        }
        if (bestCount < 0) return null
        val cells = IntArray(size)
        val columnHeights = IntArray(bestCount)
        var cutCost = 0.0
        var end = size
        for (column in bestCount - 1 downTo 0) {
            val start = starts[column][end]
            cells.fill(column, start, end)
            columnHeights[column] = choices.heights[start][choices.ends[start].indexOf(from + end)]
            cutCost += scoring.columnCost(from + start, from + end)
            end = start
        }
        return ColumnPlacement(cells = cells, columnCount = bestCount, cutCost = cutCost, columnHeights = columnHeights)
    }

    /**
     * The units from [from] until [until] set in exactly [columnCount] columns as evenly as their cuts allow, or null
     * where no setting of them in that many fits [maxCap]. The lowest height they fit under in that many columns is found
     * first; the setting at that height, at one, two and three [lineHeight]s more and at [maxCap] is then taken by what
     * its cuts cost and how far apart its columns end, which is what lets a column end a line short rather than strand
     * one, and still lines the bottoms up where no cut is the price.
     */
    fun evenOut(from: Int, until: Int, columnCount: Int, maxCap: Int, lineHeight: Int): ColumnPlacement? {
        fun settingUnder(cap: Int) = program(choices(from, until, cap), until, columnCount..columnCount, paidColumns = columnCount, cap = cap)
        if (settingUnder(maxCap) == null) return null
        var tooLow = 0
        var enough = maxCap
        while (enough - tooLow > 1) {
            val cap = tooLow + (enough - tooLow) / 2
            if (settingUnder(cap) != null) enough = cap else tooLow = cap
        }
        val caps = (0 until EVENED_OUT_LINES).map { (enough + it.toLong() * lineHeight).coerceAtMost(maxCap.toLong()).toInt() } + maxCap
        // Of two that cost the same, the lower one, whose columns end closer to each other.
        return caps.distinct().mapNotNull(::settingUnder).minBy { it.evenedOutCost() }
    }

    private fun ColumnPlacement.evenedOutCost() = cutCost + scoring.spreadCost(columnHeights.max() - columnHeights.min())
}

/**
 * Where a column starting at each unit from [from] on may end ([ends], in their order) and how tall it is then
 * ([heights]), as [ColumnFlow.choices] found them.
 */
internal class ColumnChoices(
    val from: Int,
    val ends: Array<IntArray>,
    val heights: Array<IntArray>,
) {

    /** Where a column starting at [unit] may end. */
    fun endsAt(unit: Int) = ends[unit - from]
}

/**
 * A setting of units in [columnCount] columns: the column of every unit ([cells], counted from the first unit set), what
 * its cuts and pieces cost and how tall every column is.
 */
internal class ColumnPlacement(
    val cells: IntArray,
    val columnCount: Int,
    val cutCost: Double,
    val columnHeights: IntArray,
)

private val NO_CHOICES = IntArray(0)

/** The heights a page is evened out under: the lowest it fits, and as many line heights more as this, less one. */
private const val EVENED_OUT_LINES = 4
