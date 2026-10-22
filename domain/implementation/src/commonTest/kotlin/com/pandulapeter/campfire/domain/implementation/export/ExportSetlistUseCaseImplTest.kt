/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.export

import com.pandulapeter.campfire.domain.implementation.FakeArchiveRepository
import com.pandulapeter.campfire.domain.implementation.SetlistRepositoryStub
import com.pandulapeter.campfire.domain.implementation.SongContentRepositoryStub
import com.pandulapeter.campfire.domain.implementation.testSetlist
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.song.SongContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** A setlist leaves under its title, while the document inside the archive keeps the name its songs are found by. */
class ExportSetlistUseCaseImplTest {

    private val archive = FakeArchiveRepository()

    @Test
    fun `a setlist is named by its title`() = runTest {
        val exported = useCase(testSetlist(fileName = "summer_2.setlist.json", title = "Summer set")).invoke("summer_2.setlist.json", isDated = true)

        assertEquals("summer_set.zip", assertNotNull(exported).name)
        assertEquals(setOf("summer_2.setlist.json"), archive.packed.keys)
    }

    @Test
    fun `a blank title is still a name`() = runTest {
        val exported = useCase(testSetlist(fileName = "gig.setlist.json", title = "")).invoke("gig.setlist.json", isDated = true)

        assertEquals("untitled.zip", assertNotNull(exported).name)
    }

    @Test
    fun `every song the setlist names goes in beside it, a missing one left out`() = runTest {
        val setlist = testSetlist(fileName = "gig.setlist.json", title = "Gig", entries = listOf("a.cho", "b.cho", "c.cho"))

        useCase(FakeSetlistRepository(setlist), readable = setOf("a.cho", "c.cho")).invoke("gig.setlist.json", isDated = true)

        assertEquals(setOf("gig.setlist.json", "a.cho", "c.cho"), archive.packed.keys)
    }

    private fun useCase(setlist: Setlist) = useCase(FakeSetlistRepository(setlist))

    private fun useCase(setlistRepository: FakeSetlistRepository, readable: Set<String> = emptySet()) = ExportSetlistUseCaseImpl(
        setlistRepository = setlistRepository,
        songContentRepository = object : SongContentRepositoryStub() {
            override suspend fun loadSongContent(fileName: String, useCache: Boolean) =
                if (fileName in readable) SongContent(fileName, "{title: $fileName}") else null
        },
        archiveRepository = archive,
    )

    /** Holds the one setlist. */
    private class FakeSetlistRepository(private val setlist: Setlist) : SetlistRepositoryStub() {

        override suspend fun loadSetlistsIfNeeded() = listOf(setlist)

        override suspend fun loadSetlistDocument(fileName: String) = if (fileName == setlist.fileName) "{}" else null
    }
}
