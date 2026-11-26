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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListLayoutTest {

    private val noPadding = PaddingValues(0.dp)

    private fun layout(width: Dp, contentPadding: PaddingValues = noPadding) =
        ListLayout.of(settledWidth = width, contentPadding = contentPadding, layoutDirection = LayoutDirection.Ltr)

    @Test
    fun `a second song column needs two minimum columns next to the fast scroller`() {
        val twoColumns = MIN_SONG_COLUMN_WIDTH * 2 + FAST_SCROLLER_WIDTH
        assertEquals(2, layout(twoColumns).columnCount)
        assertEquals(1, layout(twoColumns - 1.dp).columnCount)
    }

    @Test
    fun `the horizontal content padding is not part of the columns' width`() {
        val padding = PaddingValues(horizontal = 16.dp)
        val twoColumns = MIN_SONG_COLUMN_WIDTH * 2 + FAST_SCROLLER_WIDTH + 32.dp
        assertEquals(2, layout(twoColumns, padding).columnCount)
        assertEquals(1, layout(twoColumns - 1.dp, padding).columnCount)
    }

    @Test
    fun `cards without covers are given a narrower column`() {
        val narrow = minSongColumnWidth(isCoverArtEnabled = false)
        assertTrue(narrow < MIN_SONG_COLUMN_WIDTH)
        val width = narrow * 3 + FAST_SCROLLER_WIDTH
        assertEquals(2, layout(width).columnCount)
        assertEquals(
            3,
            ListLayout.of(settledWidth = width, contentPadding = noPadding, layoutDirection = LayoutDirection.Ltr, songColumnWidth = narrow).columnCount,
        )
    }

    @Test
    fun `a maximized laptop window stops adding columns at four`() {
        assertEquals(4, layout(MIN_SONG_COLUMN_WIDTH * 4 + FAST_SCROLLER_WIDTH).columnCount)
        assertEquals(4, layout(MIN_SONG_COLUMN_WIDTH * 5.5f + FAST_SCROLLER_WIDTH).columnCount)
    }

    @Test
    fun `a very wide window keeps its cards from stretching`() {
        listOf(2_560.dp, 3_840.dp, 5_120.dp).forEach { width ->
            val columnWidth = (width - FAST_SCROLLER_WIDTH) / layout(width).columnCount
            assertTrue(columnWidth >= MIN_SONG_COLUMN_WIDTH && columnWidth <= MIN_SONG_COLUMN_WIDTH * 1.5f, "$width: $columnWidth")
        }
    }

    @Test
    fun `the columns fill the available width with cells at most a pixel apart`() {
        val density = Density(1f)
        (1..4).forEach { count ->
            listOf(359, 360, 1001, 1440).forEach { availableSize ->
                val sizes = with(ListColumns(count)) { density.calculateCrossAxisCellSizes(availableSize = availableSize, spacing = 7) }
                assertEquals(count, sizes.size)
                assertEquals(availableSize, sizes.sum() + 7 * (count - 1))
                assertTrue(sizes.max() - sizes.min() <= 1)
            }
        }
    }
}
