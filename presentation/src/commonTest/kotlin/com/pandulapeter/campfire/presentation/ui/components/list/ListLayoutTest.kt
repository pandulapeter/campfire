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
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
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
    fun `the setlists screen flows cards showing the same details into the same columns as the songs screen`() {
        val details = UserPreferences.CardDetails.SetlistsDefault
        val column = minSongColumnWidth(details, areChordsEnabled = true, isMetronomeEnabled = true, isCoverArtEnabled = true)
        listOf(column * 2 + FAST_SCROLLER_WIDTH - 1.dp, column * 2 + FAST_SCROLLER_WIDTH, column * 3 + FAST_SCROLLER_WIDTH).forEach { width ->
            val layout = ListLayout.of(
                settledWidth = width,
                contentPadding = noPadding,
                layoutDirection = LayoutDirection.Ltr,
                songColumnWidth = column,
                setlistColumnWidth = column,
            )
            assertEquals(layout.columnCount, layout.setlistColumnCount)
        }
    }

    @Test
    fun `a card that says less after its artist is given a narrower column`() {
        val songs = UserPreferences.CardDetails.SongsDefault
        val bare = UserPreferences.CardDetails(isKeyShown = false, isTempoShown = false, isDurationShown = false)
        val narrow = minSongColumnWidth(bare, areChordsEnabled = true, isMetronomeEnabled = true, isCoverArtEnabled = true)
        assertTrue(narrow < MIN_SONG_COLUMN_WIDTH)
        assertEquals(narrow, minSongColumnWidth(songs, areChordsEnabled = false, isMetronomeEnabled = false, isCoverArtEnabled = true))
        assertTrue(minSongColumnWidth(songs.copy(isTempoShown = true), areChordsEnabled = false, isMetronomeEnabled = false, isCoverArtEnabled = true) > narrow)
        assertTrue(minSongColumnWidth(songs.copy(isDurationShown = true), areChordsEnabled = true, isMetronomeEnabled = true, isCoverArtEnabled = true) > MIN_SONG_COLUMN_WIDTH)
        assertTrue(minSongColumnWidth(songs, areChordsEnabled = true, isMetronomeEnabled = true, isCoverArtEnabled = false) < MIN_SONG_COLUMN_WIDTH)
    }

    @Test
    fun `narrower song cards fit more columns into the same window`() {
        val narrow = minSongColumnWidth(
            UserPreferences.CardDetails(isKeyShown = false, isTempoShown = false, isDurationShown = false),
            areChordsEnabled = true,
            isMetronomeEnabled = true,
            isCoverArtEnabled = true,
        )
        val width = narrow * 3 + FAST_SCROLLER_WIDTH
        assertEquals(2, layout(width).columnCount)
        assertEquals(
            3,
            ListLayout.of(settledWidth = width, contentPadding = noPadding, layoutDirection = LayoutDirection.Ltr, songColumnWidth = narrow).columnCount,
        )
    }

    @Test
    fun `sorted by artist the songs fit more columns while the side panel stays where it was`() {
        val byArtist = songGridColumnWidth(MIN_SONG_COLUMN_WIDTH, UserPreferences.SortingMode.BY_ARTIST)
        assertEquals(MIN_SONG_COLUMN_WIDTH, songGridColumnWidth(MIN_SONG_COLUMN_WIDTH, UserPreferences.SortingMode.BY_TITLE))
        (300..3000).map { it.dp }.forEach { width ->
            val byTitleLayout = layout(width)
            val byArtistLayout = ListLayout.of(
                settledWidth = width,
                contentPadding = noPadding,
                layoutDirection = LayoutDirection.Ltr,
                songGridColumnWidth = byArtist,
            )
            assertEquals(byTitleLayout.hasRoomForSidePanel, byArtistLayout.hasRoomForSidePanel)
            assertTrue(byArtistLayout.columnCount >= byTitleLayout.columnCount)
            assertEquals(byTitleLayout.setlistColumnCount, byArtistLayout.setlistColumnCount)
        }
        val width = byArtist * 3 + FAST_SCROLLER_WIDTH
        assertEquals(2, layout(width).columnCount)
        assertEquals(
            3,
            ListLayout.of(settledWidth = width, contentPadding = noPadding, layoutDirection = LayoutDirection.Ltr, songGridColumnWidth = byArtist).columnCount,
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
