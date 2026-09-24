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

import com.pandulapeter.campfire.data.model.domain.Setlist
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
    fun setlistTextIsNormalizedOnlyWhenItChanges() {
        var calls = 0
        val index = SearchableSetlistIndex { text -> calls++; text.lowercase().filter(Char::isLetterOrDigit) }
        val first = setlist("one", "Café!", "Friday")
        val second = setlist("two", "Other", "Night")
        val initial = index.update(listOf(first, second))
        assertEquals(4, calls)
        assertEquals(true, initial.getValue("one").matches("café"))

        val entryEdit = first.copy(entries = listOf(Setlist.Entry("new.cho")))
        val sameText = index.update(listOf(entryEdit, second))
        assertSame(initial.getValue("one"), sameText.getValue("one"))
        assertEquals(4, calls)

        index.update(listOf(entryEdit.copy(title = "New title"), second))
        assertEquals(6, calls)
        assertEquals(setOf("one"), index.update(listOf(entryEdit)).keys)
    }

    private fun searchable(name: String, title: String, artist: String, tags: List<String>) = SearchableSong(
        song = song(name), title = title, artist = artist, tags = tags,
    )

    private fun song(name: String) = Song(
        fileName = "$name.cho", title = name, artist = "", key = null, transpose = 0,
        tags = emptyList(), languages = emptyList(), hasChords = false,
        canUpdateFileName = false, lastModified = 0L, size = 0L,
    )

    private fun setlist(name: String, title: String, description: String) = Setlist(
        fileName = name, title = title, description = description, priority = 0,
        isArchived = false, entries = emptyList(), size = 0L,
    )
}
