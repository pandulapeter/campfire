/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.list

import com.pandulapeter.campfire.presentation.ui.components.only
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.UserPreferences

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

/**
 * The narrowest column a song card is laid out in where there are two or more: the room its title and artist need, and
 * the cover thumbnail in front of them while covers are shown. The key, tempo, and duration stand on a line of their own
 * under the artist, so they take nothing from either and add nothing here. The cover is counted whether or not the songs
 * on screen have one, since a column count that changed as covers arrived would move every card. The Setlists screen is
 * laid out by the same width ([ListLayout.of]): a setlist's slot number takes its room from the title, which is cut short
 * first anyway. A window narrower than one column, a phone held upright included, still gets one, as wide as the window.
 */
internal fun minSongColumnWidth(isCoverArtEnabled: Boolean) = CARD_BASE_WIDTH + if (isCoverArtEnabled) CARD_COVER_WIDTH else 0.dp

/**
 * The narrowest column of the Songs screen's grid: [songColumnWidth], less the artist's room while the list is sorted by
 * artist, where the cards leave the artist to the header above them. Only the grid is laid out by it; the side panel and
 * the expanded rail are decided by [songColumnWidth], so that a change of sort never moves the chrome around the list.
 * The room taken off is only what the card's artist line no longer needs, so a column is gained only where the window is
 * within that much of fitting one more.
 */
internal fun songGridColumnWidth(
    songColumnWidth: Dp,
    sortingMode: UserPreferences.SortingMode,
) = if (sortingMode == UserPreferences.SortingMode.BY_ARTIST) songColumnWidth - CARD_ARTIST_WIDTH else songColumnWidth

// Declared before the default below, which is worked out from them as the file is initialized.
private val CARD_BASE_WIDTH = 336.dp
private val CARD_ARTIST_WIDTH = 8.dp

/** The cover thumbnail and the gap after it (`CenteredSongCardContent`). */
private val CARD_COVER_WIDTH = 68.dp

/** The [minSongColumnWidth] with covers shown, as a fresh installation has it. */
internal val MIN_SONG_COLUMN_WIDTH = minSongColumnWidth(isCoverArtEnabled = true)

private const val MAX_COLUMN_COUNT = 4
