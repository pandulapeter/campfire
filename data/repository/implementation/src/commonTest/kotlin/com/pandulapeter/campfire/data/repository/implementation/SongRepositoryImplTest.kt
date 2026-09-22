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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
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

    @Test
    fun `two songs created at once get a name each`() = runTest {
        val localSource = FakeSongLocalSource(emptyMap())
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource))

        val created = listOf(
            async { repository.createSong("song", "", "a") },
            async { repository.createSong("song", "", "b") },
        ).awaitAll()

        assertEquals(listOf("song.cho", "song_2.cho"), created.map { it.fileName })
        assertEquals(mapOf("song.cho" to "a", "song_2.cho" to "b"), localSource.files)
        assertEquals(listOf("song.cho", "song_2.cho"), repository.songs.first().data?.map { it.fileName }?.sorted())
    }

    @Test
    fun `a created song whose name the list already holds is listed once`() = runTest {
        val localSource = FakeSongLocalSource(mapOf(FILE_NAME to "old"))
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource))
        repository.loadSongsIfNeeded()
        localSource.files.remove(FILE_NAME)

        repository.createSong("song", "", "new")

        assertEquals(listOf(FILE_NAME), repository.songs.first().data?.map { it.fileName })
    }

    @Test
    fun `a deletion whose caller is cancelled after the file went is gone from the list too`() = runTest {
        val localSource = FakeSongLocalSource(mapOf(FILE_NAME to "text"))
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource))
        repository.loadSongsIfNeeded()
        val gate = CompletableDeferred<Unit>().also { localSource.afterWriteGate = it }

        val job = launch { repository.deleteSong(FILE_NAME) }
        runCurrent()
        job.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(FILE_NAME !in localSource.files)
        assertEquals(emptyList(), repository.loadSongsIfNeeded()?.map { it.fileName })
    }

    @Test
    fun `a guarded save cancelled after it wrote updates the list`() = runTest {
        val localSource = FakeSongLocalSource(mapOf(FILE_NAME to "opened"))
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource))
        repository.loadSongsIfNeeded()
        val gate = CompletableDeferred<Unit>().also { localSource.afterWriteGate = it }

        val job = launch { repository.saveSong(SongContent(FILE_NAME, "retitled"), expectedText = "opened") }
        runCurrent()
        job.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("retitled", localSource.files[FILE_NAME])
        assertEquals(listOf("retitled"), repository.loadSongsIfNeeded()?.map { it.title })
    }

    /** A library held in a map, with only the calls a save, a creation and a deletion make answered. */
    private class FakeSongLocalSource(files: Map<String, String>) : SongLocalSource {

        val files = files.toMutableMap()

        /** Awaited once a save or a deletion has reached the map: the file changed, the caller has not heard yet. */
        var afterWriteGate: CompletableDeferred<Unit>? = null

        override suspend fun loadSongs(onProgress: (List<Song>) -> Unit) = files.keys.map(::song)

        override suspend fun loadSong(fileName: String) = if (fileName in files) song(fileName) else null

        override suspend fun loadSongContent(fileName: String) = files[fileName]?.let { SongContent(fileName = fileName, text = it) }

        override suspend fun saveSongContent(content: SongContent) {
            files[content.fileName] = content.text
            afterWriteGate?.await()
        }

        override suspend fun createSong(title: String, artist: String, text: String): Song {
            val fileName = generateSequence(1) { it + 1 }.map { if (it == 1) "$title.cho" else "${title}_$it.cho" }.first { it !in files }
            // The storage finds the name free on one trip and writes under it on another, and this is the gap between them.
            yield()
            files[fileName] = text
            return song(fileName)
        }

        override fun importFileName(fallbackTitle: String, text: String) = throw UnsupportedOperationException()

        override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean) = throw UnsupportedOperationException()

        override suspend fun renameSong(song: Song) = throw UnsupportedOperationException()

        override suspend fun deleteSong(fileName: String) {
            files.remove(fileName)
            afterWriteGate?.await()
        }

        override suspend fun exists(fileName: String) = fileName in files

        private fun song(fileName: String) = Song(
            fileName = fileName,
            // Titled with what the file holds, so that a list entry shows whether it was read before or after a save.
            title = files[fileName] ?: fileName,
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
