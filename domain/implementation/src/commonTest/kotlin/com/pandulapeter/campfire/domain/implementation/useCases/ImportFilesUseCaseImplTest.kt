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
import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.implementation.ImportPlanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Clock

/**
 * Applying a plan is the one place anything in the library is overwritten, so what it replaces is pinned here as well
 * as in the planner: never a song the same import brings back unchanged.
 */
class ImportFilesUseCaseImplTest {

    @Test
    fun `replace never overwrites a song the import brings back`() = runTest {
        ImportConflictResolution.entries.forEach { resolution ->
            val songs = FakeSongRepository(files = mutableMapOf("foo.cho" to A))
            val setlists = FakeSetlistRepository()
            val songEntries = ImportPlanner.planSongs(
                incoming = listOf(
                    ImportPlanner.IncomingSong(fileName = "foo.cho", text = A, sourceFileName = "foo.cho"),
                    ImportPlanner.IncomingSong(fileName = "foo.cho", text = B, sourceFileName = "foo_2.cho"),
                ),
                libraryFileNames = songs.files.keys.toList(),
                readLibraryText = songs.files::get,
            )
            val setlistEntries = ImportPlanner.planSetlists(
                incoming = listOf(ImportPlanner.IncomingSetlist(setlist(entries = listOf("foo.cho", "foo_2.cho")), "set.setlist.json")),
                librarySetlists = emptyList(),
                songFileNames = ImportPlanner.plannedSongFileNames(songEntries),
            )
            val plan = ImportPlan(songs = songEntries, setlists = setlistEntries)
            assertFalse(plan.hasConflicts)

            ImportFilesUseCaseImpl(songRepository = songs, setlistRepository = setlists).invoke(plan, resolution)

            assertEquals(mapOf("foo.cho" to A, "foo_2.cho" to B), songs.files, "$resolution")
            assertEquals(listOf("foo.cho", "foo_2.cho"), setlists.files.values.single().entries.map { it.songFileName }, "$resolution")
        }
    }

    @Test
    fun `the applier refuses to replace a kept name even if the plan asks`() = runTest {
        val songs = FakeSongRepository(files = mutableMapOf("foo.cho" to A))
        val plan = ImportPlan(
            songs = listOf(
                ImportPlan.SongEntry(fileName = "foo.cho", text = A, status = ImportPlan.Status.IDENTICAL, sourceFileName = "foo.cho"),
                ImportPlan.SongEntry(fileName = "foo.cho", text = B, status = ImportPlan.Status.CONFLICTING, sourceFileName = "foo_2.cho"),
            ),
        )

        ImportFilesUseCaseImpl(songRepository = songs, setlistRepository = FakeSetlistRepository()).invoke(plan, ImportConflictResolution.REPLACE)

        assertEquals(mapOf("foo.cho" to A, "foo_2.cho" to B), songs.files)
    }

    @Test
    fun `a replacement goes over the spelling the library lists and keeping both under the derived name`() = runTest {
        val plan = ImportPlan(
            songs = listOf(
                ImportPlan.SongEntry(
                    fileName = "wonderwall.cho",
                    text = B,
                    status = ImportPlan.Status.CONFLICTING,
                    sourceFileName = null,
                    replacesFileName = "Wonderwall.cho",
                ),
            ),
        )
        val expectedCalls = mapOf(
            ImportConflictResolution.REPLACE to ("Wonderwall.cho" to true),
            ImportConflictResolution.KEEP_BOTH to ("wonderwall.cho" to false),
        )

        expectedCalls.forEach { (resolution, expectedCall) ->
            val songs = FakeSongRepository(files = mutableMapOf("Wonderwall.cho" to A))

            ImportFilesUseCaseImpl(songRepository = songs, setlistRepository = FakeSetlistRepository()).invoke(plan, resolution)

            assertEquals(listOf(expectedCall), songs.importCalls, "$resolution")
        }
    }

