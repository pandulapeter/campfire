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
import com.pandulapeter.campfire.domain.implementation.FakeExportFileRepository
import com.pandulapeter.campfire.domain.implementation.FakeImageRepository
import com.pandulapeter.campfire.domain.implementation.SetlistRepositoryStub
import com.pandulapeter.campfire.domain.implementation.SongContentRepositoryStub
import com.pandulapeter.campfire.domain.implementation.testSetlist
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** A setlist leaves under its title, while the document inside the archive keeps the name its songs are found by. */
class ExportSetlistUseCaseImplTest {

    private val archive = FakeArchiveRepository()
    private val images = FakeImageRepository()

    @Test
    fun `a setlist is named by its title`() = runTest {
        val exported = useCase(testSetlist(fileName = "summer_2.setlist.json", title = "Summer set")).invoke("summer_2.setlist.json", isSharedCopy = false)

        assertEquals("summer_set.zip", assertNotNull(exported).name)
        assertEquals(setOf("summer_2.setlist.json"), archive.packed.keys)
    }

    @Test
    fun `a blank title is still a name`() = runTest {
        val exported = useCase(testSetlist(fileName = "gig.setlist.json", title = "")).invoke("gig.setlist.json", isSharedCopy = false)

        assertEquals("untitled.zip", assertNotNull(exported).name)
    }

    @Test
    fun `every song the setlist names goes in beside it, a missing one left out`() = runTest {
        val setlist = testSetlist(fileName = "gig.setlist.json", title = "Gig", entries = listOf("a.cho", "b.cho", "c.cho"))

        useCase(FakeSetlistRepository(setlist), readable = setOf("a.cho", "c.cho")).invoke("gig.setlist.json", isSharedCopy = false)

        assertEquals(setOf("gig.setlist.json", "a.cho", "c.cho"), archive.packed.keys)
    }

    @Test
    fun `the archive knows what an import unpacks it to`() = runTest {
        val setlist = testSetlist(fileName = "gig.setlist.json", title = "Gig", entries = listOf("a.cho"))

        val exported = useCase(FakeSetlistRepository(setlist), readable = setOf("a.cho")).invoke("gig.setlist.json", isSharedCopy = false)

        assertEquals(archive.packed.values.sumOf { it.size.toLong() }, assertNotNull(exported).unpackedSize)
    }

    private fun useCase(setlist: Setlist) = useCase(FakeSetlistRepository(setlist))

    @Test
    fun `the pictures its songs show go in once after them`() = runTest {
        val picture = FakeImageRepository.picture(1)
        val name = images.store(picture)
        val setlist = testSetlist(fileName = "gig.setlist.json", title = "Gig", entries = listOf("a.cho", "b.cho"))
        val texts = mapOf("a.cho" to "{title: A}\n{image: src=$name}", "b.cho" to "{title: B}\n{image: src=$name}")

        val exported = useCase(FakeSetlistRepository(setlist), readable = texts.keys, texts = texts).invoke("gig.setlist.json", isSharedCopy = false)

        assertEquals(listOf("gig.setlist.json", "a.cho", "b.cho", "images/$name"), archive.packed.keys.toList())
        assertEquals(picture.size.toLong(), assertNotNull(exported).unpackedImageSize)
    }

    @Test
    fun `a shared copy's songs keep no line for an attachment it leaves out, whichever song names it`() = runTest {
        images.library["0123456789abcdef.mp4"] = ByteArray((ImportLimits.MAX_SHARED_ATTACHMENTS_SIZE + 1).toInt())
        images.library["fedcba9876543210.pdf"] = ByteArray(100)
        val setlist = testSetlist(fileName = "gig.setlist.json", title = "Gig", entries = listOf("a.cho", "b.cho"))
        val texts = mapOf(
            "a.cho" to "{title: A}\n{meta: attachment fedcba9876543210.pdf Sheet}",
            "b.cho" to "{title: B}\n{meta: attachment 0123456789abcdef.mp4 Video}\n[C]Line",
        )

        val exported = useCase(FakeSetlistRepository(setlist), readable = texts.keys, texts = texts).invoke("gig.setlist.json", isSharedCopy = true)

        assertEquals(1, assertNotNull(exported).leftOutAttachmentCount)
        assertEquals(texts.getValue("a.cho"), archive.packed.getValue("a.cho").decodeToString())
        assertEquals("{title: B}\n[C]Line", archive.packed.getValue("b.cho").decodeToString())
        assertEquals(listOf("gig.setlist.json", "a.cho", "b.cho", "images/fedcba9876543210.pdf"), archive.packed.keys.toList())
    }

    private fun useCase(
        setlistRepository: FakeSetlistRepository,
        readable: Set<String> = emptySet(),
        texts: Map<String, String> = emptyMap(),
    ) = ExportSetlistUseCaseImpl(
        setlistRepository = setlistRepository,
        songContentRepository = object : SongContentRepositoryStub() {
            override suspend fun loadSongContent(fileName: String, useCache: Boolean) =
                if (fileName in readable) SongContent(fileName, texts[fileName] ?: "{title: $fileName}") else null
        },
        archiveRepository = archive,
        exportFileRepository = FakeExportFileRepository(),
        imageRepository = images,
    )

    /** Holds the one setlist. */
    private class FakeSetlistRepository(private val setlist: Setlist) : SetlistRepositoryStub() {

        override suspend fun loadSetlistsIfNeeded() = listOf(setlist)

        override suspend fun loadSetlistDocument(fileName: String) = if (fileName == setlist.fileName) "{}" else null
    }
}
