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

import com.pandulapeter.campfire.chordpro.ChordProMetadataFields.Field
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.repository.api.SongRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest

class CreateSongUseCaseImplTest {

    @Test
    fun createsSongWithOptionalMetadataAndEditableVerse() = runTest {
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
        assertTrue("{key: }\n{capo: }\n{tempo: }\n{time: }\n" in repository.text)
        assertTrue(repository.text.endsWith("\n{start_of_verse}\n\n{end_of_verse}\n"))
    }

    @Test
    fun titleAloneKeepsTheOriginalTemplateWithoutEmptyOptionalDirectives() = runTest {
        val repository = RecordingSongRepository()
        val create = CreateSongUseCaseImpl(repository)
        create(title = "Title", artist = "")
        val original = repository.text
        create(title = "Title", artist = "", metadata = mapOf(Field.SUBTITLE to "", Field.ALBUM to "  "))
        assertEquals(original, repository.text)
        assertEquals("Title" to "", repository.name)
        assertEquals("{title: Title}\n{key: }\n{capo: }\n{tempo: }\n{time: }\n\n{start_of_verse}\n\n{end_of_verse}\n", repository.text)
    }

    private class RecordingSongRepository : SongRepository {
        var text = ""
        var name = "" to ""
        override val songs: Flow<DataState<List<Song>>> = emptyFlow()
        override suspend fun createSong(title: String, artist: String, text: String): Song {
            this.text = text
            name = title to artist
            return Song(
                fileName = "song.cho", title = title, artist = artist, key = null, transpose = 0,
                tags = emptyList(), languages = emptyList(), coverArtUrl = null, hasChords = false,
                canUpdateFileName = false, lastModified = 0, size = text.length.toLong(),
            )
        }
        override suspend fun loadSongsIfNeeded() = error("Unused")
        override suspend fun loadSongFileSizes() = error("Unused")
        override suspend fun rescan() = error("Unused")
        override suspend fun refresh(fileNames: Set<String>) = error("Unused")
        override suspend fun adoptImported(songs: Collection<Song>) = error("Unused")
        override suspend fun saveSong(content: SongContent, expectedText: String?) = error("Unused")
        override fun importFileName(fallbackTitle: String, text: String) = error("Unused")
        override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean) = error("Unused")
        override suspend fun renameSong(song: Song) = error("Unused")
        override suspend fun deleteSong(fileName: String) = error("Unused")
        override suspend fun deleteAllSongs() = error("Unused")
    }
}
