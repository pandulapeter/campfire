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

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The lock every write to a setlist file takes: the one thing that keeps a change the user just made from being
 * overwritten by a dialog's old copy of the setlist, or by a save, a move or a deletion crossing it halfway.
 */
class SetlistRepositoryImplTest {

    @Test
    fun `a rename keeps what the setlist gained since the caller read it`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho")))
        val repository = SetlistRepositoryImpl(localSource)

        repository.updateSetlist(FILE_NAME) { it.copy(entries = it.entries + Setlist.Entry("b.cho"), isArchived = true) }
        val renamed = repository.renameSetlist(FILE_NAME, "Summer", "For the lake")

        listOf(renamed, localSource.files[RENAMED_FILE_NAME]).forEach { setlist ->
            assertEquals(listOf("a.cho", "b.cho"), setlist?.entries?.map { it.songFileName })
            assertEquals(true, setlist?.isArchived)
            assertEquals("Summer", setlist?.title)
            assertEquals("For the lake", setlist?.description)
        }
        assertTrue(FILE_NAME !in localSource.files)
        assertTrue(repository.setlists.first().data.orEmpty().none { it.fileName == FILE_NAME })
    }

    @Test
    fun `a rename of a setlist that is gone writes nothing`() = runTest {
        val localSource = FakeSetlistLocalSource(emptyList())
        val repository = SetlistRepositoryImpl(localSource)

        assertNull(repository.renameSetlist(FILE_NAME, "Summer", ""))
        assertTrue(localSource.files.isEmpty())
    }

    @Test
    fun `a rename waits for the change being written`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho")))
        val repository = SetlistRepositoryImpl(localSource)
        val gate = CompletableDeferred<Unit>().also { localSource.saveGate = it }

        launch { repository.updateSetlist(FILE_NAME) { it.copy(entries = it.entries + Setlist.Entry("b.cho")) } }
        runCurrent()
        launch { repository.renameSetlist(FILE_NAME, "Summer", "") }
        runCurrent()

        assertEquals(setOf(FILE_NAME), localSource.files.keys)
        assertEquals(listOf("a.cho"), localSource.files.getValue(FILE_NAME).entries.map { it.songFileName })
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(setOf(RENAMED_FILE_NAME), localSource.files.keys)
        assertEquals(listOf("a.cho", "b.cho"), localSource.files.getValue(RENAMED_FILE_NAME).entries.map { it.songFileName })
    }

    @Test
    fun `a deletion waits for the change being written`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho")))
        val repository = SetlistRepositoryImpl(localSource)
        val gate = CompletableDeferred<Unit>().also { localSource.saveGate = it }

        launch { repository.updateSetlist(FILE_NAME) { it.copy(entries = it.entries + Setlist.Entry("b.cho")) } }
        runCurrent()
        launch { repository.deleteSetlist(FILE_NAME) }
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(localSource.files.isEmpty())
        assertTrue(repository.setlists.first().data.orEmpty().isEmpty())
    }

    @Test
    fun `a save waits for the change being written`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho")))
        val repository = SetlistRepositoryImpl(localSource)
        val gate = CompletableDeferred<Unit>().also { localSource.saveGate = it }

        launch { repository.updateSetlist(FILE_NAME) { it.copy(entries = it.entries + Setlist.Entry("b.cho")) } }
        runCurrent()
        launch { repository.saveSetlist(setlist(FILE_NAME, "c.cho")) }
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("c.cho"), localSource.files.getValue(FILE_NAME).entries.map { it.songFileName })
    }

    @Test
    fun `two setlists created at once get a name each`() = runTest {
        val localSource = FakeSetlistLocalSource(emptyList())
        val repository = SetlistRepositoryImpl(localSource)

        val created = listOf(
            async { repository.createSetlist("Gig", "", 1) },
            async { repository.createSetlist("Gig", "", 1) },
        ).awaitAll()

        assertEquals(listOf(FILE_NAME, SECOND_FILE_NAME), created.map { it.fileName })
        assertEquals(setOf(FILE_NAME, SECOND_FILE_NAME), localSource.files.keys)
        assertEquals(listOf(FILE_NAME, SECOND_FILE_NAME), repository.setlists.first().data?.map { it.fileName }?.sorted())
    }

    @Test
    fun `a created setlist whose name the list already holds is listed once`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME)))
        val repository = SetlistRepositoryImpl(localSource)
        repository.loadSetlistsIfNeeded()
        localSource.files.remove(FILE_NAME)

        repository.createSetlist("Gig", "", 1)

        assertEquals(listOf(FILE_NAME), repository.setlists.first().data?.map { it.fileName })
    }

    @Test
    fun `a creation waits for the change being written`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho")))
        val repository = SetlistRepositoryImpl(localSource)
        val gate = CompletableDeferred<Unit>().also { localSource.saveGate = it }

        launch { repository.updateSetlist(FILE_NAME) { it.copy(entries = it.entries + Setlist.Entry("b.cho")) } }
        runCurrent()
        launch { repository.createSetlist("Gig", "", 2) }
        runCurrent()

        assertEquals(setOf(FILE_NAME), localSource.files.keys)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("a.cho", "b.cho"), localSource.files.getValue(FILE_NAME).entries.map { it.songFileName })
        assertEquals(setOf(FILE_NAME, SECOND_FILE_NAME), localSource.files.keys)
    }

    @Test
    fun `a change whose caller is cancelled after the file was written still reaches the cache`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho")))
        val repository = SetlistRepositoryImpl(localSource)
        val gate = CompletableDeferred<Unit>().also { localSource.afterSaveGate = it }

        val job = launch { repository.updateSetlist(FILE_NAME) { it.copy(entries = it.entries + Setlist.Entry("b.cho")) } }
        runCurrent()
        job.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("a.cho", "b.cho"), repository.loadSetlistsIfNeeded()?.single()?.entries?.map { it.songFileName })
    }

    @Test
    fun `a change cancelled before it took the lock writes nothing`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho")))
        val repository = SetlistRepositoryImpl(localSource)
        val gate = CompletableDeferred<Unit>().also { localSource.saveGate = it }

        launch { repository.updateSetlist(FILE_NAME) { it.copy(entries = it.entries + Setlist.Entry("b.cho")) } }
        runCurrent()
        val second = launch { repository.updateSetlist(FILE_NAME) { it.copy(entries = it.entries + Setlist.Entry("c.cho")) } }
        runCurrent()
        second.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("a.cho", "b.cho"), localSource.files.getValue(FILE_NAME).entries.map { it.songFileName })
    }

    /** A setlists directory held in a map, whose writes can be held back until the test lets them through. */
    private class FakeSetlistLocalSource(setlists: List<Setlist>) : SetlistLocalSource {

        val files = setlists.associateBy { it.fileName }.toMutableMap()

        var saveGate: CompletableDeferred<Unit>? = null

        /** Awaited once the file holds the new version: the write has reached the disk, the caller has not heard yet. */
        var afterSaveGate: CompletableDeferred<Unit>? = null

        override suspend fun loadSetlists() = files.values.toList()

        override suspend fun createSetlist(title: String, description: String, priority: Int): Setlist {
            val name = title.lowercase()
            val fileName = generateSequence(1) { it + 1 }.map { if (it == 1) "$name.setlist.json" else "${name}_$it.setlist.json" }.first { it !in files }
            // The storage finds the name free on one trip and writes under it on another, and this is the gap between them.
            yield()
            return Setlist(
                fileName = fileName,
                title = title,
                description = description,
                priority = priority,
                isArchived = false,
                entries = emptyList(),
            ).also { files[it.fileName] = it }
        }

        override suspend fun saveSetlist(setlist: Setlist) {
            saveGate?.await()
            files[setlist.fileName] = setlist
            afterSaveGate?.await()
        }

        override suspend fun renameSetlist(setlist: Setlist, title: String): Setlist {
            files.remove(setlist.fileName)
            return setlist.copy(title = title, fileName = "${title.lowercase()}.setlist.json").also { files[it.fileName] = it }
        }

        override suspend fun parseSetlist(document: String) = throw UnsupportedOperationException()

        override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean) = throw UnsupportedOperationException()

        override suspend fun loadSetlistDocument(fileName: String) = throw UnsupportedOperationException()

        override suspend fun deleteSetlist(fileName: String) {
            files.remove(fileName)
        }
    }

    private companion object {
        const val FILE_NAME = "gig.setlist.json"
        const val SECOND_FILE_NAME = "gig_2.setlist.json"
        const val RENAMED_FILE_NAME = "summer.setlist.json"

        fun setlist(fileName: String, vararg songs: String) = Setlist(
            fileName = fileName,
            title = "Gig",
            description = "",
            priority = 1,
            isArchived = false,
            entries = songs.map { Setlist.Entry(it) },
        )
    }
}
