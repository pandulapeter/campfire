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
