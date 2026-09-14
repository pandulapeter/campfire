/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The guard that keeps an edit built on an old copy of a song from being written over a newer file: the only thing
 * standing between a tag toggled on an open song and the version a sync run has just brought in.
 */
class SongRepositoryImplTest {

    @Test
    fun `a guarded save over a file that changed underneath it writes nothing`() = runTest {
        val localSource = FakeSongLocalSource(mapOf(FILE_NAME to "synced"))
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource))

        val isWritten = repository.saveSong(SongContent(FILE_NAME, "opened with a tag"), expectedText = "opened")

        assertFalse(isWritten)
        assertEquals("synced", localSource.files[FILE_NAME])
    }

    @Test
    fun `a refused save drops the cached text so that the next read reaches the file`() = runTest {
        val localSource = FakeSongLocalSource(mapOf(FILE_NAME to "opened"))
        val songContentRepository = SongContentRepositoryImpl(localSource)
        val repository = SongRepositoryImpl(localSource, songContentRepository)
        songContentRepository.loadSongContent(FILE_NAME)
        localSource.files[FILE_NAME] = "synced"
        val invalidations = mutableListOf<String?>()
        backgroundScope.launch(Dispatchers.Unconfined) { songContentRepository.invalidations.collect { invalidations += it } }

        repository.saveSong(SongContent(FILE_NAME, "opened with a tag"), expectedText = "opened")

        assertEquals("synced", songContentRepository.loadSongContent(FILE_NAME)?.text)
        assertEquals(listOf<String?>(FILE_NAME), invalidations)
    }

    @Test
    fun `a guarded save over the text it was built on is written`() = runTest {
        val localSource = FakeSongLocalSource(mapOf(FILE_NAME to "opened"))
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource))

        val isWritten = repository.saveSong(SongContent(FILE_NAME, "opened with a tag"), expectedText = "opened")

        assertTrue(isWritten)
        assertEquals("opened with a tag", localSource.files[FILE_NAME])
    }

    @Test
    fun `an unguarded save overwrites whatever the file holds`() = runTest {
        val localSource = FakeSongLocalSource(mapOf(FILE_NAME to "synced"))
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource))

        val isWritten = repository.saveSong(SongContent(FILE_NAME, "the editor's draft"))

        assertTrue(isWritten)
        assertEquals("the editor's draft", localSource.files[FILE_NAME])
    }

    /** A library held in a map, with only the calls a save makes answered. */
    private class FakeSongLocalSource(files: Map<String, String>) : SongLocalSource {

        val files = files.toMutableMap()

        override suspend fun loadSongs(onProgress: (List<Song>) -> Unit) = files.keys.map(::song)

        override suspend fun loadSong(fileName: String) = if (fileName in files) song(fileName) else null

        override suspend fun loadSongContent(fileName: String) = files[fileName]?.let { SongContent(fileName = fileName, text = it) }

        override suspend fun saveSongContent(content: SongContent) {
            files[content.fileName] = content.text
        }

        override suspend fun createSong(title: String, artist: String, text: String) = throw UnsupportedOperationException()

        override fun importFileName(fallbackTitle: String, text: String) = throw UnsupportedOperationException()

        override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean) = throw UnsupportedOperationException()

        override suspend fun renameSong(song: Song) = throw UnsupportedOperationException()

        override suspend fun deleteSong(fileName: String) = throw UnsupportedOperationException()

        override suspend fun exists(fileName: String) = fileName in files

        private fun song(fileName: String) = Song(
            fileName = fileName,
            title = fileName,
            artist = "",
            key = null,
            transpose = 0,
            tags = emptyList(),
            languages = emptyList(),
            hasChords = false,
            canUpdateFileName = false,
            lastModified = 0L,
        )
    }

    private companion object {
        const val FILE_NAME = "song.cho"
    }
}
