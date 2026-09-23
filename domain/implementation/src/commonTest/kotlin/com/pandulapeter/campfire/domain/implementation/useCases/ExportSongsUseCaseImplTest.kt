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

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** A song leaves under the name its own header gives it, which is the name the import brings it back under. */
class ExportSongsUseCaseImplTest {

    private val archive = FakeArchiveRepository()

    @Test
    fun `a single song is named by its header`() = runTest {
        val songs = FakeSongRepository(mapOf("old_title" to "new_title.cho"))

        val exported = useCase(songs, "old_title.cho" to "{title: New title}\n").invoke(listOf("old_title.cho"))

        assertEquals("new_title.cho", assertNotNull(exported).name)
        assertEquals(listOf("old_title" to "{title: New title}\n"), songs.importFileNameCalls)
    }

    @Test
    fun `the stored extension is kept`() = runTest {
        val songs = FakeSongRepository(mapOf("song" to "song.cho"))

        val exported = useCase(songs, "song.crd" to "{title: Song}\n").invoke(listOf("song.crd"))

        assertEquals("song.crd", assertNotNull(exported).name)
    }

    @Test
    fun `several songs keep their library names inside the archive`() = runTest {
        val songs = FakeSongRepository(emptyMap())

        val exported = useCase(songs, "a.cho" to "{title: A}\n", "b_2.cho" to "{title: B}\n").invoke(listOf("a.cho", "b_2.cho"))

        assertEquals("campfire_songs.zip", assertNotNull(exported).name)
        assertEquals(setOf("a.cho", "b_2.cho"), archive.packed.keys)
        assertEquals(emptyList(), songs.importFileNameCalls)
    }

    private fun useCase(songRepository: SongRepository, vararg files: Pair<String, String>) = ExportSongsUseCaseImpl(
        songRepository = songRepository,
        songContentRepository = FakeSongContentRepository(files.toMap()),
        archiveRepository = archive,
    )

    /** Names an incoming song from a table keyed by the fallback title, and records what it was asked. */
    private class FakeSongRepository(private val names: Map<String, String>) : SongRepository {
        val importFileNameCalls = mutableListOf<Pair<String, String>>()
        override val songs: Flow<DataState<List<Song>>> = emptyFlow()
        override suspend fun loadSongsIfNeeded() = throw UnsupportedOperationException()
        override suspend fun loadSongFileSizes() = throw UnsupportedOperationException()
        override suspend fun rescan() = throw UnsupportedOperationException()
        override suspend fun saveSong(content: SongContent, expectedText: String?) = throw UnsupportedOperationException()
        override suspend fun createSong(title: String, artist: String, text: String) = throw UnsupportedOperationException()
        override fun importFileName(fallbackTitle: String, text: String): String {
            importFileNameCalls += fallbackTitle to text
            return names.getValue(fallbackTitle)
        }

        override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean) = throw UnsupportedOperationException()
        override suspend fun renameSong(song: Song) = throw UnsupportedOperationException()
        override suspend fun deleteSong(fileName: String) = throw UnsupportedOperationException()
    }

    private class FakeSongContentRepository(private val files: Map<String, String>) : SongContentRepository {
        override val invalidations: Flow<String?> = emptyFlow()
        override suspend fun loadSongContent(fileName: String, shouldCache: Boolean) =
            files[fileName]?.let { SongContent(fileName = fileName, text = it) }

        override suspend fun invalidate(fileName: String?) = throw UnsupportedOperationException()
    }

    private class FakeArchiveRepository : ArchiveRepository {

        var packed: Map<String, ByteArray> = emptyMap()

        override suspend fun unpack(archive: ByteArray, maxSize: Long): List<ImportedFile> = throw UnsupportedOperationException()

        override suspend fun pack(files: Map<String, ByteArray>): ByteArray {
            packed = files
            return ByteArray(0)
        }
    }
}
