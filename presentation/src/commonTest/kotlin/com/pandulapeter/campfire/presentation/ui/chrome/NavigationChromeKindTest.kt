/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chrome

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.list.hasRoomForSidePanel
import com.pandulapeter.campfire.presentation.ui.components.list.minSongColumnWidth
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationChromeKindTest {

    @Test
    fun `widening a window never takes the side panel away once it has appeared`() = sweep { column, widths ->
        var hasAppeared = false
        widths.forEach { (width, hasPanel) ->
            if (hasAppeared) assertTrue(hasPanel, "The panel went away at $width with $column columns")
            hasAppeared = hasAppeared || hasPanel
        }
    }

    @Test
    fun `the expanded rail is only used where the side panel fits beside it`() = sweep { column, _ ->
        WIDTHS.filter { navigationChromeKind(it, column) == NavigationChromeKind.EXPANDED_RAIL }.forEach { width ->
            assertTrue(hasRoomForSidePanel(width - EXPANDED_NAVIGATION_RAIL_MIN_WIDTH - NAVIGATION_RAIL_CONTENT_GAP, column), "No panel beside the expanded rail at $width")
        }
    }

    @Test
    fun `widening a window never collapses the expanded rail again`() = sweep { column, _ ->
        var hasExpanded = false
        WIDTHS.forEach { width ->
            val kind = navigationChromeKind(width, column)
            if (hasExpanded) assertFalse(kind == NavigationChromeKind.RAIL, "The rail collapsed again at $width with $column columns")
            hasExpanded = hasExpanded || kind == NavigationChromeKind.EXPANDED_RAIL
        }
    }

    /** Runs [assert] for the column width with and without covers, with the panel at every width. */
    private fun sweep(assert: (column: Dp, widths: List<Pair<Dp, Boolean>>) -> Unit) = listOf(false, true).forEach { isCoverArtEnabled ->
        val column = minSongColumnWidth(isCoverArtEnabled)
        assert(column, WIDTHS.map { it to hasPanel(it, column) })
    }

    /** Whether the Songs screen shows its side panel in a window this wide, beside the rail the window gets. */
    private fun hasPanel(windowWidth: Dp, column: Dp): Boolean {
        val railWidth = when (navigationChromeKind(windowWidth, column)) {
            NavigationChromeKind.BAR -> 0.dp
            NavigationChromeKind.RAIL -> COLLAPSED_RAIL_WIDTH
            NavigationChromeKind.EXPANDED_RAIL -> EXPANDED_NAVIGATION_RAIL_MIN_WIDTH
        }
        return hasRoomForSidePanel(windowWidth - railWidth - if (railWidth > 0.dp) NAVIGATION_RAIL_CONTENT_GAP else 0.dp, column)
    }

    private companion object {
        /** Material's collapsed `WideNavigationRail` width. */
        val COLLAPSED_RAIL_WIDTH = 96.dp
        val WIDTHS = (600..3000).map { it.dp }
    }
}
