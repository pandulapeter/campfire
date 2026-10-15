/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.songs

import com.pandulapeter.campfire.domain.implementation.RecordingSongRepository
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields.Field
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

    @Test
    fun `a pasted text is written with the form's values instead of the template`() = runTest {
        val repository = RecordingSongRepository()
        CreateSongUseCaseImpl(repository)(
            title = "Real title",
            artist = "Real artist",
            metadata = mapOf(Field.SUBTITLE to "Live", Field.ALBUM to ""),
            text = "{title: Guessed}\n{artist: Guessed}\n{album: Wrong album}\n{capo: 2}\n\n[Am]Hello [C]world\n",
        )
        val metadata = ChordProParser.parseMetadata(repository.text)
        assertEquals("Real title", metadata.title)
        assertEquals("Real artist", metadata.artist)
        assertEquals("Live", metadata.subtitle)
        assertEquals(null, metadata.album)
        assertEquals(2, metadata.capo)
        assertEquals("Real title (Live)" to "Real artist", repository.name)
        assertTrue(repository.text.endsWith("\n[Am]Hello [C]world\n"))
    }

    @Test
    fun `a pasted text with no header gets one set apart from the song`() = runTest {
        val repository = RecordingSongRepository()
        CreateSongUseCaseImpl(repository)(title = "Title", artist = "Artist", text = "[G]Lyrics\n")
        assertEquals("{title: Title}\n{artist: Artist}\n{key: }\n{capo: 0}\n{tempo: 120}\n{time: 4/4}\n\n[G]Lyrics\n", repository.text)
    }

    @Test
    fun `a pasted text gets the template's header lines it has none of`() = runTest {
        val repository = RecordingSongRepository()
        CreateSongUseCaseImpl(repository)(title = "Title", artist = "", text = "{capo: 3}\n\n[G]Lyrics\n")
        assertEquals("{title: Title}\n{artist: }\n{key: }\n{capo: 3}\n{tempo: 120}\n{time: 4/4}\n\n[G]Lyrics\n", repository.text)
        assertEquals("Title" to "", repository.name)
    }
}
