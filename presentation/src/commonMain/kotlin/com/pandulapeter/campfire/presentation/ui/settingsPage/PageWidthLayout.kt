/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.settingsPage

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.preferences.THEME_COLOR_CHOICE_WIDTH
import com.pandulapeter.campfire.presentation.ui.screens.settings.SETTINGS_CATEGORY_PANE_WIDTH
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsCategoryPane
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsTabPage

/**
 * What the settings screen decides from the width it settles at without its insets, rather than the one it is being
 * measured at, which follows the navigation chrome while that animates: whether the tabs are a [SettingsCategoryPane]
 * or a row, and how many [MIN_COLUMN_WIDTH] wide columns a [SettingsPage] of the Metronome and the Tuner has room for
 * (the settings tabs themselves are always one column, see [SettingsTabPage]). The app's `CampfireScreens` works it out and hands it down in place of the width itself, so that a
 * window being resized recomposes the screen only in the frames one of these changes in.
 */
@Immutable
internal data class PageWidthLayout(
    val isWide: Boolean,
    val sectionColumns: Int,
) {
    /**
     * How wide a [SettingsPage] of this many sections gets at most, for what is pinned above one and has to end where
     * its columns do.
     */
    fun pageMaxWidth(sections: Int) =
        if (sectionColumns >= sections) MAX_COLUMN_WIDTH * sections + SECTION_GAP * (sections - 1) else MAX_COLUMN_WIDTH

    /**
     * [pageMaxWidth], carried on the spatial spring from one width to the other as a window is resized across the
     * width that sets the sections side by side, so that what is pinned above them grows and shrinks with the page
     * rather than jumping.
     */
    @Composable
    fun animatedPageMaxWidth(sections: Int): Dp = animateDpAsState(
        targetValue = pageMaxWidth(sections),
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    ).value

    companion object {

        /**
         * @param settledWidth The width of the screen once the navigation bars have finished animating.
         * @param contentPadding The insets the screen is laid out inside, whose start and end are not part of its width.
         */
        fun of(settledWidth: Dp, contentPadding: PaddingValues, layoutDirection: LayoutDirection): PageWidthLayout {
            val pageWidth = settledWidth - contentPadding.calculateStartPadding(layoutDirection) - contentPadding.calculateEndPadding(layoutDirection)
            return PageWidthLayout(
                isWide = pageWidth > SETTINGS_TAB_ROW_MAX_WIDTH + SETTINGS_CATEGORY_PANE_WIDTH,
                sectionColumns = (pageWidth / MIN_COLUMN_WIDTH).toInt(),
            )
        }
    }
}

/** Below this a column is too narrow for a switch next to two lines of description, so a tab stacks its sections. */
internal val MIN_COLUMN_WIDTH = 380.dp

/**
 * Above this a row is mostly the distance between its label and its control. It is as wide as every theme color disc
 * in one row, so that the color choice does not wrap wherever it has the room.
 */
internal val MAX_COLUMN_WIDTH = THEME_COLOR_CHOICE_WIDTH

/** The room between two sections, whether they are side by side or one above the other. */
internal val SECTION_GAP = 16.dp

/** What the first row of a page keeps free under the tabs. */
internal val PAGE_TOP_PADDING = 8.dp

/** Above this four tabs are four words spread apart rather than a row of tabs. */
internal val SETTINGS_TAB_ROW_MAX_WIDTH = MAX_COLUMN_WIDTH
