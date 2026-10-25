/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import com.pandulapeter.campfire.chordpro.model.TabRowLine

/**
 * The strokes a row of tablature is drawn with in place of its dashes and bar lines, in columns of its monospaced text
 * and indices of its lines: what a printed tab looks like, a string running unbroken from bar to bar and every bar
 * line crossing the whole staff, where the characters leave a gap between every dash and a pipe on every string.
 * Everything else on a staff line (string names, frets, the letters of a hammer-on or a dead note, a repeat count) is
 * still text, drawn over the gap its cell leaves in the string, which is how a fret number interrupts a string in print.
 */
internal data class TabStaffGeometry(
    val strings: List<TabString>,
    val bars: List<TabBar>,
) {

    /**
     * A stretch of one string, from the left edge of the column [from] to [to], both in columns. A stretch that ends on a
     * bar line ends at its middle, so that nothing sticks out past the bar that opens or closes a staff.
     */
    data class TabString(val line: Int, val from: Float, val to: Float)

    /**
     * A bar line in the middle of [column], from the string of [fromLine] down to that of [toLine]: every string of a
     * staff that has a bar in the column, one under the other. A bar on a string with none above or under it is drawn
     * as tall as its line, where [fromLine] and [toLine] are the same.
     */
    data class TabBar(val column: Int, val fromLine: Int, val toLine: Int)
}

/** The strokes of [row], one of the rows a run of tablature is cut into. */
internal fun tabStaffGeometryOf(row: List<TabRowLine>): TabStaffGeometry {
    val strings = mutableListOf<TabStaffGeometry.TabString>()
    row.forEachIndexed { index, line ->
        if (!line.isStaffLine) return@forEachIndexed
        var column = 0
        while (column < line.text.length) {
            if (!line.text[column].isStroke) {
                column++
                continue
            }
            val start = column
            while (column < line.text.length && line.text[column].isStroke) column++
            val from = if (line.text[start] == BAR) start + HALF_COLUMN else start.toFloat()
            val to = if (line.text[column - 1] == BAR) column - 1 + HALF_COLUMN else column.toFloat()
            if (to > from) strings += TabStaffGeometry.TabString(line = index, from = from, to = to)
        }
    }
    val bars = mutableListOf<TabStaffGeometry.TabBar>()
    val columns = row.filter { it.isStaffLine }.maxOfOrNull { it.text.length } ?: 0
    for (column in 0 until columns) {
        var index = 0
        while (index < row.size) {
            if (!row[index].hasBarAt(column)) {
                index++
                continue
            }
            val fromLine = index
            while (index + 1 < row.size && row[index + 1].hasBarAt(column)) index++
            bars += TabStaffGeometry.TabBar(column = column, fromLine = fromLine, toLine = index)
            index++
        }
    }
    return TabStaffGeometry(strings = strings, bars = bars)
}

/**
 * The text of a line of a row as it is drawn over its strokes: a staff line with its dashes and bar lines blanked out,
 * which keeps every other character in its column, since the text is monospaced. Any other line is drawn as it is.
 */
internal val TabRowLine.drawnText
    get() = if (isStaffLine) CharArray(text.length) { index -> text[index].takeUnless { it.isStroke } ?: ' ' }.concatToString() else text

private fun TabRowLine.hasBarAt(column: Int) = isStaffLine && text.getOrNull(column) == BAR

private val Char.isStroke get() = this == DASH || this == BAR

private const val BAR = '|'
private const val DASH = '-'
private const val HALF_COLUMN = 0.5f
