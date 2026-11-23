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

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.data.formats.zip.WrappedArchiveException
import com.pandulapeter.campfire.domain.implementation.FakeArchiveRepository
import com.pandulapeter.campfire.domain.implementation.FakeImageRepository
import com.pandulapeter.campfire.domain.implementation.FakeStaging
import com.pandulapeter.campfire.domain.implementation.wrappedArchiveException
import com.pandulapeter.campfire.domain.implementation.SetlistRepositoryStub
import com.pandulapeter.campfire.domain.implementation.SongContentRepositoryStub
import com.pandulapeter.campfire.domain.implementation.SongRepositoryStub
import com.pandulapeter.campfire.domain.implementation.testSetlist
import com.pandulapeter.campfire.chordpro.convert.ChordSheet
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.StagingRefusedException
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.setlist.ParsedSetlist
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.DocumentRepository
import com.pandulapeter.campfire.domain.api.importing.PageCropper
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
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
        val staging = FakeStaging()
        val archive = FakeArchiveRepository(staging = staging) { _, _ -> throw IllegalStateException("Not a zip archive.") }

        val plan = prepare(archive = archive)(listOf(staging.received(ImportedFile("broken.zip", byteArrayOf(1))), song("song.cho")))

        assertEquals(listOf("broken.zip"), plan.skippedFileNames)
        assertEquals(listOf("song.cho"), plan.songs.map { it.sourceFileName })
    }

    @Test
    fun `an archive refused for space ends the whole preparation rather than being skipped`() = runTest {
        val staging = FakeStaging()
        val archive = FakeArchiveRepository(staging = staging) { _, _ -> throw StagingRefusedException.NoSpace(neededBytes = 2_000_000_000) }

        val failure = assertFailsWith<StagingRefusedException.NoSpace> {
            prepare(archive = archive)(listOf(staging.received(ImportedFile("library.zip", byteArrayOf(1))), song("song.cho")))
        }

        assertEquals(2_000_000_000, failure.neededBytes)
    }

    @Test
    fun `an archive past 4 GB that its tool wrapped around ends the whole preparation rather than being skipped`() = runTest {
        val staging = FakeStaging()
        val wrapped = wrappedArchiveException()
        val archive = FakeArchiveRepository(staging = staging) { _, _ -> throw wrapped }

        val failure = assertFailsWith<WrappedArchiveException> {
            prepare(archive = archive)(listOf(staging.received(ImportedFile("library.zip", byteArrayOf(1))), song("song.cho")))
        }

        assertSame(wrapped, failure)
    }

    @Test
    fun `a staged archive is unpacked from the staging and never read as the bytes the file carries`() = runTest {
        val staging = FakeStaging()
        val unpacked = mutableListOf<List<Byte>>()
        val archive = FakeArchiveRepository(staging = staging) { bytes, _ -> listOf(song("one.cho")).also { unpacked += bytes.toList() } }

        val plan = prepare(archive = archive)(listOf(staging.received(ImportedFile("backup.zip", byteArrayOf(7, 8)))))

        assertEquals(listOf(listOf<Byte>(7, 8)), unpacked)
        assertEquals(listOf("one.cho"), plan.songs.map { it.sourceFileName })
        assertEquals(listOf("backup.zip"), plan.sourceNames)
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
        val staging = FakeStaging()
        val archive = FakeArchiveRepository(staging = staging) { _, _ ->
            listOf(
                ImportedFile("one.cho", "{title: One}\n{artist: Band}\n".encodeToByteArray()),
                ImportedFile("two.cho", "{title: Two}\n{subtitle: Live}\n".encodeToByteArray()),
            )
        }

        val plan = prepare(archive = archive)(
            listOf(staging.received(ImportedFile("backup.zip", byteArrayOf(1))), ImportedFile("Untitled.cho", "[C]la la\n".encodeToByteArray())),
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
    fun `a README under an unknown name is reported skipped not imported as prose`() = runTest {
        val readme = "# Campfire\nSee [C](https://example.com) for the details.\n- A\n- B\n"

        val plan = prepare()(listOf(ImportedFile("README.md", readme.encodeToByteArray(), isPlainTextByContent = true)))

        assertEquals(listOf("README.md"), plan.skippedFileNames)
        assertTrue(plan.songs.isEmpty())
    }

    @Test
    fun `a bytes-only archive of a licence text is reported as one file`() = runTest {
        val staging = FakeStaging()
        val licence = "Permission is hereby granted, free of charge, to any person obtaining a copy.\n"
        val archive = FakeArchiveRepository(staging = staging) { _, _ -> listOf(ImportedFile("LICENSE.txt", licence.encodeToByteArray())) }

        val plan = prepare(archive = archive)(listOf(staging.received(ImportedFile("book.epub", byteArrayOf(0x50, 0x4B, 3, 4, 0)))))

        assertEquals(listOf("book.epub"), plan.skippedFileNames)
        assertTrue(plan.songs.isEmpty())
    }

    @Test
    fun `an unknown name with a dot in it keeps its whole name as the title`() = runTest {
        val plan = prepare()(listOf(ImportedFile("Mr. Brightside", SHEET.encodeToByteArray(), isPlainTextByContent = true)))

        assertEquals("Mr. Brightside", plan.songs.single().title)
    }

    @Test
    fun `a loose document of songs is planned as its songs - each with the document's origin`() = runTest {
        val archive = FakeArchiveRepository(songsIn = { file -> if (file.name == "songs.json") listOf(song("One.cho"), song("Two.cho")) else null })

        val plan = prepare(archive = archive)(listOf(song("first.cho"), ImportedFile("songs.json", "[]".encodeToByteArray())))

        assertEquals(listOf("first.cho", "One.cho", "Two.cho"), plan.songs.map { it.sourceFileName })
        assertEquals(listOf(null, 1, 1), plan.songs.map { it.origin })
    }

    @Test
    fun `a loose OnSong file is planned as the song its document holds not skipped`() = runTest {
        val archive = FakeArchiveRepository(songsIn = { file -> if (file.name == "Riverlight.onsong") listOf(song("Riverlight.cho")) else null })

        val plan = prepare(archive = archive)(listOf(ImportedFile("Riverlight.onsong", "Riverlight\n\nG D\nLa".encodeToByteArray())))

        assertEquals(listOf("Riverlight.cho"), plan.songs.map { it.sourceFileName })
        assertEquals(emptyList(), plan.skippedFileNames)
    }

    @Test
    fun `a pasted ABC tune is one converted song with a chord grid and the tune kept`() = runTest {
        val tune = "X:1\nT:Lantern Reel\nC:Campfire Players\nK:G\n\"G\"GABc \"D\"dedB|\"C\"cBAG|"

        val song = prepare()(listOf(ImportedFile("Pasted.txt", tune.encodeToByteArray()))).songs.single()

        assertTrue(song.isConverted)
        assertEquals("Lantern Reel", ChordProParser.parseMetadata(song.text).title)
        val lines = ChordProParser.parse(song.text).blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }
        assertEquals(listOf("G", "D", "C"), lines.filterIsInstance<ChordProLine.Grid>().flatMap { it.tokens }.filterIsInstance<GridToken.Chord>().map { it.name })
        assertTrue(lines.any { it is ChordProLine.Verbatim && it.text == "X:1" })
    }

    @Test
    fun `two pasted ABC tunes are two songs`() = runTest {
        val tunes = "X:1\nT:Lantern Reel\nK:G\nGABc|\n\nX:2\nT:Meadow Jig\nK:D\nDEF|"

        val songs = prepare()(listOf(ImportedFile("Pasted.txt", tunes.encodeToByteArray()))).songs

        assertEquals(listOf("Lantern Reel", "Meadow Jig"), songs.map { ChordProParser.parseMetadata(it.text).title })
    }

    @Test
    fun `pasted ChordPro holding an ABC block stays the ChordPro it is`() = runTest {
        val text = "{title: Lantern Reel}\n\n{start_of_verse}\n[G]Down by the [D]river\n{end_of_verse}\n\n{start_of_abc}\nX:1\nT:Lantern Reel\nK:G\nGABc|\n{end_of_abc}\n"

        val song = prepare()(listOf(ImportedFile("Pasted.txt", text.encodeToByteArray()))).songs.single()

        val sections = ChordProParser.parse(song.text).blocks.filterIsInstance<ChordProBlock.Section>()
        assertEquals(1, sections.count { section -> section.lines.any { it is ChordProLine.Verbatim } })
        assertTrue(sections.none { section -> section.lines.any { it is ChordProLine.Grid } })
        assertTrue("[G]Down by the [D]river" in song.text)
    }

    @Test
    fun `a setlist is still read as one`() = runTest {
        val plan = prepare(parseSetlist = { ParsedSetlist(testSetlist(fileName = "gig.setlist.json"), isDated = true) })(
            listOf(ImportedFile("gig.setlist.json", "{}".encodeToByteArray())),
        )

        assertEquals(listOf("gig.setlist.json"), plan.setlists.map { it.sourceFileName })
    }

    @Test
    fun `a page's clutter is left out of its song and the whole page is kept for the editor`() = runTest {
        val plan = prepare(pageCropper = FakePageCropper { songLines(3, 4, 6, 7) })(listOf(page(PAGE)))

        val song = plan.songs.single()
        assertTrue("river light" in song.text && "morning" in song.text)
        assertTrue("Sign in" !in song.text && "Great song" !in song.text)
        assertTrue(song.wholePageText.orEmpty().let { "Great song" in it && "river light" in it })
    }

    @Test
    fun `a page's title line before its song leaves the song and is suggested as its title and artist`() = runTest {
        val page = "Sign in\nRiverlight Chords by The Invented Band\nStrumming: down down up\n\n" + PAGE.substringAfter("Sign in\n\n")
        val plan = prepare(pageCropper = FakePageCropper { songLines(2, 4, 5, 7, 8, titleLine = 1, headerLines = listOf(1)) })(listOf(page(page)))

        val song = plan.songs.single()
        assertEquals("Riverlight", song.pageTitle)
        assertEquals("The Invented Band", song.pageArtist)
        assertTrue("Riverlight" !in song.text && "river light" in song.text)
        assertTrue(song.wholePageText.orEmpty().let { "Riverlight" in it && "Great song" in it })
    }

    @Test
    fun `a page's song with no title line is suggested nothing`() = runTest {
        assertEquals(null, prepare(pageCropper = FakePageCropper { songLines(3, 4, 6, 7) })(listOf(page(PAGE))).songs.single().pageTitle)
    }

    @Test
    fun `a page the cropper finds no song in is converted whole`() = runTest {
        val plan = prepare(pageCropper = FakePageCropper { null })(listOf(page(PAGE)))

        assertTrue("Great song" in plan.songs.single().text)
        assertEquals(null, plan.songs.single().wholePageText)
    }

    @Test
    fun `a page is converted whole when the cropper fails`() = runTest {
        val plan = prepare(pageCropper = FakePageCropper { error("No weights") })(listOf(page(PAGE)))

        assertTrue("Great song" in plan.songs.single().text)
        assertEquals(null, plan.songs.single().wholePageText)
    }

    @Test
    fun `a text file is never cropped`() = runTest {
        val cropper = FakePageCropper { songLines(3, 4, 6, 7) }

        val plan = prepare(pageCropper = cropper)(listOf(ImportedFile("page.txt", PAGE.encodeToByteArray())))

        assertTrue(cropper.asked.isEmpty())
        assertTrue("Great song" in plan.songs.single().text)
    }

    @Test
    fun `ChordPro from a page is never cropped`() = runTest {
        val cropper = FakePageCropper { songLines(0) }

        val plan = prepare(pageCropper = cropper)(listOf(page("{title: River}\n[G]Down by the [D]river light\n[C]We sang until morning\n")))

        assertTrue(cropper.asked.isEmpty())
        assertTrue("morning" in plan.songs.single().text)
    }

    private fun page(text: String) = ImportedFile("page.txt", text.encodeToByteArray(), isPageText = true)

    private fun song(name: String) = ImportedFile(name, "{title: ${name.substringBeforeLast('.')}}\n".encodeToByteArray())

    private companion object {
        const val PAGE = "Home | Chords | Tabs\nSign in\n\nG         D\nDown by the river light\n\nC         G\nWe sang until morning\n\n" +
            "Comments (12)\nGreat song!\n"
        const val SHEET = "G         D\nDown by the river light\nC         G\nWe sang until morning\n"
    }

    /** An import into an empty library, which leaves what the preparation left out the only thing two plans differ by. */
    private fun prepare(
        archive: ArchiveRepository = FakeArchiveRepository(),
        parseSetlist: (String) -> ParsedSetlist? = { null },
        pageCropper: PageCropper = FakePageCropper(),
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
            override suspend fun extract(file: ImportedFile): ChordSheet? = null
        },
        imageRepository = FakeImageRepository(),
        pictureLinks = incomingPictureLinks(),
        pageCropper = pageCropper,
        logger = Logger.Standard,
    )
}
