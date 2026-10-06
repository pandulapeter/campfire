/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The columns of the song lists: [count] equally wide columns filling the available width, so that wide windows show
 * several songs side by side instead of one very long line of text.
 *
 * The count is decided by the caller from the width the screen settles at (see [songListColumnCount]) instead of
 * being measured from the width the grid is given. That width follows the navigation bars while they animate, so a
 * grid that picked its own count would start a navigation transition laid out for one count and switch to another
 * halfway through it, moving every item that is already on screen.
 */
internal data class ListColumns(private val count: Int) : GridCells {

    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val sizeWithoutSpacing = availableSize - spacing * (count - 1)
        val columnWidth = sizeWithoutSpacing / count
        val remainingPixels = sizeWithoutSpacing % count
        // The pixels left over by the integer division go to the first columns, so that the columns fill the width.
        return List(count) { columnWidth + if (it < remainingPixels) 1 else 0 }
    }
}

/**
 * The number of [minColumnWidth] wide columns that fit into the given width. Capped, so that a maximized desktop
 * window does not turn the list into a wall of narrow columns.
 */
internal fun columnCountForWidth(
    width: Dp,
    minColumnWidth: Dp = MIN_SONG_COLUMN_WIDTH,
) = (width / minColumnWidth).toInt().coerceIn(1, MAX_COLUMN_COUNT)

/** The narrowest column a song card is laid out in, which a phone held upright still fits one of. */
internal val MIN_SONG_COLUMN_WIDTH = 360.dp

/**
 * The narrowest column the Setlists screen lays its song cards out in. A card there carries more on one line than on
 * the Songs screen — the slot number before the title, and the key, the tempo and the duration after the artist — so at
 * the songs' width a laptop's maximized window fits four columns in which those lines are cut short. A phone is
 * unaffected, since it fits one column at either width.
 */
internal val MIN_SETLIST_COLUMN_WIDTH = 416.dp
private const val MAX_COLUMN_COUNT = 4
