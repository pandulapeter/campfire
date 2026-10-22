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

import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.list.FAST_SCROLLER_WIDTH
import com.pandulapeter.campfire.presentation.ui.components.list.columnCountForWidth
import com.pandulapeter.campfire.presentation.ui.components.list.minSongColumnWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationChromeKindTest {

    @Test
    fun `the expanded rail leaves three song columns beside it`() = COLUMNS.forEach { column ->
        WIDTHS.filter { navigationChromeKind(it, column) == NavigationChromeKind.EXPANDED_RAIL }.forEach { width ->
            val columns = columnCountForWidth(width - EXPANDED_NAVIGATION_RAIL_MIN_WIDTH - NAVIGATION_RAIL_CONTENT_GAP - FAST_SCROLLER_WIDTH, column)
            assertTrue(columns >= 3, "Only $columns columns beside the expanded rail at $width")
        }
    }

    @Test
    fun `widening a window never collapses the expanded rail again`() = COLUMNS.forEach { column ->
        var hasExpanded = false
        WIDTHS.forEach { width ->
            val kind = navigationChromeKind(width, column)
            if (hasExpanded) assertFalse(kind == NavigationChromeKind.RAIL, "The rail collapsed again at $width with $column columns")
            hasExpanded = hasExpanded || kind == NavigationChromeKind.EXPANDED_RAIL
        }
        assertTrue(hasExpanded, "The rail never expanded with $column columns")
    }

    @Test
    fun `a window under 600dp gets the bar`() = COLUMNS.forEach { column ->
        assertEquals(NavigationChromeKind.BAR, navigationChromeKind(599.dp, column))
        assertTrue(navigationChromeKind(600.dp, column).isRail)
    }

    private companion object {
        val COLUMNS = listOf(false, true).map { minSongColumnWidth(isCoverArtEnabled = it) }
        val WIDTHS = (600..3000).map { it.dp }
    }
}
