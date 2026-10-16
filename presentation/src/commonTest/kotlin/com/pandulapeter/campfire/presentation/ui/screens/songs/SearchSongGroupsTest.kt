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

import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.domain.api.library.SongSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SearchSongGroupsTest {

    private fun section(letter: Char, vararg songs: String) =
        SongGroup(header = SongGroupHeader.Section(SongSection.Header.Letter(letter)), songs = songs.map(::song))

    @Test
    fun `sections run together under no header`() {
        val merged = listOf(section('A', "a1", "a2"), section('B', "b1")).withoutSectionHeaders()
        assertEquals(1, merged.size)
        assertEquals(null, merged.single().header)
        assertEquals(listOf("a1.cho", "a2.cho", "b1.cho"), merged.single().songs.map { it.fileName })
    }

    @Test
    fun `the selection loses its header but still leads the merged sections`() {
        val selection = SongGroup(header = SongGroupHeader.Selection, songs = listOf(song("s1")))
        val merged = listOf(selection, section('A', "a1"), section('B', "b1")).withoutSectionHeaders()
        assertEquals(listOf(true, false), merged.map { it.isSelection })
        assertEquals(listOf(null, null), merged.map { it.header })
        assertEquals(listOf("a1.cho", "b1.cho"), merged[1].songs.map { it.fileName })
    }

    @Test
    fun `results without sections are left as they are`() {
        val results = listOf(SongGroup(header = null, songs = listOf(song("a1"))))
        assertSame(results, results.withoutSectionHeaders())
    }

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
