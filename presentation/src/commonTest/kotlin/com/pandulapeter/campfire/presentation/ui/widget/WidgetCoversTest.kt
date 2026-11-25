/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.widget

import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.platform.reencodedSizeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WidgetCoversTest {

    private fun song(fileName: String, coverArtUrl: String?) = Song(
        fileName = fileName,
        title = fileName,
        artist = "",
        key = null,
        transpose = 0,
        tags = emptyList(),
        languages = emptyList(),
        coverArtUrl = coverArtUrl,
        hasChords = true,
        canUpdateFileName = false,
        lastModified = 0,
        size = 0,
    )

    private val songs = listOf(song("a.cho", "https://covers/a.jpg"), song("b.cho", null), song("c.cho", " "))

    @Test
    fun `only the songs naming a cover have an address and none do with the Cover art switch off`() {
        assertEquals(mapOf("a.cho" to "https://covers/a.jpg"), widgetCoverAddresses(songs = songs, isCoverArtEnabled = true))
        assertEquals(emptyMap(), widgetCoverAddresses(songs = songs, isCoverArtEnabled = false))
    }

    @Test
    fun `a cover's thumbnail is named by its address alone`() {
        assertEquals(widgetCoverName("https://covers/a.jpg"), widgetCoverName("https://covers/a.jpg"))
        assertNotEquals(widgetCoverName("https://covers/a.jpg"), widgetCoverName("https://covers/b.jpg"))
    }

    @Test
    fun `a snapshot names only the thumbnails that are there`() {
        val snapshot = WidgetSnapshot(
            songs = listOf("a", "b").map { WidgetSnapshot.Song(fileName = "$it.cho", title = it, artist = "", link = "", cover = "stale.jpg") },
            setlists = emptyList(),
            areSongsEnabled = true,
            areSetlistsEnabled = true,
            homeLink = "",
            setlistsLink = "",
            strings = WidgetTestStrings.strings,
        )
        val addresses = mapOf("a.cho" to "https://covers/a.jpg", "b.cho" to "https://covers/b.jpg")
        val covers = snapshot.withCovers(addresses = addresses, available = setOf(widgetCoverName("https://covers/a.jpg"))).songs.map { it.cover }
        assertEquals(listOf(widgetCoverName("https://covers/a.jpg"), null), covers)
    }

    @Test
    fun `a cover's thumbnail has its shorter side at the thumbnail's size and is never made larger`() {
        fun shorterSide(width: Int, height: Int): Int {
            val size = reencodedSizeOf(width, height, widgetCoverScale(width, height))
            return minOf(size.width, size.height)
        }
        assertEquals(WIDGET_COVER_SIDE, shorterSide(1200, 1200))
        assertEquals(WIDGET_COVER_SIDE, shorterSide(1600, 900))
        assertEquals(1f, widgetCoverScale(60, 60))
        assertTrue(widgetCoverScale(8000, 8000) < 1f)
    }

    @Test
    fun `Random picks shows every song's cover while Setlist widgets alone show only the setlists' songs and no widget shows none`() {
        val snapshot = WidgetSnapshot(
            songs = listOf("a", "b").map { WidgetSnapshot.Song(fileName = "$it.cho", title = it, artist = "", link = "") },
            setlists = listOf(
                WidgetSnapshot.Setlist(
                    fileName = "gig.setlist.json",
                    title = "Gig",
                    date = "2026-12-01",
                    formattedDate = "",
                    isArchived = true,
                    songFileNames = listOf("b.cho"),
                    link = "",
                ),
            ),
            areSongsEnabled = true,
            areSetlistsEnabled = true,
            homeLink = "",
            setlistsLink = "",
            strings = WidgetTestStrings.strings,
        )
        val addresses = mapOf("a.cho" to "https://covers/a.jpg", "b.cho" to "https://covers/b.jpg")
        assertEquals(addresses, snapshot.coverAddressesShownBy(PlacedWidgets(hasRandomPicks = true, hasSetlist = false), addresses))
        assertEquals(mapOf("b.cho" to "https://covers/b.jpg"), snapshot.coverAddressesShownBy(PlacedWidgets(hasRandomPicks = false, hasSetlist = true), addresses))
        assertEquals(emptyMap(), snapshot.coverAddressesShownBy(PlacedWidgets(hasRandomPicks = false, hasSetlist = false), addresses))
    }
}
