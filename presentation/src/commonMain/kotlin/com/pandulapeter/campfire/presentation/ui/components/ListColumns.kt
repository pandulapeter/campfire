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
 * The number of [MIN_COLUMN_WIDTH] wide columns that fit into the given width. Capped, so that a maximized desktop
 * window does not turn the list into a wall of narrow columns.
 */
internal fun columnCountForWidth(width: Dp) = (width / MIN_COLUMN_WIDTH).toInt().coerceIn(1, MAX_COLUMN_COUNT)

private val MIN_COLUMN_WIDTH = 360.dp
private const val MAX_COLUMN_COUNT = 4
