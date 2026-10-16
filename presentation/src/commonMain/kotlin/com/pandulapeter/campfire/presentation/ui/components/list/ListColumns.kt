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
 * The narrowest column a song card is laid out in where there are two or more, worked out from what the card says on
 * its second line ([UserPreferences.CardDetails], the key only with the chords and the tempo by default only with the
 * metronome) and from the cover thumbnail in front of its text while covers are shown, so that a card that says less is
 * given less room and a window fits more of them side by side, while one that says more keeps its artist from being cut
 * short. The cover is counted whether or not the songs on screen have one, since a column count that changed as covers
 * arrived would move every card. The Songs and the Setlists screens work it out the same way, so that cards showing the same details
 * flow into the same columns on both: a setlist's slot number takes its room from the title, which is cut short first
 * anyway. A window narrower than one column, a phone held upright included, still gets one, as wide as the window.
 */
internal fun minSongColumnWidth(
    details: UserPreferences.CardDetails,
    areChordsEnabled: Boolean,
    isMetronomeEnabled: Boolean,
    isCoverArtEnabled: Boolean,
) = CARD_BASE_WIDTH +
    (if (isCoverArtEnabled) CARD_COVER_WIDTH else 0.dp) +
    (if (areChordsEnabled && details.isKeyShown) CARD_KEY_WIDTH else 0.dp) +
    (if (details.isTempoShown(isMetronomeEnabled)) CARD_TEMPO_WIDTH else 0.dp) +
    (if (details.isDurationShown) CARD_DURATION_WIDTH else 0.dp)

// Declared before the two defaults below, which are worked out from them as the file is initialized.
private val CARD_BASE_WIDTH = 368.dp
private val CARD_KEY_WIDTH = 16.dp
private val CARD_TEMPO_WIDTH = 16.dp
private val CARD_DURATION_WIDTH = 32.dp

/** The cover thumbnail and the gap after it (`CenteredSongCardContent`). */
private val CARD_COVER_WIDTH = 56.dp

/** The Songs screen's [minSongColumnWidth] with every feature on and the cards as a fresh installation has them. */
internal val MIN_SONG_COLUMN_WIDTH = minSongColumnWidth(
    details = UserPreferences.CardDetails.SongsDefault,
    areChordsEnabled = true,
    isMetronomeEnabled = true,
    isCoverArtEnabled = true,
)

/** The Setlists screen's [minSongColumnWidth] with every feature on and the cards as a fresh installation has them. */
internal val MIN_SETLIST_COLUMN_WIDTH = minSongColumnWidth(
    details = UserPreferences.CardDetails.SetlistsDefault,
    areChordsEnabled = true,
    isMetronomeEnabled = true,
    isCoverArtEnabled = true,
)

private const val MAX_COLUMN_COUNT = 4
