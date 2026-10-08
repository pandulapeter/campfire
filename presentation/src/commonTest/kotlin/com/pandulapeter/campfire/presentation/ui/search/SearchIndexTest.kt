/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.search

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.domain.api.models.SongSection
import com.pandulapeter.campfire.presentation.ui.screens.songs.SongGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SearchIndexTest {

    @Test
    fun `ranks in stable boolean buckets`() {
        val songs = listOf(
            searchable("tag", "else", "else", listOf("love")),
            searchable("artist", "else", "lovely", emptyList()),
            searchable("title", "love song", "else", emptyList()),
            searchable("title tie", "love again", "else", emptyList()),
            searchable("other", "beloved", "else", emptyList()),
            searchable("miss", "other", "other", emptyList()),
        )
        assertEquals(listOf("title", "title tie", "artist", "other", "tag"), rankSongs(songs, "love").map { it.title })
        assertEquals(songs.map { it.song }, rankSongs(songs, ""))
    }

    @Test
    fun `a title and an artist that both start with the query outrank a title alone, and a miss has no rank`() {
        assertNull(searchRank(title = "other", artist = "other", tags = listOf("rock"), query = "love"))
        val both = assertNotNull(searchRank(title = "love song", artist = "lovers", tags = emptyList(), query = "love"))
        val titleOnly = assertNotNull(searchRank(title = "love song", artist = "else", tags = emptyList(), query = "love"))
        assertTrue(both > titleOnly)
    }

    @Test
    fun `a song whose title or tags were edited is found by the new text and no longer by the old`() {
        val index = SongSearchIndex { it.lowercase() }
        val original = song("first").copy(title = "Yesterday", tags = listOf("Ballad"))
        index.update(listOf(original), listOf(original))

        val edited = original.copy(title = "Tomorrow", tags = listOf("Rock"))
        val snapshot = index.update(listOf(edited), listOf(edited))

        assertEquals(listOf(edited), rankSongs(snapshot.filtered, "tomorrow"))
        assertEquals(listOf(edited), rankSongs(snapshot.filtered, "rock"))
        assertEquals(emptyList(), rankSongs(snapshot.filtered, "yesterday"))
        assertEquals(emptyList(), rankSongs(snapshot.filtered, "ballad"))
    }

    @Test
    fun `a renamed file is indexed under its new name only`() {
        val index = SongSearchIndex { it.lowercase() }
        val original = song("first")
        index.update(listOf(original), listOf(original))

        val renamed = original.copy(fileName = "renamed.cho")
        val snapshot = index.update(listOf(renamed), listOf(renamed))

        assertEquals(setOf("renamed.cho"), snapshot.byFileName.keys)
        assertEquals(setOf("renamed.cho"), snapshot.songsByFileName.keys)
    }

    @Test
    fun `an edit that leaves the searched fields alone still hands out the new song`() {
        var calls = 0
        val index = SongSearchIndex { text -> calls++; text.lowercase() }
        val original = song("first").copy(title = "First", artist = "Artist")
        index.update(listOf(original), listOf(original))
        val callsAfterFirstUpdate = calls

        val metadataEdit = original.copy(size = 100L, key = "C")
        val snapshot = index.update(listOf(metadataEdit), listOf(metadataEdit))

        assertEquals(listOf(metadataEdit), snapshot.filtered.map { it.song })
        assertSame(metadataEdit, snapshot.songsByFileName.getValue("first.cho"))
        assertEquals(listOf(metadataEdit), rankSongs(snapshot.filtered, "first"))
        // The index exists so that a library value that leaves a song's text alone does not fold it again.
        assertEquals(callsAfterFirstUpdate, calls)
    }

    @Test
    fun `an empty normalized query keeps the sections`() {
        val first = searchable("first", "first", "a", emptyList())
        val second = searchable("second", "second", "b", emptyList())
        val sections = listOf(
            SongSection(header = SongSection.Header.Letter('F'), songs = listOf(first.song)),
            SongSection(header = SongSection.Header.Letter('S'), songs = listOf(second.song)),
        )
        assertEquals(
            listOf(
                SongGroup(header = SongSection.Header.Letter('F'), songs = listOf(first.song)),
                SongGroup(header = SongSection.Header.Letter('S'), songs = listOf(second.song)),
            ),
            songGroupsFor(sections, listOf(first, second), ""),
        )
        assertEquals(
            listOf(SongGroup(header = null, songs = listOf(second.song))),
            songGroupsFor(sections, listOf(first, second), "sec"),
        )
        assertEquals(emptyList(), songGroupsFor(sections, listOf(first, second), "nothing"))
    }

    private fun searchable(name: String, title: String, artist: String, tags: List<String>) = SearchableSong(
        song = song(name), title = title, artist = artist, tags = tags,
    )

    private fun song(name: String) = Song(
        fileName = "$name.cho", title = name, artist = "", key = null, transpose = 0,
        tags = emptyList(), languages = emptyList(), coverArtUrl = null, hasChords = false,
        canUpdateFileName = false, lastModified = 0L, size = 0L,
    )
}
