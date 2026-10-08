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

import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.repository.implementation.base.RecordingLogger
import com.pandulapeter.campfire.data.source.local.api.LibraryChanges
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The guard that keeps an edit built on an old copy of a song from being written over a newer file: the only thing
 * standing between a tag toggled on an open song and the version a sync run has just brought in.
 */
class SongRepositoryImplTest {

    @Test
    fun `a guarded save over a file that changed underneath it writes nothing`() = runTest {
        val localSource = FakeSongLocalSource(mapOf(FILE_NAME to "synced"))
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource, Logger.Standard), LibraryFileLock(), LibraryChanges(), Logger.Standard)

        val isWritten = repository.saveSong(SongContent(FILE_NAME, "opened with a tag"), expectedText = "opened")

        assertFalse(isWritten)
        assertEquals("synced", localSource.files[FILE_NAME])
    }

    @Test
    fun `a refused save drops the cached text so that the next read reaches the file`() = runTest {
        val localSource = FakeSongLocalSource(mapOf(FILE_NAME to "opened"))
        val songContentRepository = SongContentRepositoryImpl(localSource, Logger.Standard)
        val repository = SongRepositoryImpl(localSource, songContentRepository, LibraryFileLock(), LibraryChanges(), Logger.Standard)
        songContentRepository.loadSongContent(FILE_NAME)
        localSource.files[FILE_NAME] = "synced"
        val invalidations = mutableListOf<Long>()
        backgroundScope.launch(Dispatchers.Unconfined) { songContentRepository.invalidations.collect { invalidations += it } }

        repository.saveSong(SongContent(FILE_NAME, "opened with a tag"), expectedText = "opened")

        assertEquals("synced", songContentRepository.loadSongContent(FILE_NAME)?.text)
        assertEquals(2, invalidations.size)
    }

    @Test
    fun `a guarded save over the text it was built on is written`() = runTest {
        val localSource = FakeSongLocalSource(mapOf(FILE_NAME to "opened"))
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource, Logger.Standard), LibraryFileLock(), LibraryChanges(), Logger.Standard)

        val isWritten = repository.saveSong(SongContent(FILE_NAME, "opened with a tag"), expectedText = "opened")

        assertTrue(isWritten)
        assertEquals("opened with a tag", localSource.files[FILE_NAME])
    }

    @Test
    fun `an unguarded save overwrites whatever the file holds`() = runTest {
        val localSource = FakeSongLocalSource(mapOf(FILE_NAME to "synced"))
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource, Logger.Standard), LibraryFileLock(), LibraryChanges(), Logger.Standard)

        val isWritten = repository.saveSong(SongContent(FILE_NAME, "the editor's draft"))

        assertTrue(isWritten)
        assertEquals("the editor's draft", localSource.files[FILE_NAME])
    }

    @Test
    fun `two songs created at once get a name each`() = runTest {
        val localSource = FakeSongLocalSource(emptyMap())
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource, Logger.Standard), LibraryFileLock(), LibraryChanges(), Logger.Standard)

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
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource, Logger.Standard), LibraryFileLock(), LibraryChanges(), Logger.Standard)
        repository.loadSongsIfNeeded()
        localSource.files.remove(FILE_NAME)

        repository.createSong("song", "", "new")

        assertEquals(listOf(FILE_NAME), repository.songs.first().data?.map { it.fileName })
    }

    @Test
    fun `a deletion whose caller is cancelled after the file went is gone from the list too`() = runTest {
        val localSource = FakeSongLocalSource(mapOf(FILE_NAME to "text"))
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource, Logger.Standard), LibraryFileLock(), LibraryChanges(), Logger.Standard)
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
    fun `deleting every song goes past a file that fails and keeps only that one listed`() = runTest {
        val localSource = FakeSongLocalSource(mapOf("a.cho" to "a", "b.cho" to "b", "c.cho" to "c"))
        localSource.undeletableFileName = "b.cho"
        val logger = RecordingLogger()
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource, Logger.Standard), LibraryFileLock(), LibraryChanges(), logger)
        repository.loadSongsIfNeeded()

        val failure = runCatching { repository.deleteAllSongs() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(1, logger.lines.count { "\"b.cho\"" in it })
        assertEquals(setOf("b.cho"), localSource.files.keys)
        assertEquals(listOf("b.cho"), repository.loadSongsIfNeeded()?.map { it.fileName })
    }

    @Test
    fun `a guarded save cancelled after it wrote updates the list`() = runTest {
        val localSource = FakeSongLocalSource(mapOf(FILE_NAME to "opened"))
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource, Logger.Standard), LibraryFileLock(), LibraryChanges(), Logger.Standard)
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

    @Test
    fun `a save made while a refresh reads is not undone by it`() = runTest {
        val localSource = FakeSongLocalSource(mapOf("a.cho" to "old", "b.cho" to "b"))
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource, Logger.Standard), LibraryFileLock(), LibraryChanges(), Logger.Standard)
        repository.loadSongsIfNeeded()
        val gate = CompletableDeferred<Unit>().also { localSource.loadGates["b.cho"] = it }

        val refresh = launch { repository.refresh(setOf("a.cho", "b.cho")) }
        runCurrent()
        localSource.loadGates.clear()
        val save = launch { repository.saveSong(SongContent("a.cho", "new")) }
        runCurrent()
        gate.complete(Unit)
        refresh.join()
        save.join()

        assertEquals("new", repository.loadSongsIfNeeded()?.first { it.fileName == "a.cho" }?.title)
    }

    @Test
    fun `a deletion made while a refresh reads is not undone by it`() = runTest {
        val localSource = FakeSongLocalSource(mapOf("a.cho" to "a", "b.cho" to "b"))
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource, Logger.Standard), LibraryFileLock(), LibraryChanges(), Logger.Standard)
        repository.loadSongsIfNeeded()
        val gate = CompletableDeferred<Unit>().also { localSource.loadGates["b.cho"] = it }

        val refresh = launch { repository.refresh(setOf("a.cho", "b.cho")) }
        runCurrent()
        val deletion = launch { repository.deleteSong("a.cho") }
        runCurrent()
        gate.complete(Unit)
        refresh.join()
        deletion.join()

        assertEquals(listOf("b.cho"), repository.loadSongsIfNeeded()?.map { it.fileName })
    }

    @Test
    fun `a save waits for the library lock that a sync run holds`() = runTest {
        val localSource = FakeSongLocalSource(mapOf(FILE_NAME to "original"))
        val lock = LibraryFileLock()
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource, Logger.Standard), lock, LibraryChanges(), Logger.Standard)
        val release = CompletableDeferred<Unit>()
        launch { lock.withLock { release.await() } }
        runCurrent()

        val write = launch { repository.saveSong(SongContent(FILE_NAME, "saved")) }
        runCurrent()

        assertEquals("original", localSource.files[FILE_NAME])
        release.complete(Unit)
        write.join()
        assertEquals("saved", localSource.files[FILE_NAME])
    }

    @Test
    fun `a deletion waits for the library lock that a sync run holds`() = runTest {
        val localSource = FakeSongLocalSource(mapOf(FILE_NAME to "original"))
        val lock = LibraryFileLock()
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource, Logger.Standard), lock, LibraryChanges(), Logger.Standard)
        val release = CompletableDeferred<Unit>()
        launch { lock.withLock { release.await() } }
        runCurrent()

        val deletion = launch { repository.deleteSong(FILE_NAME) }
        runCurrent()

        assertTrue(FILE_NAME in localSource.files)
        release.complete(Unit)
        deletion.join()
        assertTrue(FILE_NAME !in localSource.files)
    }

    @Test
    fun `a renamed song is listed under its new name alone`() = runTest {
        val localSource = FakeSongLocalSource(mapOf("old.cho" to "text"))
        localSource.renames["old.cho"] = "new.cho"
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource, Logger.Standard), LibraryFileLock(), LibraryChanges(), Logger.Standard)
        val song = repository.loadSongsIfNeeded()!!.single()

        val renamed = repository.renameSong(song)

        assertEquals("new.cho", renamed?.fileName)
        assertEquals(listOf("new.cho"), repository.songs.first().data?.map { it.fileName })
    }

    @Test
    fun `a rename drops the cached text of the old name`() = runTest {
        val localSource = FakeSongLocalSource(mapOf("old.cho" to "text"))
        localSource.renames["old.cho"] = "new.cho"
        val songContentRepository = SongContentRepositoryImpl(localSource, Logger.Standard)
        val repository = SongRepositoryImpl(localSource, songContentRepository, LibraryFileLock(), LibraryChanges(), Logger.Standard)
        val song = repository.loadSongsIfNeeded()!!.single()
        songContentRepository.loadSongContent("old.cho")

        repository.renameSong(song)

        assertEquals(null, songContentRepository.loadSongContent("old.cho"))
    }

    @Test
    fun `a song the local source does not rename leaves the list as it was`() = runTest {
        val localSource = FakeSongLocalSource(mapOf("a.cho" to "a", "b.cho" to "b"))
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource, Logger.Standard), LibraryFileLock(), LibraryChanges(), Logger.Standard)
        val before = repository.loadSongsIfNeeded()

        assertNull(repository.renameSong(before!!.first()))
        assertEquals(before, repository.songs.first().data)
    }

    @Test
    fun `songs adopted before the list was read read the whole library`() = runTest {
        val localSource = FakeSongLocalSource(mapOf("a.cho" to "a", "imported.cho" to "imported"))
        val repository = SongRepositoryImpl(localSource, SongContentRepositoryImpl(localSource, Logger.Standard), LibraryFileLock(), LibraryChanges(), Logger.Standard)

        repository.adoptImported(listOf(localSource.song("imported.cho")))

        assertEquals(listOf("a.cho", "imported.cho"), repository.songs.first().data?.map { it.fileName }?.sorted())
    }

    private companion object {
        const val FILE_NAME = "song.cho"
    }
}
