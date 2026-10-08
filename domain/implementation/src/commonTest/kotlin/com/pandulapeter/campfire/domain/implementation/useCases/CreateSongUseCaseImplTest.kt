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

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields.Field
import com.pandulapeter.campfire.data.model.domain.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CreateSongUseCaseImplTest {

    @Test
    fun `writes the optional metadata and an empty verse to edit`() = runTest {
        val repository = RecordingSongRepository()
        CreateSongUseCaseImpl(repository)(
            title = " Title ",
            artist = " Artist ",
            metadata = mapOf(
                Field.TITLE to "Ignored title",
                Field.ARTIST to "Ignored artist",
                Field.SUBTITLE to " Acoustic ",
                Field.ALBUM to "Album",
                Field.COMPOSER to "Composer",
                Field.LYRICIST to "Lyricist",
                Field.YEAR to "2026",
                Field.DURATION to "3:45",
            ),
        )
        val metadata = ChordProParser.parseMetadata(repository.text)
        assertEquals("Title", metadata.title)
        assertEquals("Artist", metadata.artist)
        assertEquals("Acoustic", metadata.subtitle)
        assertEquals("Album", metadata.album)
        assertEquals("Composer", metadata.composer)
        assertEquals("Lyricist", metadata.lyricist)
        assertEquals("2026", metadata.year)
        assertEquals("3:45", metadata.duration)
        assertEquals("Title (Acoustic)" to "Artist", repository.name)
        assertTrue("{key: }\n{capo: 0}\n{tempo: 120}\n{time: 4/4}\n" in repository.text)
        assertTrue(repository.text.endsWith("\n{start_of_verse}\n\n{end_of_verse}\n"))
    }

    @Test
    fun `a title alone writes the plain template without empty optional directives`() = runTest {
        val repository = RecordingSongRepository()
        val create = CreateSongUseCaseImpl(repository)
        create(title = "Title", artist = "")
        val original = repository.text
        create(title = "Title", artist = "", metadata = mapOf(Field.SUBTITLE to "", Field.ALBUM to "  "))
        assertEquals(original, repository.text)
        assertEquals("Title" to "", repository.name)
        assertEquals("{title: Title}\n{artist: }\n{key: }\n{capo: 0}\n{tempo: 120}\n{time: 4/4}\n\n{start_of_verse}\n\n{end_of_verse}\n", repository.text)
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
