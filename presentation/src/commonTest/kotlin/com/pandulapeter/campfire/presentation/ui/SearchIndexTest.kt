/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import com.pandulapeter.campfire.data.model.domain.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SearchIndexTest {

    @Test
    fun ranksInStableBooleanBuckets() {
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
    fun songFieldsAreNormalizedOnceAndLatestModelsReplaceCachedOnes() {
        var calls = 0
        val index = SongSearchIndex { text -> calls++; text.lowercase() }
        val first = song("first").copy(title = "First", artist = "Artist", tags = listOf("Tag"))
        val second = song("second").copy(title = "Second", artist = "Artist")
        val initial = index.update(listOf(first, second), listOf(second, first))
        assertEquals(5, calls)
        assertEquals(listOf(second, first), initial.filtered.map { it.song })

        val metadataEdit = first.copy(size = 100L, key = "C")
        val reused = index.update(listOf(metadataEdit, second), listOf(metadataEdit))
        assertEquals(5, calls)
        assertSame(metadataEdit, reused.byFileName.getValue("first.cho").song)
        assertEquals(listOf(metadataEdit), reused.filtered.map { it.song })

        val titleEdit = metadataEdit.copy(title = "Changed")
        val changed = index.update(listOf(titleEdit, second), listOf(titleEdit))
        assertEquals(8, calls)
        assertEquals("changed", changed.byFileName.getValue("first.cho").title)

        val renamed = titleEdit.copy(fileName = "renamed.cho")
        val final = index.update(listOf(renamed), listOf(renamed))
        assertEquals(setOf("renamed.cho"), final.byFileName.keys)
        assertEquals(setOf("renamed.cho"), final.songsByFileName.keys)
        assertEquals(11, calls)
    }

    private fun searchable(name: String, title: String, artist: String, tags: List<String>) = SearchableSong(
        song = song(name), title = title, artist = artist, tags = tags,
    )

    private fun song(name: String) = Song(
        fileName = "$name.cho", title = name, artist = "", key = null, transpose = 0,
        tags = emptyList(), languages = emptyList(), hasChords = false,
        canUpdateFileName = false, lastModified = 0L, size = 0L,
    )
}
