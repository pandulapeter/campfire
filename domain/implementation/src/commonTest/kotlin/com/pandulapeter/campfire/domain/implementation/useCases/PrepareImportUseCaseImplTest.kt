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

import com.pandulapeter.campfire.data.model.domain.ExtractedDocument
import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.model.domain.ParsedSetlist
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.DocumentRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which files of a batch the preparation leaves out, and under which heading the import report names them: a file
 * that is left out must never take the rest of the batch with it.
 */
class PrepareImportUseCaseImplTest {

    @Test
    fun `a hidden file picked next to a song is skipped`() = runTest {
        val plan = prepare()(listOf(ImportedFile("._song.cho", byteArrayOf(0, 5, 22, 7)), song("song.cho")))

        assertEquals(listOf("._song.cho"), plan.skippedFileNames)
        assertEquals(listOf("song.cho"), plan.songs.map { it.sourceFileName })
    }

    @Test
    fun `a file that fits its own limit but not what the batch left is oversized`() = runTest {
        val first = ImportedFile("first.pdf", ByteArray(ImportLimits.MAX_DOCUMENT_FILE_SIZE.toInt()))
        val second = ImportedFile("second.pdf", ByteArray((ImportLimits.MAX_IMPORT_SIZE - ImportLimits.MAX_DOCUMENT_FILE_SIZE + 1).toInt()))

        val plan = prepare()(listOf(first, second))

        assertEquals(listOf("second.pdf"), plan.oversizedFileNames)
        assertEquals(listOf("first.pdf"), plan.unreadableDocumentFileNames)
    }

    @Test
    fun `an archive that cannot be unpacked is skipped and the rest is still planned`() = runTest {
        val archive = FakeArchiveRepository { _, _ -> throw IllegalStateException("Not a zip archive.") }

        val plan = prepare(archive = archive)(listOf(ImportedFile("broken.zip", byteArrayOf(1)), song("song.cho")))

        assertEquals(listOf("broken.zip"), plan.skippedFileNames)
        assertEquals(listOf("song.cho"), plan.songs.map { it.sourceFileName })
    }

    @Test
    fun `a setlist that does not parse is skipped and the rest is still planned`() = runTest {
        val files = listOf("gig", "broken").map { ImportedFile("$it.setlist.json", it.encodeToByteArray()) }

        val plan = prepare(
            parseSetlist = { document -> if (document == "broken") null else ParsedSetlist(testSetlist(fileName = "$document.setlist.json"), isDated = true) },
        )(files)

        assertEquals(listOf("broken.setlist.json"), plan.skippedFileNames)
        assertEquals(listOf("gig.setlist.json"), plan.setlists.map { it.sourceFileName })
    }

    @Test
    fun `a song that formatting grows past the limit of a song is oversized`() = runTest {
        // Formatting puts a blank line between sections, which makes each of these one byte longer: the file is within
        // the limit as it arrives, and over it as it would be written.
        val sections = "{sov}\nx\n{eov}\n".repeat(580_000)
        val text = "{title: Long}\n$sections"
        assertTrue(text.length < ImportLimits.MAX_TEXT_FILE_SIZE)

        val plan = prepare()(listOf(ImportedFile("long.cho", text.encodeToByteArray())))

        assertEquals(listOf("long.cho"), plan.oversizedFileNames)
        assertTrue(plan.songs.isEmpty())
    }

    private fun song(name: String) = ImportedFile(name, "{title: ${name.substringBeforeLast('.')}}\n".encodeToByteArray())

    /** An import into an empty library, which leaves what the preparation left out the only thing two plans differ by. */
    private fun prepare(
        archive: ArchiveRepository = FakeArchiveRepository(),
        parseSetlist: (String) -> ParsedSetlist? = { null },
    ) = PrepareImportUseCaseImpl(
        archiveRepository = archive,
        songRepository = object : SongRepositoryStub() {
            override suspend fun loadSongsIfNeeded() = emptyList<Song>()
            override fun importFileName(fallbackTitle: String, text: String) = "$fallbackTitle.cho"
        },
        songContentRepository = object : SongContentRepositoryStub() {
            override suspend fun loadSongContent(fileName: String, useCache: Boolean): SongContent? = null
        },
        setlistRepository = object : SetlistRepositoryStub() {
            override suspend fun loadSetlistsIfNeeded() = emptyList<Setlist>()
            override suspend fun parseSetlist(document: String) = parseSetlist.invoke(document)
        },
        documentRepository = object : DocumentRepository {
            override suspend fun extract(file: ImportedFile): ExtractedDocument? = null
        },
        logger = Logger.Standard,
    )
}
