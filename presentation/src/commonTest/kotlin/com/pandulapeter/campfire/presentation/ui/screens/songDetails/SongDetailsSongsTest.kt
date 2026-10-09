/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.data.model.domain.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongDetailsSongsTest {

    private val a = song("a.cho")
    private val b = song("b.cho")
    private val c = song("c.cho")
    private val d = song("d.cho")
    private val setlist = listOf("a.cho", "b.cho", "c.cho", "d.cho")

    @Test
    fun `a partial batch missing a song is not resolved before the read`() =
        assertNull(songDetailsSongsOf(setlist, lookup(isLibraryRead = false, a, c, d), emptyMap()))

    @Test
    fun `every song in the lookup is resolved before the read, in the destination's order`() =
        assertEquals(listOf(a, b, c, d), songDetailsSongsOf(setlist, lookup(isLibraryRead = false, d, c, b, a), emptyMap()))

    @Test
    fun `a song being renamed counts as resolved before the read`() =
        assertEquals(listOf(a, b, c, d), songDetailsSongsOf(setlist, lookup(isLibraryRead = false, a, c, d), mapOf("b.cho" to b)))

    @Test
    fun `a setlist with a song gone is shown without it after the read`() =
        assertEquals(listOf(a, c, d), songDetailsSongsOf(setlist, lookup(isLibraryRead = true, a, c, d), emptyMap()))

    @Test
    fun `a destination with none of its songs resolves to nothing after the read`() =
        assertEquals(emptyList(), songDetailsSongsOf(setlist, lookup(isLibraryRead = true), emptyMap()))

    @Test
    fun `a destination with none of its songs is not resolved before the read`() =
        assertNull(songDetailsSongsOf(setlist, lookup(isLibraryRead = false), emptyMap()))

    @Test
    fun `a song the destination names twice is one song and completes it`() =
        assertEquals(listOf(a), songDetailsSongsOf(listOf("a.cho", "a.cho"), lookup(isLibraryRead = false, a), emptyMap()))

    private fun lookup(isLibraryRead: Boolean, vararg songs: Song) = SongLookup(
        songsByFileName = songs.associateBy { it.fileName },
        isLibraryRead = isLibraryRead,
    )

    private fun song(fileName: String) = Song(
        fileName = fileName, title = "", artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
        coverArtUrl = null, hasChords = false, canUpdateFileName = false, lastModified = 0L, size = 0L,
    )
}
