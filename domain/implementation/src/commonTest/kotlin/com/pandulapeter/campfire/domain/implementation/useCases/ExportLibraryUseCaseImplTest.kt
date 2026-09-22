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
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * What the library export puts into the archive and what it says it left out: an export is the one copy of the library
 * the user keeps elsewhere, and on the web the only one there is.
 */
class ExportLibraryUseCaseImplTest {

    private val archive = FakeArchiveRepository()

    @Test
    fun `exports nothing when the song scan failed`() = runTest {
        val result = useCase(songs = null, setlists = listOf(setlist("a"), setlist("b"))).invoke()

        assertNull(result)
    }

    @Test
    fun `exports nothing when the setlist scan failed`() = runTest {
        val result = useCase(songs = listOf(song("a")), setlists = null).invoke()

        assertNull(result)
    }

    @Test
    fun `exports nothing for an empty library`() = runTest {
        val result = useCase(songs = emptyList(), setlists = emptyList()).invoke()

        assertNull(result)
    }

    @Test
    fun `names the songs it could not read`() = runTest {
        val result = useCase(
            songs = listOf(song("a"), song("b"), song("c")),
            unreadable = setOf("b.cho"),
        ).invoke()

        assertEquals(listOf("b.cho"), assertNotNull(result).skippedFileNames)
        assertEquals(setOf("songs/a.cho", "songs/c.cho"), archive.packed.keys)
    }

    @Test
    fun `names a song that is in the folder but not in the scan`() = runTest {
        val result = useCase(
            songs = listOf(song("a"), song("b"), song("c")),
            folder = listOf("a.cho", "b.cho", "c.cho", "huge.cho"),
        ).invoke()

        assertEquals(listOf("huge.cho"), assertNotNull(result).skippedFileNames)
    }

    @Test
    fun `names a setlist it could not read`() = runTest {
        val result = useCase(
            songs = listOf(song("a")),
            setlists = listOf(setlist("x"), setlist("y")),
            unreadable = setOf("y.setlist.json"),
        ).invoke()

        assertEquals(listOf("y.setlist.json"), assertNotNull(result).skippedFileNames)
        assertEquals(setOf("songs/a.cho", "setlists/x.setlist.json"), archive.packed.keys)
    }

    @Test
    fun `keeps the archive layout`() = runTest {
        val result = useCase(songs = listOf(song("a")), setlists = listOf(setlist("x"))).invoke()

        assertEquals(emptyList(), assertNotNull(result).skippedFileNames)
        assertEquals(setOf("songs/a.cho", "setlists/x.setlist.json"), archive.packed.keys)
    }

    private fun useCase(
        songs: List<Song>?,
        setlists: List<Setlist>? = emptyList(),
        folder: List<String> = songs.orEmpty().map { it.fileName },
        unreadable: Set<String> = emptySet(),
    ) = ExportLibraryUseCaseImpl(
        songRepository = FakeSongRepository(scanned = songs, folder = folder),
        songContentRepository = FakeSongContentRepository(unreadable = unreadable),
        setlistRepository = FakeSetlistRepository(scanned = setlists, unreadable = unreadable),
        archiveRepository = archive,
    )

    private class FakeSongRepository(
        private val scanned: List<Song>?,
        private val folder: List<String>,
    ) : SongRepository {
        override val songs: Flow<DataState<List<Song>>> = emptyFlow()
        override suspend fun loadSongsIfNeeded() = scanned
        override suspend fun loadSongFileNames() = folder
        override suspend fun rescan() = throw UnsupportedOperationException()
        override suspend fun saveSong(content: SongContent, expectedText: String?) = throw UnsupportedOperationException()
        override suspend fun createSong(title: String, artist: String, text: String) = throw UnsupportedOperationException()
        override fun importFileName(fallbackTitle: String, text: String) = throw UnsupportedOperationException()
        override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean) = throw UnsupportedOperationException()
        override suspend fun renameSong(song: Song) = throw UnsupportedOperationException()
        override suspend fun deleteSong(fileName: String) = throw UnsupportedOperationException()
    }

    private class FakeSongContentRepository(private val unreadable: Set<String>) : SongContentRepository {
        override val invalidations: Flow<String?> = emptyFlow()
        override suspend fun loadSongContent(fileName: String, shouldCache: Boolean) =
            if (fileName in unreadable) null else SongContent(fileName = fileName, text = "{title: $fileName}")

        override suspend fun invalidate(fileName: String?) = throw UnsupportedOperationException()
    }

    private class FakeSetlistRepository(
        private val scanned: List<Setlist>?,
        private val unreadable: Set<String>,
    ) : SetlistRepository {
        override val setlists: Flow<DataState<List<Setlist>>> = emptyFlow()
        override suspend fun loadSetlistsIfNeeded() = scanned
        override suspend fun rescan() = throw UnsupportedOperationException()
        override suspend fun createSetlist(title: String, description: String, priority: Int) = throw UnsupportedOperationException()
        override suspend fun saveSetlist(setlist: Setlist) = throw UnsupportedOperationException()
        override suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist) = throw UnsupportedOperationException()
        override suspend fun renameSetlist(fileName: String, title: String, description: String) = throw UnsupportedOperationException()
        override suspend fun parseSetlist(document: String) = throw UnsupportedOperationException()
        override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean) = throw UnsupportedOperationException()
        override suspend fun loadSetlistDocument(fileName: String) = if (fileName in unreadable) null else "{}"
        override suspend fun deleteSetlist(fileName: String) = throw UnsupportedOperationException()
    }

    private class FakeArchiveRepository : ArchiveRepository {

        var packed: Map<String, ByteArray> = emptyMap()

        override suspend fun unpack(archive: ByteArray, maxSize: Long): List<ImportedFile> = throw UnsupportedOperationException()

        override suspend fun pack(files: Map<String, ByteArray>): ByteArray {
            packed = files
            return ByteArray(0)
        }
    }

    private companion object {

        fun song(name: String) = Song(
            fileName = "$name.cho",
            title = name,
            artist = "",
            key = null,
            transpose = 0,
            tags = emptyList(),
            languages = emptyList(),
            hasChords = true,
            canUpdateFileName = false,
            lastModified = 0L,
            size = 0L,
        )

        fun setlist(name: String) = Setlist(
            fileName = "$name.setlist.json",
            title = name,
            description = "",
            priority = 0,
            isArchived = false,
            entries = emptyList(),
            size = 0L,
        )
    }
}
