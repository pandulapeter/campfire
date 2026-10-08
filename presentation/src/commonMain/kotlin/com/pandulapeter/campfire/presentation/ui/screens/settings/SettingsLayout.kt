/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.THEME_COLOR_CHOICE_WIDTH

/**
 * What the settings screen decides from the width it settles at without its insets, rather than the one it is being
 * measured at, which follows the navigation chrome while that animates: whether the tabs are a [SettingsCategoryPane]
 * or a row, and how many [MIN_COLUMN_WIDTH] wide columns a [SettingsPage] has room for, across the whole width and
 * beside the pane. The app's `CampfireScreens` works it out and hands it down in place of the width itself, so that a
 * window being resized recomposes the screen only in the frames one of these changes in.
 */
@Immutable
internal data class SettingsWidthLayout(
    val isWide: Boolean,
    val sectionColumns: Int,
    val sectionColumnsBesidePane: Int,
) {
    /**
     * How wide a [SettingsPage] of this many sections gets at most, for what is pinned above one and has to end where
     * its columns do.
     */
    fun pageMaxWidth(sections: Int) =
        if (sectionColumns >= sections) MAX_COLUMN_WIDTH * sections + SECTION_GAP * (sections - 1) else MAX_COLUMN_WIDTH

    companion object {

        /**
         * @param settledWidth The width of the screen once the navigation bars have finished animating.
         * @param contentPadding The insets the screen is laid out inside, whose start and end are not part of its width.
         */
        fun of(settledWidth: Dp, contentPadding: PaddingValues, layoutDirection: LayoutDirection): SettingsWidthLayout {
            val pageWidth = settledWidth - contentPadding.calculateStartPadding(layoutDirection) - contentPadding.calculateEndPadding(layoutDirection)
            return SettingsWidthLayout(
                isWide = pageWidth > SETTINGS_TAB_ROW_MAX_WIDTH + SETTINGS_CATEGORY_PANE_WIDTH,
                sectionColumns = (pageWidth / MIN_COLUMN_WIDTH).toInt(),
                sectionColumnsBesidePane = ((pageWidth - SETTINGS_CATEGORY_PANE_WIDTH) / MIN_COLUMN_WIDTH).toInt(),
            )
        }
    }
}

/** Leaves a full [MAX_COLUMN_WIDTH] for a page as soon as the tab row reaches its width cap. */
internal val SETTINGS_CATEGORY_PANE_WIDTH = 180.dp

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
