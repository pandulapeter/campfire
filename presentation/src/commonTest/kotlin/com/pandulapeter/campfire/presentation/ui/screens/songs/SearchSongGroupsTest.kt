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
import com.pandulapeter.campfire.presentation.ui.state.songs.SongGroup
import com.pandulapeter.campfire.presentation.ui.state.songs.SongGroupHeader
import com.pandulapeter.campfire.presentation.ui.state.songs.songItemKey
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
    fun `the results of a query stay listed after the selection that leads them`() {
        val selection = SongGroup(header = SongGroupHeader.Selection, songs = listOf(song("s1")))
        val results = SongGroup(header = null, songs = listOf(song("a1"), song("b1")))
        val merged = listOf(selection, results).withoutSectionHeaders()
        assertEquals(listOf(true, false), merged.map { it.isSelection })
        assertEquals(listOf(null, null), merged.map { it.header })
        assertEquals(listOf("a1.cho", "b1.cho"), merged[1].songs.map { it.fileName })
    }

    @Test
    fun `a selection alone loses its header and stays the selection`() {
        val merged = listOf(SongGroup(header = SongGroupHeader.Selection, songs = listOf(song("s1")))).withoutSectionHeaders()
        assertEquals(null, merged.single().header)
        assertEquals(true, merged.single().isSelection)
    }

    @Test
    fun `results without sections are left as they are`() {
        val results = listOf(SongGroup(header = null, songs = listOf(song("a1"))))
        assertSame(results, results.withoutSectionHeaders())
    }

    @Test
    fun `the card kept in place keeps its column and the cards before it close up on it`() {
        val sections = listOf(section('A', "a1"), section('B', "b1", "b2", "b3"))
        // b3 starts the second row of B in two columns; run together after a1, it would be in the second column.
        val merged = sections.withoutSectionHeaders(anchor = SongCardSlot(key = songItemKey(song("b3")), column = 0, columnCount = 2))
        assertEquals(1, merged.single().leadingGap)
        assertEquals(SongCardSlot(key = songItemKey(song("b3")), column = 0, columnCount = 2), merged.slotOf(songItemKey(song("b3")), columnCount = 2))
    }

    @Test
    fun `a card already in its column needs no gap`() {
        val merged = listOf(section('A', "a1", "a2"), section('B', "b1")).withoutSectionHeaders(
            anchor = SongCardSlot(key = songItemKey(song("b1")), column = 0, columnCount = 2),
        )
        assertEquals(0, merged.single().leadingGap)
    }

    @Test
    fun `the gap goes before the group the card is in`() {
        val selection = SongGroup(header = SongGroupHeader.Selection, songs = listOf(song("s1")))
        val merged = listOf(selection, section('A', "a1"), section('B', "b1")).withoutSectionHeaders(
            anchor = SongCardSlot(key = songItemKey(song("b1")), column = 0, columnCount = 3),
        )
        assertEquals(listOf(0, 2), merged.map { it.leadingGap })
    }

    @Test
    fun `a slot is where a card is in its section`() =
        assertEquals(1, listOf(section('A', "a1", "a2", "a3")).slotOf(songItemKey(song("a2")), columnCount = 2)?.column)

    @Test
    fun `the card kept across a search is the top left one in sight`() {
        val groups = listOf(section('A', "a1", "a2"))
        assertEquals(songItemKey(song("a2")), groups.cardKeptAcrossSearch(listOf(songItemKey(song("a2")), songItemKey(song("a1")))))
    }

    @Test
    fun `random picks in sight give way to the first card after them`() {
        val picks = SongGroup(header = SongGroupHeader.RandomPicks, songs = listOf(song("a2")))
        val groups = listOf(picks, section('A', "a1", "a2"))
        assertEquals(songItemKey(song("a1")), groups.cardKeptAcrossSearch(listOf(picks.itemKey(song("a2")), songItemKey(song("a1")))))
    }

    @Test
    fun `random picks that fill the screen give way to the card they are followed by`() {
        val picks = SongGroup(header = SongGroupHeader.RandomPicks, songs = listOf(song("a2")))
        val groups = listOf(picks, section('A', "a1", "a2"))
        assertEquals(songItemKey(song("a1")), groups.cardKeptAcrossSearch(listOf(picks.itemKey(song("a2")))))
    }

    @Test
    fun `no card in sight keeps none`() = assertEquals(null, listOf(section('A', "a1")).cardKeptAcrossSearch(emptyList()))

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