    @Test
    fun `an import puts what it wrote into the lists without rescanning them`() = runTest {
        val songs = FakeSongRepository(files = mutableMapOf("foo.cho" to A))
        val setlists = FakeSetlistRepository()
        val plan = ImportPlan(
            songs = listOf(
                ImportPlan.SongEntry(fileName = "foo.cho", text = B, status = ImportPlan.Status.CONFLICTING, sourceFileName = null),
                ImportPlan.SongEntry(fileName = "bar.cho", text = A, status = ImportPlan.Status.NEW, sourceFileName = null),
            ),
            setlists = ImportPlanner.planSetlists(
                incoming = listOf(ImportPlanner.IncomingSetlist(setlist(entries = listOf("bar.cho")), "set.setlist.json")),
                librarySetlists = emptyList(),
                songFileNames = mapOf("bar.cho" to "bar.cho"),
            ),
        )

        ImportFilesUseCaseImpl(songRepository = songs, setlistRepository = setlists).invoke(plan, ImportConflictResolution.REPLACE)

        assertEquals(listOf("foo.cho", "bar.cho"), songs.adopted)
        assertEquals(listOf("set.setlist.json"), setlists.adopted)
        assertEquals(0, songs.rescanCount)
    }

    @Test
    fun `a setlist that names no date is dated by the import and one that does keeps its own`() = runTest {
        val setlists = FakeSetlistRepository()
        val dated = setlist(entries = emptyList()).copy(fileName = "dated.setlist.json", title = "Dated", date = LocalDate(2020, 1, 1))
        val plan = ImportPlan(
            setlists = ImportPlanner.planSetlists(
                incoming = listOf(setlist(entries = emptyList()), dated).map { ImportPlanner.IncomingSetlist(it, it.fileName) },
                librarySetlists = emptyList(),
                songFileNames = emptyMap(),
            ),
        )

        ImportFilesUseCaseImpl(songRepository = FakeSongRepository(files = mutableMapOf()), setlistRepository = setlists)
            .invoke(plan, ImportConflictResolution.KEEP_BOTH)

        assertEquals(Clock.System.todayIn(TimeZone.currentSystemDefault()), setlists.files.getValue("set.setlist.json").date)
        assertEquals(LocalDate(2020, 1, 1), setlists.files.getValue("dated.setlist.json").date)
    }

    @Test
    fun `an undated setlist that replaces a library one keeps its date and one kept next to it is dated today`() = runTest {
        val planned = LocalDate(2026, 1, 10)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val library = setlist(entries = listOf("a.cho")).copy(fileName = "gig.setlist.json", title = "Gig", date = planned)
        fun incoming(vararg entries: List<String>) = entries.map {
            ImportPlanner.IncomingSetlist(setlist = library.copy(date = null, entries = it.map(Setlist::Entry)), sourceFileName = library.fileName)
        }
        suspend fun importing(incoming: List<ImportPlanner.IncomingSetlist>, resolution: ImportConflictResolution) = FakeSetlistRepository().also { setlists ->
            setlists.files[library.fileName] = library
            val plan = ImportPlan(
                setlists = ImportPlanner.planSetlists(incoming = incoming, librarySetlists = listOf(library), songFileNames = emptyMap()),
            )
            ImportFilesUseCaseImpl(songRepository = FakeSongRepository(files = mutableMapOf()), setlistRepository = setlists).invoke(plan, resolution)
        }

        assertEquals(planned, importing(incoming(listOf("b.cho")), ImportConflictResolution.REPLACE).files.getValue("gig.setlist.json").date)
        assertEquals(today, importing(incoming(listOf("b.cho")), ImportConflictResolution.KEEP_BOTH).files.getValue("gig_2.setlist.json").date)
        importing(incoming(listOf("b.cho"), listOf("c.cho")), ImportConflictResolution.REPLACE).files.let { files ->
            assertEquals(listOf(Setlist.Entry("b.cho")), files.getValue("gig.setlist.json").entries)
            assertEquals(planned, files.getValue("gig.setlist.json").date)
            assertEquals(listOf(Setlist.Entry("c.cho")), files.getValue("gig_2.setlist.json").entries)
            assertEquals(today, files.getValue("gig_2.setlist.json").date)
        }
    }

    @Test
    fun `an import that fails halfway still puts what it wrote into the list`() = runTest {
        val songs = FakeSongRepository(files = mutableMapOf(), failingImport = 2)
        val plan = ImportPlan(
            songs = listOf("one.cho", "two.cho", "three.cho").map {
                ImportPlan.SongEntry(fileName = it, text = A, status = ImportPlan.Status.NEW, sourceFileName = null)
            },
        )

        assertFailsWith<IllegalStateException> {
            ImportFilesUseCaseImpl(songRepository = songs, setlistRepository = FakeSetlistRepository()).invoke(plan, ImportConflictResolution.KEEP_BOTH)
        }

        assertEquals(listOf("one.cho", "two.cho"), songs.adopted)
        assertEquals(0, songs.rescanCount)
    }

