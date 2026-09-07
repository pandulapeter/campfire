package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The columns of the song lists: as many [MIN_COLUMN_WIDTH] wide columns as fit next to each other, so that wide
 * windows show several songs side by side instead of one very long line of text. Same as [GridCells.Adaptive], except
 * that the count is capped, so that a maximized desktop window does not turn the list into a wall of narrow columns.
 */
internal object ListColumns : GridCells {

    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val count = ((availableSize + spacing) / (MIN_COLUMN_WIDTH.roundToPx() + spacing)).coerceIn(1, MAX_COLUMN_COUNT)
        val sizeWithoutSpacing = availableSize - spacing * (count - 1)
        val columnWidth = sizeWithoutSpacing / count
        val remainingPixels = sizeWithoutSpacing % count
        // The pixels left over by the integer division go to the first columns, so that the columns fill the width.
        return List(count) { columnWidth + if (it < remainingPixels) 1 else 0 }
    }
}

/**
 * The number of columns [ListColumns] produces in the given width, without the need to measure the list first.
 */
internal fun columnCountForWidth(width: Dp) = (width / MIN_COLUMN_WIDTH).toInt().coerceIn(1, MAX_COLUMN_COUNT)

private val MIN_COLUMN_WIDTH = 360.dp
private const val MAX_COLUMN_COUNT = 4
