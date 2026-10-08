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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Whether the [ControlsSidePanel] fits into a screen of the given width: the list comes first, so the panel only gets
 * its space when at least [SIDE_PANEL_MIN_COLUMN_COUNT] columns of songs remain next to it. On narrower screens the
 * same controls are shown in a bottom sheet instead. The [FastScroller]'s column is taken off as well, since
 * [songListColumnCount] takes it off too, and a panel granted by a width the list then lays out one column fewer in
 * would leave the list with less than the columns it was promised.
 */
internal fun hasRoomForSidePanel(screenWidth: Dp) =
    columnCountForWidth(screenWidth - SIDE_PANEL_WIDTH - FAST_SCROLLER_WIDTH) >= SIDE_PANEL_MIN_COLUMN_COUNT

/**
 * What a list screen decides from the width it settles at: whether its filter side panel fits, how many columns the
 * Songs screen lays out with and without that panel next to it, and how many the Setlists screen's wider cards fit
 * (see [MIN_SETLIST_COLUMN_WIDTH]). The app's `CampfireScreens` works it out and hands it down in place of the width
 * itself, so that a window being resized, whose width changes on every frame, recomposes the screens only in the
 * frames one of these decisions changes in: the value is equal everywhere between two breakpoints.
 */
@Immutable
internal data class ListLayout(
    val hasRoomForSidePanel: Boolean,
    val columnCount: Int,
    val columnCountBesideSidePanel: Int,
    val setlistColumnCount: Int,
) {
    companion object {

        /**
         * @param settledWidth The width of the screen once the navigation bars have finished animating.
         * @param contentPadding The insets the screen hands to its list, whose start and end are not part of its width.
         */
        fun of(settledWidth: Dp, contentPadding: PaddingValues, layoutDirection: LayoutDirection) = ListLayout(
            hasRoomForSidePanel = hasRoomForSidePanel(settledWidth),
            columnCount = songListColumnCount(settledWidth, contentPadding, layoutDirection, isSidePanelVisible = false),
            columnCountBesideSidePanel = songListColumnCount(settledWidth, contentPadding, layoutDirection, isSidePanelVisible = true),
            setlistColumnCount = songListColumnCount(
                settledWidth = settledWidth,
                contentPadding = contentPadding,
                layoutDirection = layoutDirection,
                isSidePanelVisible = false,
                minColumnWidth = MIN_SETLIST_COLUMN_WIDTH,
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
    isSidePanelVisible: Boolean,
    minColumnWidth: Dp = MIN_SONG_COLUMN_WIDTH,
): Int {
    // The panel covers the end inset while it is visible (see besideSidePanel), so either way the same width goes.
    val sidePanelWidth = if (isSidePanelVisible) SIDE_PANEL_WIDTH else 0.dp
    return columnCountForWidth(
        width = settledWidth - contentPadding.calculateStartPadding(layoutDirection) - contentPadding.calculateEndPadding(layoutDirection) - sidePanelWidth - FAST_SCROLLER_WIDTH,
        minColumnWidth = minColumnWidth,
    )
}

internal val SIDE_PANEL_WIDTH = 320.dp

private const val SIDE_PANEL_MIN_COLUMN_COUNT = 2
