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

/** Where the bars of one grid line stand once it is broken into rows ([gridRowPositions]), and how tall the rows make it. */
internal data class GridRowPositions(
    val bars: List<Bar>,
    val height: Int,
) {

    /** A bar's top left corner, and whether it [opensRow], with its wrapped opening drawn in front of it. */
    data class Bar(
        val x: Int,
        val y: Int,
        val opensRow: Boolean,
    )
}

/**
 * The bars of a grid line placed into rows [maxWidth] wide, as many whole bars a row as fit. A bar that starts a row
 * after the first stands after its opening bar line ([openingWidths], none for the line's first bar), and a bar too wide
 * for any row starts one of its own.
 */
internal fun gridRowPositions(
    barWidths: List<Int>,
    barHeights: List<Int>,
    openingWidths: List<Int>,
    maxWidth: Int,
): GridRowPositions {
    val bars = mutableListOf<GridRowPositions.Bar>()
    var x = 0
    var y = 0
    var rowHeight = 0
    barWidths.indices.forEach { index ->
        if (index > 0 && x + barWidths[index] > maxWidth) {
            y += rowHeight
            x = 0
            rowHeight = 0
        }
        val opensRow = x == 0
        if (opensRow) x += openingWidths[index]
        bars += GridRowPositions.Bar(x = x, y = y, opensRow = opensRow)
        x += barWidths[index]
        rowHeight = maxOf(rowHeight, barHeights[index])
    }
    return GridRowPositions(bars = bars, height = y + rowHeight)
}
