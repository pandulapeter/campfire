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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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

    /** Numbers a taken name the way the storage layer does, `x_2`, `x_3`…, unless told to replace it. */
    private fun MutableMap<String, *>.freeName(fileName: String, extension: String): String {
        val name = fileName.removeSuffix(extension)
        return generateSequence(1) { it + 1 }
            .map { if (it == 1) fileName else "${name}_$it$extension" }
            .first { it !in this }
    }

    private inner class FakeSongRepository(val files: MutableMap<String, String>) : SongRepository {
        val importCalls = mutableListOf<Pair<String, Boolean>>()
        override val songs: Flow<DataState<List<Song>>> = emptyFlow()
        override suspend fun loadSongsIfNeeded() = files.keys.map(::song)
        override suspend fun loadSongFileNames() = files.keys.toList()
        override suspend fun rescan() = Unit
        override suspend fun saveSong(content: SongContent, expectedText: String?) = throw UnsupportedOperationException()
        override suspend fun createSong(title: String, artist: String, text: String) = throw UnsupportedOperationException()
        override fun importFileName(fallbackTitle: String, text: String) = throw UnsupportedOperationException()
        override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean): Song {
            importCalls += fileName to shouldReplace
            val storedName = if (shouldReplace) fileName else files.freeName(fileName, ".cho")
            files[storedName] = text
            return song(storedName)
        }

        override suspend fun renameSong(song: Song) = throw UnsupportedOperationException()
        override suspend fun deleteSong(fileName: String) = throw UnsupportedOperationException()
    }

    private inner class FakeSetlistRepository : SetlistRepository {
        val files = mutableMapOf<String, Setlist>()
        override val setlists: Flow<DataState<List<Setlist>>> = emptyFlow()
        override suspend fun loadSetlistsIfNeeded() = files.values.toList()
        override suspend fun loadSetlistFileNamesNaming(songFileName: String) = throw UnsupportedOperationException()
        override suspend fun rescan() = Unit
        override suspend fun createSetlist(title: String, description: String, priority: Int) = throw UnsupportedOperationException()
        override suspend fun saveSetlist(setlist: Setlist) = throw UnsupportedOperationException()
        override suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist) = throw UnsupportedOperationException()
        override suspend fun renameSetlist(fileName: String, title: String, description: String) = throw UnsupportedOperationException()
        override suspend fun parseSetlist(document: String) = throw UnsupportedOperationException()
        override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean): Setlist {
            val storedName = if (shouldReplace) setlist.fileName else files.freeName(setlist.fileName, ".setlist.json")
            return setlist.copy(fileName = storedName).also { files[storedName] = it }
        }

        override suspend fun loadSetlistDocument(fileName: String) = throw UnsupportedOperationException()
        override suspend fun deleteSetlist(fileName: String) = throw UnsupportedOperationException()
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
            hasChords = true,
            canUpdateFileName = false,
            lastModified = 0L,
            size = 0L,
        )

        fun setlist(entries: List<String>) = Setlist(
            fileName = "set.setlist.json",
            title = "Set",
            description = "",
            priority = 0,
            isArchived = false,
            entries = entries.map { Setlist.Entry(songFileName = it) },
            size = 0L,
        )
    }
}
