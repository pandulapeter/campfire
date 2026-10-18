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
import kotlin.test.assertNull

class SongItemIndexTest {

    private val groups = listOf(
        SongGroup(header = SongGroupHeader.Section(SongSection.Header.Letter('A')), songs = listOf(song("a1"), song("a2"))),
        SongGroup(header = SongGroupHeader.Section(SongSection.Header.Letter('B')), songs = listOf(song("b1"))),
    )

    @Test
    fun `a song counts the headers and the songs before it`() {
        assertEquals(1, groups.itemIndexOf(songItemKey(song("a1")), hasPlaceholder = false))
        assertEquals(2, groups.itemIndexOf(songItemKey(song("a2")), hasPlaceholder = false))
        assertEquals(4, groups.itemIndexOf(songItemKey(song("b1")), hasPlaceholder = false))
    }

    @Test
    fun `a placeholder takes the first item`() = assertEquals(5, groups.itemIndexOf(songItemKey(song("b1")), hasPlaceholder = true))

    @Test
    fun `search results have no headers`() {
        val results = listOf(SongGroup(header = null, songs = listOf(song("a1"), song("b1"))))
        assertEquals(1, results.itemIndexOf(songItemKey(song("b1")), hasPlaceholder = false))
    }

    @Test
    fun `a key no song has is nowhere`() = assertNull(groups.itemIndexOf("song_c1.cho", hasPlaceholder = false))

    @Test
    fun `a copy key of a song copied nowhere is nowhere`() =
        assertNull(groups.itemIndexOf(SongGroup(header = null, songs = emptyList(), copiedFileNames = setOf("a1.cho")).itemKey(song("a1")), hasPlaceholder = false))

    @Test
    fun `a key of another kind is nowhere`() {
        assertNull(groups.itemIndexOf("header_a1.cho", hasPlaceholder = false))
        assertNull(groups.itemIndexOf("selection_divider", hasPlaceholder = false))
        assertNull(groups.itemIndexOf(1, hasPlaceholder = false))
    }

    @Test
    fun `the gap that keeps a card in its column is one item`() {
        val results = listOf(SongGroup(header = null, songs = listOf(song("a1"), song("b1")), leadingGap = 2))
        assertEquals(2, results.itemIndexOf(songItemKey(song("b1")), hasPlaceholder = false))
        assertEquals(0, results.firstCardIndex(hasPlaceholder = false))
    }

    @Test
    fun `the divider under the selection is counted`() {
        val selected = listOf(SongGroup(header = SongGroupHeader.Selection, songs = listOf(song("b1")))) + groups
        assertEquals(1, selected.itemIndexOf(songItemKey(song("b1")), hasPlaceholder = false))
        assertEquals(4, selected.itemIndexOf(songItemKey(song("a1")), hasPlaceholder = false))
    }

    @Test
    fun `a copy in the selection is an item apart from its song`() {
        val selection = SongGroup(header = SongGroupHeader.Selection, songs = listOf(song("b1")), copiedFileNames = setOf("b1.cho"))
        val selected = listOf(selection) + groups
        assertEquals(1, selected.itemIndexOf(selection.itemKey(song("b1")), hasPlaceholder = false))
        assertEquals(7, selected.itemIndexOf(songItemKey(song("b1")), hasPlaceholder = false))
    }

    @Test
    fun `the first card follows the first header`() = assertEquals(1, groups.firstCardIndex(hasPlaceholder = false))

    @Test
    fun `a placeholder comes before the first card`() = assertEquals(2, groups.firstCardIndex(hasPlaceholder = true))

    @Test
    fun `an empty section's header is counted before the first card`() {
        val leadingEmpty = listOf(SongGroup(header = SongGroupHeader.Selection, songs = emptyList())) + groups

        assertEquals(2, leadingEmpty.firstCardIndex(hasPlaceholder = false))
    }

    @Test
    fun `a list without headers starts with its first card`() =
        assertEquals(0, listOf(SongGroup(header = null, songs = listOf(song("a1")))).firstCardIndex(hasPlaceholder = false))

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
