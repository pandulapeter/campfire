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

/**
 * The song flowed into one strip of columns read left to right (right to left in a song written that way), each filled
 * down to [maxColumnHeight], the height of the screen, for a song turned a column at a time: the column being read slides
 * to where the one before it was, so the eye never leaves the strip, and the strip is as short as the song allows, every
 * column but the last as full as it goes. Where the columns end is decided by the score of [LayoutScoring], as a
 * magazine's pages are (see [flowLikeAMagazine]), every column but the last of a stretch paying for the space it leaves,
 * so a column ends a line or two short rather than strand a line of a verse on either side of a cut. Unlike a page, a
 * column is never evened out with the ones beside it: there is no row for it to end level with, only the next column.
 *
 * The units are those of [flowLikeAMagazine], [heights] being each one's height at the width it is laid out at in the
 * strip, and a section may only be cut in front of a unit [isCuttableBefore] says so of and that has a height there,
 * with [piecePadding] at both sides of a cut. Every stretch of [timingStarts] (the sections a change of tempo or time
 * signature starts, 0 first) starts a column, so that the column being read is what the click follows.
 *
 * Null where something that cannot be cut is taller than a column: it could only be read in a column that scrolls,
 * which a song turned sideways has none of, so the song is turned a page at a time instead.
 */
internal fun flowIntoStrip(
    sectionStarts: IntArray,
    timingStarts: IntArray,
    heights: IntArray,
    isCuttableBefore: (unit: Int) -> Boolean,
    piecePadding: IntArray,
    sectionGap: Int,
    maxColumnHeight: Int,
    lineHeight: Int,
    lineCount: (unit: Int) -> Int,
    isStanzaBreakBefore: (unit: Int) -> Boolean,
): StripLayout? {
    if (sectionStarts.size < 2) return null
    val scoring = LayoutScoring(sectionStarts, lineHeight, lineCount, isStanzaBreakBefore)
    val unitCount = scoring.unitCount
    val flow = ColumnFlow(
        scoring = scoring,
        heights = heights,
        isCuttableBefore = isCuttableBefore,
        piecePadding = piecePadding,
        sectionGap = sectionGap,
    )
    val sectionCount = sectionStarts.size - 1
    val stretchStarts = (timingStarts.filter { it in 1 until sectionCount } + 0).distinct().sorted().map { sectionStarts[it] }
    val columns = IntArray(unitCount)
    var columnCount = 0
    stretchStarts.forEachIndexed { index, from ->
        val until = stretchStarts.getOrElse(index + 1) { unitCount }
        val choices = flow.choices(from, until, maxColumnHeight)
        val fewest = flow.fill(choices, until) ?: return null
        val placement = flow.program(choices, until, fewest..fewest, paidColumns = fewest - 1, cap = maxColumnHeight) ?: return null
        placement.cells.forEachIndexed { unit, column -> columns[from + unit] = columnCount + column }
        columnCount += placement.columnCount
    }
    return StripLayout(columns = columns, columnCount = columnCount)
}

/** The column of the strip every unit of a song is in ([columns]), as [flowIntoStrip] flowed it, and how many there are. */
internal class StripLayout(
    val columns: IntArray,
    val columnCount: Int,
)
