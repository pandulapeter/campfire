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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `the setlists screen's wider cards need a wider window for the same columns`() {
        val twoSetlistColumns = MIN_SETLIST_COLUMN_WIDTH * 2 + FAST_SCROLLER_WIDTH
        assertEquals(2, layout(twoSetlistColumns).setlistColumnCount)
        val justShort = layout(twoSetlistColumns - 1.dp)
        assertEquals(1, justShort.setlistColumnCount)
        assertEquals(2, justShort.columnCount)
    }

    @Test
    fun `a card that says less after its artist is given a narrower column`() {
        val songs = UserPreferences.CardDetails.SongsDefault
        val bare = UserPreferences.CardDetails(isKeyShown = false, isTempoShown = false, isDurationShown = false)
        val narrow = minSongColumnWidth(bare, areChordsEnabled = true, isMetronomeEnabled = true, isInSetlist = false)
        assertTrue(narrow < MIN_SONG_COLUMN_WIDTH)
        assertEquals(narrow, minSongColumnWidth(songs, areChordsEnabled = false, isMetronomeEnabled = false, isInSetlist = false))
        assertTrue(minSongColumnWidth(songs.copy(isDurationShown = true), areChordsEnabled = true, isMetronomeEnabled = true, isInSetlist = false) > MIN_SONG_COLUMN_WIDTH)
        assertTrue(minSongColumnWidth(bare, areChordsEnabled = true, isMetronomeEnabled = true, isInSetlist = true) > narrow)
    }

    @Test
    fun `narrower song cards fit more columns into the same window`() {
        val narrow = minSongColumnWidth(
            UserPreferences.CardDetails(isKeyShown = false, isTempoShown = false, isDurationShown = false),
            areChordsEnabled = true,
            isMetronomeEnabled = true,
            isInSetlist = false,
        )
        val width = narrow * 3 + FAST_SCROLLER_WIDTH
        assertEquals(2, layout(width).columnCount)
        assertEquals(
            3,
            ListLayout.of(settledWidth = width, contentPadding = noPadding, layoutDirection = LayoutDirection.Ltr, songColumnWidth = narrow).columnCount,
        )
    }

    @Test
    fun `a maximized window stops adding columns`() {
        val wide = layout(MIN_SONG_COLUMN_WIDTH * 20)
        assertEquals(wide, layout(MIN_SONG_COLUMN_WIDTH * 200))
        assertTrue(wide.columnCount < 20)
    }

    @Test
    fun `the side panel is only shown where two song columns still fit beside it`() {
        val panelWithTwoColumns = SIDE_PANEL_WIDTH + FAST_SCROLLER_WIDTH + MIN_SONG_COLUMN_WIDTH * 2
        assertTrue(layout(panelWithTwoColumns).hasRoomForSidePanel)
        assertFalse(layout(panelWithTwoColumns - 1.dp).hasRoomForSidePanel)
        (300..3000).map { layout(it.dp) }.filter { it.hasRoomForSidePanel }.forEach { assertTrue(it.columnCountBesideSidePanel >= 2) }
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
