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
import com.pandulapeter.campfire.data.model.domain.ParsedSetlist
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.source.local.api.LibraryChanges
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
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
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)

        repository.updateSetlist(FILE_NAME) { it.copy(entries = it.entries + Setlist.Entry("b.cho"), isArchived = true) }
        val renamed = repository.renameSetlist(FILE_NAME, "Summer", "For the lake", DATE, isCountdownShown = true)

        listOf(renamed, localSource.files[RENAMED_FILE_NAME]).forEach { setlist ->
            assertEquals(listOf("a.cho", "b.cho"), setlist?.entries?.map { it.songFileName })
            assertEquals(true, setlist?.isArchived)
            assertEquals("Summer", setlist?.title)
            assertEquals("For the lake", setlist?.description)
            assertEquals(DATE, setlist?.date)
            assertEquals(true, setlist?.isCountdownShown)
        }
        assertTrue(FILE_NAME !in localSource.files)
        assertTrue(repository.setlists.first().data.orEmpty().none { it.fileName == FILE_NAME })
    }

    @Test
    fun `a rename of a setlist that is gone writes nothing`() = runTest {
        val localSource = FakeSetlistLocalSource(emptyList())
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)

        assertNull(repository.renameSetlist(FILE_NAME, "Summer", "", DATE, isCountdownShown = false))
        assertTrue(localSource.files.isEmpty())
    }

    @Test
    fun `a rename waits for the change being written`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho")))
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)
        val gate = CompletableDeferred<Unit>().also { localSource.saveGate = it }

        launch { repository.updateSetlist(FILE_NAME) { it.copy(entries = it.entries + Setlist.Entry("b.cho")) } }
        runCurrent()
        launch { repository.renameSetlist(FILE_NAME, "Summer", "", DATE, isCountdownShown = false) }
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
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)
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
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)
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
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)

        val created = listOf(
            async { repository.createSetlist("Gig", "", DATE, isCountdownShown = false) },
            async { repository.createSetlist("Gig", "", DATE, isCountdownShown = false) },
        ).awaitAll()

        assertEquals(listOf(FILE_NAME, SECOND_FILE_NAME), created.map { it.fileName })
        assertEquals(setOf(FILE_NAME, SECOND_FILE_NAME), localSource.files.keys)
        assertEquals(listOf(FILE_NAME, SECOND_FILE_NAME), repository.setlists.first().data?.map { it.fileName }?.sorted())
    }

    @Test
    fun `a created setlist whose name the list already holds is listed once`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME)))
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)
        repository.loadSetlistsIfNeeded()
        localSource.files.remove(FILE_NAME)

        repository.createSetlist("Gig", "", DATE, isCountdownShown = false)

        assertEquals(listOf(FILE_NAME), repository.setlists.first().data?.map { it.fileName })
    }

    @Test
    fun `a creation waits for the change being written`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho")))
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)
        val gate = CompletableDeferred<Unit>().also { localSource.saveGate = it }

        launch { repository.updateSetlist(FILE_NAME) { it.copy(entries = it.entries + Setlist.Entry("b.cho")) } }
        runCurrent()
        launch { repository.createSetlist("Gig", "", DATE, isCountdownShown = false) }
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
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)
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
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)
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

    @Test
    fun `a change is built on the file rather than on the list read before it`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho", "b.cho")))
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)
        repository.loadSetlistsIfNeeded()
        localSource.files[FILE_NAME] = setlist(FILE_NAME, "b.cho", "a.cho", "c.cho")

        repository.updateSetlist(FILE_NAME) { it.copy(isArchived = true) }

        val written = localSource.files.getValue(FILE_NAME)
        assertEquals(listOf("b.cho", "a.cho", "c.cho"), written.entries.map { it.songFileName })
        assertTrue(written.isArchived)
        assertEquals(listOf(written), repository.setlists.first().data)
    }

    @Test
    fun `the cache keeps the size the write reported`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho")))
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)
        repository.loadSetlistsIfNeeded()

        repository.updateSetlist(FILE_NAME) { it.copy(isArchived = true) }

        assertEquals(SAVED_SIZE, repository.setlists.first().data.orEmpty().single().size)
    }

    @Test
    fun `a rename is built on the file as well`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho", "b.cho")))
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)
        repository.loadSetlistsIfNeeded()
        localSource.files[FILE_NAME] = setlist(FILE_NAME, "b.cho", "a.cho", "c.cho")

        repository.renameSetlist(FILE_NAME, "Summer", "", DATE, isCountdownShown = false)

        assertEquals(listOf("b.cho", "a.cho", "c.cho"), localSource.files.getValue(RENAMED_FILE_NAME).entries.map { it.songFileName })
    }

    @Test
    fun `a change to a setlist whose file is gone writes nothing and drops it from the list`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho")))
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)
        repository.loadSetlistsIfNeeded()
        localSource.files.remove(FILE_NAME)

        assertNull(repository.updateSetlist(FILE_NAME) { it.copy(isArchived = true) })
        assertTrue(localSource.files.isEmpty())
        assertTrue(repository.setlists.first().data.orEmpty().isEmpty())
    }

    @Test
    fun `a setlist whose file cannot be decoded is changed as the list has it`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho")))
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)
        repository.loadSetlistsIfNeeded()
        localSource.isUnreadable = true

        repository.updateSetlist(FILE_NAME) { it.copy(entries = it.entries + Setlist.Entry("b.cho")) }

        assertEquals(listOf("a.cho", "b.cho"), localSource.files.getValue(FILE_NAME).entries.map { it.songFileName })
    }

    @Test
    fun `the setlists naming a song are read from the files`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho")))
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)
        repository.loadSetlistsIfNeeded()
        localSource.files[SECOND_FILE_NAME] = setlist(SECOND_FILE_NAME, "a.cho", "b.cho")

        assertEquals(setOf(FILE_NAME, SECOND_FILE_NAME), repository.loadSetlistFileNamesNaming("a.cho").toSet())
        assertTrue(repository.loadSetlistFileNamesNaming("c.cho").isEmpty())
        assertEquals(listOf(FILE_NAME), repository.setlists.first().data?.map { it.fileName })
    }

    @Test
    fun `a setlist only the cache knows still counts`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho")))
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)
        repository.loadSetlistsIfNeeded()
        localSource.unlistable = setOf(FILE_NAME)

        assertEquals(listOf(FILE_NAME), repository.loadSetlistFileNamesNaming("a.cho"))
    }

    @Test
    fun `setlists that cannot be listed are not taken for none`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho")))
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)
        repository.loadSetlistsIfNeeded()
        localSource.isListingBroken = true

        assertFailsWith<IllegalStateException> { repository.loadSetlistFileNamesNaming("a.cho") }
    }

    @Test
    fun `a change made while a refresh reads is not undone by it`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist("a.setlist.json", "a.cho"), setlist("b.setlist.json", "a.cho")))
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)
        repository.loadSetlistsIfNeeded()
        val gate = CompletableDeferred<Unit>().also { localSource.loadGates["b.setlist.json"] = it }

        val refresh = launch { repository.refresh(setOf("a.setlist.json", "b.setlist.json")) }
        runCurrent()
        localSource.loadGates.clear()
        val change = launch { repository.updateSetlist("a.setlist.json") { it.copy(entries = it.entries + Setlist.Entry("b.cho")) } }
        runCurrent()
        gate.complete(Unit)
        refresh.join()
        change.join()

        assertEquals(
            listOf("a.cho", "b.cho"),
            repository.setlists.first().data?.first { it.fileName == "a.setlist.json" }?.entries?.map { it.songFileName },
        )
    }

    @Test
    fun `the day is written into the version of an undated setlist that replaced the one the listing read`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho"))).apply { undated += FILE_NAME }
        // A sync download landing between the listing and the day being written.
        localSource.afterListing = {
            localSource.files[FILE_NAME] = setlist(FILE_NAME, "a.cho", "b.cho")
            localSource.afterListing = {}
        }
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)

        repository.loadSetlistsIfNeeded()

        val saved = localSource.saves.single()
        assertEquals(listOf("a.cho", "b.cho"), saved.entries.map { it.songFileName })
        assertEquals(GIVEN_DAY, saved.date)
        assertEquals(listOf(saved), repository.setlists.first().data)
    }

    @Test
    fun `an undated setlist is saved with its day once`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho"), setlist(SECOND_FILE_NAME, "b.cho")))
            .apply { undated += FILE_NAME }
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)

        repository.loadSetlistsIfNeeded()
        repository.loadSetlistsIfNeeded()
        repository.rescan()

        assertEquals(listOf(FILE_NAME), localSource.saves.map { it.fileName })
        assertEquals(GIVEN_DAY, localSource.files.getValue(FILE_NAME).date)
    }

    @Test
    fun `finding the setlists that name a song writes nothing`() = runTest {
        val localSource = FakeSetlistLocalSource(listOf(setlist(FILE_NAME, "a.cho"))).apply { undated += FILE_NAME }
        val repository = SetlistRepositoryImpl(localSource, LibraryFileLock(), LibraryChanges(), Logger.Standard)

        assertEquals(listOf(FILE_NAME), repository.loadSetlistFileNamesNaming("a.cho"))
        assertTrue(localSource.saves.isEmpty())
    }

    /** A setlists directory held in a map, whose writes can be held back until the test lets them through. */
    private class FakeSetlistLocalSource(setlists: List<Setlist>) : SetlistLocalSource {

        val files = setlists.associateBy { it.fileName }.toMutableMap()

        var saveGate: CompletableDeferred<Unit>? = null

        /** Awaited once the file holds the new version: the write has reached the disk, the caller has not heard yet. */
        var afterSaveGate: CompletableDeferred<Unit>? = null

        /** Set to make [loadSetlist] fail the way a file edited by hand into invalid JSON does. */
        var isUnreadable = false

        /** Left out of the listing the way the storage skips a single file it cannot read. */
        var unlistable = emptySet<String>()

        /** Set to make [loadSetlists] fail the way a directory that cannot be listed does. */
        var isListingBroken = false

        /** The files that name no day of their own, which [loadSetlist] dates and saves the way the storage does. */
        val undated = mutableSetOf<String>()

        /** Every setlist [saveSetlist] wrote, in order. */
        val saves = mutableListOf<Setlist>()

        /** Run once [loadSetlists] has its answer, which is where a test changes a file behind the listing's back. */
        var afterListing: () -> Unit = {}

        override suspend fun loadSetlists(): List<ParsedSetlist> {
            if (isListingBroken) throw IllegalStateException("Not a directory.")
            return files.values
                .filter { it.fileName !in unlistable }
                .map { ParsedSetlist(setlist = if (it.fileName in undated) it.copy(date = GIVEN_DAY) else it, isDated = it.fileName !in undated) }
                .also { afterListing() }
        }

        /** Awaited by a read of the file it is filed under, as a slow storage would keep a refresh reading. */
        val loadGates = mutableMapOf<String, CompletableDeferred<Unit>>()

        override suspend fun loadSetlist(fileName: String): Setlist? {
            loadGates[fileName]?.await()
            if (isUnreadable) throw IllegalStateException("Not a setlist.")
            val setlist = files[fileName] ?: return null
            if (!undated.remove(fileName)) return setlist
            return saveSetlist(setlist.copy(date = GIVEN_DAY))
        }

        override suspend fun createSetlist(title: String, description: String, date: LocalDate, isCountdownShown: Boolean): Setlist {
            val name = title.lowercase()
            val fileName = generateSequence(1) { it + 1 }.map { if (it == 1) "$name.setlist.json" else "${name}_$it.setlist.json" }.first { it !in files }
            // The storage finds the name free on one trip and writes under it on another, and this is the gap between them.
            yield()
            return Setlist(
                fileName = fileName,
                title = title,
                description = description,
                date = date,
                isCountdownShown = isCountdownShown,
                isArchived = false,
                entries = emptyList(),
                size = 0L,
            ).also { files[it.fileName] = it }
        }

        override suspend fun saveSetlist(setlist: Setlist): Setlist {
            saveGate?.await()
            val saved = setlist.copy(size = SAVED_SIZE)
            files[setlist.fileName] = saved
            saves += saved
            afterSaveGate?.await()
            return saved
        }

        override suspend fun renameSetlist(setlist: Setlist, title: String): Setlist {
            files.remove(setlist.fileName)
            return setlist.copy(title = title, fileName = "${title.lowercase()}.setlist.json").also { files[it.fileName] = it }
        }

        override suspend fun parseSetlist(document: String) = throw UnsupportedOperationException()

        override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean) = throw UnsupportedOperationException()

        override suspend fun loadSetlistFileSizes(): Map<String, Long> = throw UnsupportedOperationException()

        override suspend fun loadSetlistDocument(fileName: String, songFileNames: Set<String>?) = throw UnsupportedOperationException()

        override suspend fun deleteSetlist(fileName: String) {
            files.remove(fileName)
        }
    }

    private companion object {
        const val FILE_NAME = "gig.setlist.json"
        const val SECOND_FILE_NAME = "gig_2.setlist.json"
        const val RENAMED_FILE_NAME = "summer.setlist.json"
        const val SAVED_SIZE = 42L
        val DATE = LocalDate(2026, 9, 28)
        val GIVEN_DAY = LocalDate(2026, 10, 7)

        fun setlist(fileName: String, vararg songs: String) = Setlist(
            fileName = fileName,
            title = "Gig",
            description = "",
            date = LocalDate(2026, 1, 1),
            isArchived = false,
            entries = songs.map { Setlist.Entry(it) },
            size = 0L,
        )
    }
}
