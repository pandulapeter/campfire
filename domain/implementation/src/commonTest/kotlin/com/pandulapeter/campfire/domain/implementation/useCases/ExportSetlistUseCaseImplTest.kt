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
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** A setlist leaves under its title, while the document inside the archive keeps the name its songs are found by. */
class ExportSetlistUseCaseImplTest {

    private val archive = FakeArchiveRepository()

    @Test
    fun `a setlist is named by its title`() = runTest {
        val exported = useCase(setlist(fileName = "summer_2.setlist.json", title = "Summer set")).invoke("summer_2.setlist.json")

        assertEquals("summer_set.zip", assertNotNull(exported).name)
        assertEquals(setOf("summer_2.setlist.json"), archive.packed.keys)
    }

    @Test
    fun `a blank title is still a name`() = runTest {
        val exported = useCase(setlist(fileName = "gig.setlist.json", title = "")).invoke("gig.setlist.json")

        assertEquals("untitled.zip", assertNotNull(exported).name)
    }

    private fun useCase(setlist: Setlist) = ExportSetlistUseCaseImpl(
        setlistRepository = FakeSetlistRepository(setlist),
        songContentRepository = FakeSongContentRepository(),
        archiveRepository = archive,
    )

    private class FakeSetlistRepository(private val setlist: Setlist) : SetlistRepository {
        override val setlists: Flow<DataState<List<Setlist>>> = emptyFlow()
        override suspend fun loadSetlistsIfNeeded() = listOf(setlist)
        override suspend fun loadSetlistFileNamesNaming(songFileName: String) = throw UnsupportedOperationException()
        override suspend fun rescan() = throw UnsupportedOperationException()
        override suspend fun createSetlist(title: String, description: String, priority: Int) = throw UnsupportedOperationException()
        override suspend fun saveSetlist(setlist: Setlist) = throw UnsupportedOperationException()
        override suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist) = throw UnsupportedOperationException()
        override suspend fun renameSetlist(fileName: String, title: String, description: String) = throw UnsupportedOperationException()
        override suspend fun parseSetlist(document: String) = throw UnsupportedOperationException()
        override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean) = throw UnsupportedOperationException()
        override suspend fun loadSetlistDocument(fileName: String) = if (fileName == setlist.fileName) "{}" else null
        override suspend fun deleteSetlist(fileName: String) = throw UnsupportedOperationException()
    }

    private class FakeSongContentRepository : SongContentRepository {
        override val invalidations: Flow<String?> = emptyFlow()
        override suspend fun loadSongContent(fileName: String, shouldCache: Boolean): SongContent? = null
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

    private companion object {
        fun setlist(fileName: String, title: String) = Setlist(
            fileName = fileName,
            title = title,
            description = "",
            priority = 0,
            isArchived = false,
            entries = emptyList(),
            size = 0L,
        )
    }
}