    /** Numbers a taken name the way the storage layer does, `x_2`, `x_3`…, unless told to replace it. */
    private fun MutableMap<String, *>.freeName(fileName: String, extension: String): String {
        val name = fileName.removeSuffix(extension)
        return generateSequence(1) { it + 1 }
            .map { if (it == 1) fileName else "${name}_$it$extension" }
            .first { it !in this }
    }

    private inner class FakeSongRepository(
        val files: MutableMap<String, String>,
        private val failingImport: Int? = null,
    ) : SongRepository {
        val importCalls = mutableListOf<Pair<String, Boolean>>()
        val adopted = mutableListOf<String>()
        var rescanCount = 0
        override val songs: Flow<DataState<List<Song>>> = emptyFlow()
        override suspend fun loadSongsIfNeeded() = files.keys.map(::song)
        override suspend fun loadSongFileSizes() = files.mapValues { it.value.length.toLong() }
        override suspend fun rescan() {
            rescanCount++
        }

        override suspend fun refresh(fileNames: Set<String>) = Unit
        override suspend fun saveSong(content: SongContent, expectedText: String?) = throw UnsupportedOperationException()
        override suspend fun createSong(title: String, artist: String, text: String) = throw UnsupportedOperationException()
        override fun importFileName(fallbackTitle: String, text: String) = throw UnsupportedOperationException()
        override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean): Song {
            if (importCalls.size == failingImport) throw IllegalStateException("Full")
            importCalls += fileName to shouldReplace
            val storedName = if (shouldReplace) fileName else files.freeName(fileName, ".cho")
            files[storedName] = text
            return song(storedName)
        }

        override suspend fun adoptImported(songs: Collection<Song>) {
            adopted += songs.map { it.fileName }
        }

        override suspend fun renameSong(song: Song) = throw UnsupportedOperationException()
        override suspend fun deleteSong(fileName: String) = throw UnsupportedOperationException()
        override suspend fun deleteAllSongs() = throw UnsupportedOperationException()
    }

    private inner class FakeSetlistRepository : SetlistRepository {
        val files = mutableMapOf<String, Setlist>()
        override val setlists: Flow<DataState<List<Setlist>>> = emptyFlow()
        val adopted = mutableListOf<String>()
        override suspend fun loadSetlistsIfNeeded() = files.values.toList()
        override suspend fun loadSetlistFileNamesNaming(songFileName: String) = throw UnsupportedOperationException()
        override suspend fun rescan() = throw UnsupportedOperationException()
        override suspend fun refresh(fileNames: Set<String>) = Unit
        override suspend fun createSetlist(title: String, description: String, date: LocalDate, isCountdownShown: Boolean) = throw UnsupportedOperationException()
        override suspend fun saveSetlist(setlist: Setlist) = throw UnsupportedOperationException()
        override suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist) = throw UnsupportedOperationException()
        override suspend fun renameSetlist(fileName: String, title: String, description: String, date: LocalDate, isCountdownShown: Boolean) = throw UnsupportedOperationException()
        override suspend fun parseSetlist(document: String) = throw UnsupportedOperationException()
        override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean): Setlist {
            val storedName = if (shouldReplace) setlist.fileName else files.freeName(setlist.fileName, ".setlist.json")
            return setlist.copy(fileName = storedName).also { files[storedName] = it }
        }

        override suspend fun adoptImported(setlists: Collection<Setlist>) {
            adopted += setlists.map { it.fileName }
        }

        override suspend fun loadSetlistFileSizes(): Map<String, Long> = throw UnsupportedOperationException()
        override suspend fun loadSetlistDocument(fileName: String) = throw UnsupportedOperationException()
        override suspend fun deleteSetlist(fileName: String) = throw UnsupportedOperationException()
        override suspend fun deleteAllSetlists() = throw UnsupportedOperationException()
    }

    private companion object {
        const val A = "{title: Foo}\nA\n"
        const val B = "{title: Foo}\nB\n"

        fun song(fileName: String) = Song(
            fileName = fileName,
            title = fileName,
            artist = "",
            key = null,
            transpose = 0,
            tags = emptyList(),
            languages = emptyList(),
            coverArtUrl = null,
            hasChords = true,
            canUpdateFileName = false,
            lastModified = 0L,
            size = 0L,
        )

        fun setlist(entries: List<String>) = Setlist(
            fileName = "set.setlist.json",
            title = "Set",
            description = "",
            date = null,
            isArchived = false,
            entries = entries.map { Setlist.Entry(songFileName = it) },
            size = 0L,
        )
    }
}
