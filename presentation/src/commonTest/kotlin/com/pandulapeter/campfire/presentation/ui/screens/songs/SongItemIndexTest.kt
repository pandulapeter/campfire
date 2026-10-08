/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.domain.api.models.SongSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongItemIndexTest {

    private val groups = listOf(
        SongGroup(header = SongSection.Header.Letter('A'), songs = listOf(song("a1"), song("a2"))),
        SongGroup(header = SongSection.Header.Letter('B'), songs = listOf(song("b1"))),
    )

    @Test
    fun aSongCountsTheHeadersAndTheSongsBeforeIt() {
        assertEquals(1, groups.itemIndexOf(songItemKey(song("a1")), hasPlaceholder = false))
        assertEquals(2, groups.itemIndexOf(songItemKey(song("a2")), hasPlaceholder = false))
        assertEquals(4, groups.itemIndexOf(songItemKey(song("b1")), hasPlaceholder = false))
    }

    @Test
    fun aPlaceholderTakesTheFirstItem() = assertEquals(5, groups.itemIndexOf(songItemKey(song("b1")), hasPlaceholder = true))

    @Test
    fun searchResultsHaveNoHeaders() {
        val results = listOf(SongGroup(header = null, songs = listOf(song("a1"), song("b1"))))
        assertEquals(1, results.itemIndexOf(songItemKey(song("b1")), hasPlaceholder = false))
    }

    @Test
    fun aKeyNoSongHasIsNowhere() = assertNull(groups.itemIndexOf("song_c1.cho", hasPlaceholder = false))

    private fun song(name: String) = Song(
        fileName = "$name.cho",
        title = name,
        artist = "",
        key = null,
        transpose = 0,
        tags = emptyList(),
        languages = emptyList(),
        coverArtUrl = null,
        hasChords = true,
        canUpdateFileName = false,
        lastModified = 0,
        size = 0,
    )
}
