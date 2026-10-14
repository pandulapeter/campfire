/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.domain.implementation.SongContentRepositoryStub
import com.pandulapeter.campfire.domain.implementation.SongRepositoryStub
import com.pandulapeter.campfire.domain.implementation.testSong
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields.Field
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DuplicateSongUseCaseImplTest {

    private val original = "{title: Title}\n{artist: Artist}\n{album: Album}\n{key: G}\n\n{start_of_verse}\n[G]Line one\n{end_of_verse}\n"

    @Test
    fun `the copy keeps the original's text and writes the changed fields over it`() = runTest {
        val repository = RecordingSongRepository()
        val song = DuplicateSongUseCaseImpl(repository, FixedSongContentRepository(original))(
            fileName = "artist-title.cho",
            changes = mapOf(Field.SUBTITLE to "Live", Field.ALBUM to ""),
        )
        val metadata = ChordProParser.parseMetadata(repository.text)
        assertEquals("Title", metadata.title)
        assertEquals("Live", metadata.subtitle)
        assertEquals("Artist", metadata.artist)
        assertNull(metadata.album)
        assertEquals("G", metadata.key)
        assertTrue(repository.text.endsWith("\n{start_of_verse}\n[G]Line one\n{end_of_verse}\n"))
        assertEquals("Title (Live)" to "Artist", repository.name)
        assertEquals("Title (Live)", song?.title)
    }

    @Test
    fun `a copy with no changes is the original's text under the original's title`() = runTest {
        val repository = RecordingSongRepository()
        DuplicateSongUseCaseImpl(repository, FixedSongContentRepository(original))(fileName = "artist-title.cho", changes = emptyMap())
        assertEquals(original, repository.text)
        assertEquals("Title" to "Artist", repository.name)
    }

    @Test
    fun `a changed artist names the copy`() = runTest {
        val repository = RecordingSongRepository()
        DuplicateSongUseCaseImpl(repository, FixedSongContentRepository(original))(
            fileName = "artist-title.cho",
            changes = mapOf(Field.ARTIST to " Someone Else "),
        )
        assertEquals("Title" to "Someone Else", repository.name)
    }

    @Test
    fun `nothing is written when the original cannot be read`() = runTest {
        val repository = RecordingSongRepository()
        assertNull(DuplicateSongUseCaseImpl(repository, FixedSongContentRepository(null))(fileName = "gone.cho", changes = emptyMap()))
        assertEquals("", repository.text)
    }

    private class FixedSongContentRepository(private val text: String?) : SongContentRepositoryStub() {
        override suspend fun loadSongContent(fileName: String, useCache: Boolean) = text?.let { SongContent(fileName = fileName, text = it) }
    }

    private class RecordingSongRepository : SongRepositoryStub() {
        var text = ""
        var name = "" to ""

        override suspend fun createSong(title: String, artist: String, text: String): Song {
            this.text = text
            name = title to artist
            return testSong(fileName = "song.cho", title = title, artist = artist)
        }
    }
}
