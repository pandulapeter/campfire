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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * What a list screen decides from the width it settles at: how many columns the Songs and Setlists screens lay their
 * cards out in. The app's `CampfireScreens` works it out and hands it down in place of the width itself, so that a window
 * being resized, whose width changes on every frame, recomposes the screens only in the frames the count changes in: the
 * value is equal everywhere between two breakpoints.
 */
@Immutable
internal data class ListLayout(
    val columnCount: Int,
) {
    companion object {

        /**
         * @param settledWidth The width of the screen once the navigation bars have finished animating.
         * @param contentPadding The insets the screen hands to its list, whose start and end are not part of its width.
         * @param songColumnWidth The narrowest column, see [minSongColumnWidth]. It is the same whichever way the songs are
         *   sorted, and the Setlists screen's cards are laid out by it too, whatever details they show: those stand on a
         *   line of their own, under the artist, and take nothing from the title's or the artist's room. So a change of
         *   sort never reflows the songs, and the two screens fit the same number of cards side by side.
         */
        fun of(
            settledWidth: Dp,
            contentPadding: PaddingValues,
            layoutDirection: LayoutDirection,
            songColumnWidth: Dp = MIN_SONG_COLUMN_WIDTH,
        ) = ListLayout(
            columnCount = songListColumnCount(
                settledWidth = settledWidth,
                contentPadding = contentPadding,
                layoutDirection = layoutDirection,
                minColumnWidth = songColumnWidth,
            ),
        )
    }
}

/**
 * The number of columns the song lists lay their items out in, measured from the width the screen settles at rather
 * than from the width the grid currently has, see [ListColumns]. The [FastScroller] occupies the grid's end padding,
 * not song card width.
 */
internal fun songListColumnCount(
    settledWidth: Dp,
    contentPadding: PaddingValues,
    layoutDirection: LayoutDirection,
    minColumnWidth: Dp = MIN_SONG_COLUMN_WIDTH,
) = columnCountForWidth(
    width = settledWidth - contentPadding.calculateStartPadding(layoutDirection) - contentPadding.calculateEndPadding(layoutDirection) - FAST_SCROLLER_WIDTH,
    minColumnWidth = minColumnWidth,
)
