/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.importing

import com.pandulapeter.campfire.domain.implementation.FakeArchiveRepository
import com.pandulapeter.campfire.domain.implementation.SetlistRepositoryStub
import com.pandulapeter.campfire.domain.implementation.SongContentRepositoryStub
import com.pandulapeter.campfire.domain.implementation.SongRepositoryStub
import com.pandulapeter.campfire.domain.implementation.testSetlist
import com.pandulapeter.campfire.data.model.importing.ExtractedDocument
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.setlist.ParsedSetlist
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.song.SongContent
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

    @Test
    fun `every song carries the title and artist of its header and the plan the names of the picked files`() = runTest {
        val archive = FakeArchiveRepository { _, _ ->
            listOf(
                ImportedFile("one.cho", "{title: One}\n{artist: Band}\n".encodeToByteArray()),
                ImportedFile("two.cho", "{title: Two}\n{subtitle: Live}\n".encodeToByteArray()),
            )
        }

        val plan = prepare(archive = archive)(
            listOf(ImportedFile("backup.zip", byteArrayOf(1)), ImportedFile("Untitled.cho", "[C]la la\n".encodeToByteArray())),
        )

        assertEquals(listOf("backup.zip", "Untitled.cho"), plan.sourceNames)
        assertEquals(
            listOf(Triple("one.cho", "One", "Band"), Triple("two.cho", "Two (Live)", ""), Triple("Untitled.cho", "Untitled", "")),
            plan.songs.map { Triple(it.sourceFileName, it.title, it.artist) },
        )
    }

    @Test
    fun `a text of unknown name with song structure is planned as a converted song`() = runTest {
        val plan = prepare()(listOf(ImportedFile("riverlight.songsheet", SHEET.encodeToByteArray(), isPlainTextByContent = true)))

        assertEquals(listOf("riverlight.songsheet"), plan.songs.map { it.sourceFileName })
        assertEquals("riverlight", plan.songs.single().title)
        assertTrue(plan.songs.single().text.contains("[G]Down"), plan.songs.single().text)
        assertEquals(emptyList(), plan.oversizedFileNames)
    }

    @Test
    fun `a README under an unknown name is reported skipped, not imported as prose`() = runTest {
        val readme = "# Campfire\nSee [C](https://example.com) for the details.\n- A\n- B\n"

        val plan = prepare()(listOf(ImportedFile("README.md", readme.encodeToByteArray(), isPlainTextByContent = true)))

        assertEquals(listOf("README.md"), plan.skippedFileNames)
        assertTrue(plan.songs.isEmpty())
    }

    @Test
    fun `a bytes-only archive of a licence text is reported as one file`() = runTest {
        val licence = "Permission is hereby granted, free of charge, to any person obtaining a copy.\n"
        val archive = FakeArchiveRepository { _, _ -> listOf(ImportedFile("LICENSE.txt", licence.encodeToByteArray())) }

        val plan = prepare(archive = archive)(listOf(ImportedFile("book.epub", byteArrayOf(0x50, 0x4B, 3, 4, 0))))

        assertEquals(listOf("book.epub"), plan.skippedFileNames)
        assertTrue(plan.songs.isEmpty())
    }

    @Test
    fun `an unknown name with a dot in it keeps its whole name as the title`() = runTest {
        val plan = prepare()(listOf(ImportedFile("Mr. Brightside", SHEET.encodeToByteArray(), isPlainTextByContent = true)))

        assertEquals("Mr. Brightside", plan.songs.single().title)
    }

    @Test
    fun `a loose document of songs is planned as its songs, each with the document's origin`() = runTest {
        val archive = FakeArchiveRepository(songsIn = { file -> if (file.name == "songs.json") listOf(song("One.cho"), song("Two.cho")) else null })

        val plan = prepare(archive = archive)(listOf(song("first.cho"), ImportedFile("songs.json", "[]".encodeToByteArray())))

        assertEquals(listOf("first.cho", "One.cho", "Two.cho"), plan.songs.map { it.sourceFileName })
        assertEquals(listOf(null, 1, 1), plan.songs.map { it.origin })
    }

    @Test
    fun `a loose OnSong file is planned as the song its document holds, not skipped`() = runTest {
        val archive = FakeArchiveRepository(songsIn = { file -> if (file.name == "Riverlight.onsong") listOf(song("Riverlight.cho")) else null })

        val plan = prepare(archive = archive)(listOf(ImportedFile("Riverlight.onsong", "Riverlight\n\nG D\nLa".encodeToByteArray())))

        assertEquals(listOf("Riverlight.cho"), plan.songs.map { it.sourceFileName })
        assertEquals(emptyList(), plan.skippedFileNames)
    }

    @Test
    fun `a setlist is still read as one`() = runTest {
        val plan = prepare(parseSetlist = { ParsedSetlist(testSetlist(fileName = "gig.setlist.json"), isDated = true) })(
            listOf(ImportedFile("gig.setlist.json", "{}".encodeToByteArray())),
        )

        assertEquals(listOf("gig.setlist.json"), plan.setlists.map { it.sourceFileName })
    }

    private fun song(name: String) = ImportedFile(name, "{title: ${name.substringBeforeLast('.')}}\n".encodeToByteArray())

    private companion object {
        const val SHEET = "G         D\nDown by the river light\nC         G\nWe sang until morning\n"
    }

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
