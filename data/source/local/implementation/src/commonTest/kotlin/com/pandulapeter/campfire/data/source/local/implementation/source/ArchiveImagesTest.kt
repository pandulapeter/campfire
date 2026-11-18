/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.formats.zip.ZipEntry
import com.pandulapeter.campfire.data.formats.zip.ZipWriter
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.ImportedImage
import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.data.model.library.ArchiveEntry
import com.pandulapeter.campfire.data.model.library.ExportSink
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pictures an archive's songs bring with them, found in the same archive by the names their directives give and
 * staged from the archive in the staging folder (see [ArchiveUnpacks]).
 */
internal class ArchiveImagesTest {

    private val unpacks = ArchiveUnpacks()
    private val photo = Random(1).nextBytes(1_000)
    private val otherPhoto = Random(2).nextBytes(1_000)

    @Test
    fun `a picture beside the song comes with it and is not reported as skipped`() = runTest {
        val unpacked = unpack("songs/a.cho" to song("chart.png"), "songs/chart.png" to photo)

        assertEquals(listOf("a.cho"), unpacked.map { it.name })
        assertFound(photo, unpacked.song("a.cho").images.single())
        assertEquals("chart.png", unpacked.song("a.cho").images.single().source)
    }

    @Test
    fun `a picture in an images folder at the root or beside the song comes with it`() = runTest {
        val atRoot = unpack("songs/a.cho" to song("chart.png"), "images/chart.png" to photo)
        val besideSong = unpack("set/a.cho" to song("chart.png"), "set/images/chart.png" to photo)

        assertFound(photo, atRoot.song("a.cho").images.single())
        assertFound(photo, besideSong.song("a.cho").images.single())
    }

    @Test
    fun `the file beside the song is taken before one in an images folder of the same name`() = runTest {
        val unpacked = unpack("a.cho" to song("chart.png"), "images/chart.png" to otherPhoto, "chart.png" to photo)

        assertFound(photo, unpacked.song("a.cho").images.single())
    }

    @Test
    fun `a picture anywhere else in the archive is found by its file name and in another case only where it is alone`() = runTest {
        val elsewhere = unpack("a.cho" to song("chart.png"), "photos/2024/chart.png" to photo)
        val otherCase = unpack("a.cho" to song("chart.png"), "Photos/CHART.PNG" to photo)
        val exactCaseFirst = unpack("a.cho" to song("chart.png"), "p/CHART.png" to otherPhoto, "q/chart.png" to photo)

        assertFound(photo, elsewhere.song("a.cho").images.single())
        assertFound(photo, otherCase.song("a.cho").images.single())
        assertFound(photo, exactCaseFirst.song("a.cho").images.single())
    }

    @Test
    fun `two pictures answering by file name alone are no answer`() = runTest {
        val unpacked = unpack("a.cho" to song("chart.png"), "one/chart.png" to photo, "two/chart.png" to otherPhoto)

        assertProblem(ImportedImage.Problem.MISSING, unpacked.song("a.cho").images.single())
    }

    @Test
    fun `a path the song gives is followed within the archive and never out of it`() = runTest {
        val relative = unpack("songs/a.cho" to song("../art/chart.png"), "art/chart.png" to photo)
        val climbing = unpack("a.cho" to song("../../chart.png"), "x/chart.png" to photo)
        val absolute = unpack("a.cho" to song("/Users/me/Pictures/chart.png"), "chart.png" to photo)
        val address = unpack("a.cho" to song("https://example.com/chart.png"), "chart.png" to photo)
        val backslashes = unpack("songs\\a.cho" to song("art\\chart.png"), "songs\\art\\chart.png" to photo)
        // Two entries of the name, so only the path can answer.
        val aboveRoot = unpack("a.cho" to song("../chart.png"), "chart.png" to photo, "x/chart.png" to otherPhoto)
        val intoImages = unpack("songs/a.cho" to song("../images/chart.png"), "images/chart.png" to photo, "x/chart.png" to otherPhoto)

        assertFound(photo, relative.song("a.cho").images.single())
        assertFound(photo, climbing.song("a.cho").images.single())
        assertFound(photo, absolute.song("a.cho").images.single())
        assertProblem(ImportedImage.Problem.MISSING, address.song("a.cho").images.single())
        assertFound(photo, backslashes.song("a.cho").images.single())
        assertProblem(ImportedImage.Problem.MISSING, aboveRoot.song("a.cho").images.single())
        assertFound(photo, intoImages.song("a.cho").images.single())
    }

    @Test
    fun `a picture the archive lacks is reported with its song and the song still comes`() = runTest {
        val unpacked = unpack("a.cho" to song("chart.png", "page two.jpg"), "chart.png" to photo)

        val images = unpacked.song("a.cho").images.associateBy { it.source }
        assertFound(photo, images.getValue("chart.png"))
        assertProblem(ImportedImage.Problem.MISSING, images.getValue("page two.jpg"))
    }

    @Test
    fun `a picture no song names is never read and is reported as skipped`() = runTest {
        val unpacked = unpack("a.cho" to song("chart.png"), "chart.png" to photo, "holiday.jpg" to otherPhoto)

        assertEquals(listOf("a.cho", "holiday.jpg"), unpacked.map { it.name })
        assertTrue(unpacked.single { it.name == "holiday.jpg" }.bytes.isEmpty())
    }

    @Test
    fun `an archive whose songs name no picture reads none`() = runTest {
        val unpacked = unpack("a.cho" to "{title: A}".encodeToByteArray(), "chart.png" to photo)

        assertTrue(unpacked.song("a.cho").images.isEmpty())
        assertEquals(listOf("a.cho", "chart.png"), unpacked.map { it.name })
    }

    @Test
    fun `only a ChordPro song brings pictures and never a text another reader converts`() = runTest {
        val unpacked = unpack("a.txt" to song("chart.png"), "chart.png" to photo)

        assertTrue(unpacked.single { it.name == "a.txt" }.images.isEmpty())
    }

    @Test
    fun `a hidden file is never taken for a picture`() = runTest {
        val unpacked = unpack("a.cho" to song("chart.png"), "__MACOSX/._chart.png" to otherPhoto, ".cache/chart.png" to otherPhoto)

        assertProblem(ImportedImage.Problem.MISSING, unpacked.song("a.cho").images.single())
        assertEquals(listOf("a.cho"), unpacked.map { it.name })
    }

    @Test
    fun `a picture named by several songs is read once`() = runTest {
        val unpacked = unpack("a.cho" to song("chart.png"), "b.cho" to song("chart.png"), "images/chart.png" to photo)

        assertFound(photo, unpacked.song("a.cho").images.single())
        assertFound(photo, unpacked.song("b.cho").images.single())
    }

    @Test
    fun `a picture over the size one may have is reported too large and not read`() = runTest {
        val huge = Random(3).nextBytes(ImportLimits.MAX_IMAGE_FILE_SIZE.toInt() + 1)

        val unpacked = unpack("a.cho" to song("huge.png", "chart.png"), "huge.png" to huge, "chart.png" to photo)

        val images = unpacked.song("a.cho").images.associateBy { it.source }
        assertProblem(ImportedImage.Problem.TOO_LARGE, images.getValue("huge.png"))
        assertFound(photo, images.getValue("chart.png"))
    }

    @Test
    fun `a nested archive's songs find pictures in their own archive`() = runTest {
        val inner = archiveOf("b.cho" to song("chart.png"), "chart.png" to otherPhoto)

        val unpacked = unpack("a.cho" to song("chart.png"), "chart.png" to photo, "inner.zip" to inner)

        assertFound(photo, unpacked.song("a.cho").images.single())
        assertFound(otherPhoto, unpacked.song("b.cho").images.single())
    }

    @Test
    fun `an export of a song with its pictures unpacks to the same text and the same pictures`() = runTest {
        val text = "{title: A}\n{image: src=3f9a0c1d2e3f4a5b.png}\n{image: src=0123456789abcdef.jpg}\n"
        val sink = BufferSink()
        unpacks.archiveLocalSource.pack(
            entries = listOf(
                { ArchiveEntry("a.cho", text.encodeToByteArray()) },
                { ArchiveEntry("images/0123456789abcdef.jpg", otherPhoto) },
                { ArchiveEntry("images/3f9a0c1d2e3f4a5b.png", photo) },
            ),
            isDated = false,
            sink = sink,
        )

        val unpacked = unpacks.unpack(sink.bytes())

        assertEquals(listOf("a.cho"), unpacked.map { it.name })
        assertEquals(text, unpacked.song("a.cho").bytes.decodeToString())
        val images = unpacked.song("a.cho").images.associateBy { it.source }
        assertFound(photo, images.getValue("3f9a0c1d2e3f4a5b.png"))
        assertFound(otherPhoto, images.getValue("0123456789abcdef.jpg"))
    }

    @Test
    fun `a song's attachments come with it from the archive's images folder apart from its pictures`() = runTest {
        val text = "{title: A}\n{image: src=3f9a0c1d2e3f4a5b.png}\n{meta: attachment 0123456789abcdef.pdf Sheet.pdf}\n"

        val unpacked = unpack("a.cho" to text.encodeToByteArray(), "images/3f9a0c1d2e3f4a5b.png" to photo, "images/0123456789abcdef.pdf" to otherPhoto)

        assertEquals(listOf("a.cho"), unpacked.map { it.name })
        val (pictures, attachments) = unpacked.song("a.cho").images.partition { !it.isAttachment }
        assertFound(photo, pictures.single())
        assertEquals("0123456789abcdef.pdf", attachments.single().source)
        assertFound(otherPhoto, attachments.single())
    }

    @Test
    fun `an attachment larger than a picture may be is read within the attachment limit`() = runTest {
        val recording = Random(4).nextBytes(ImportLimits.MAX_IMAGE_FILE_SIZE.toInt() + 1)

        val unpacked = unpack("a.cho" to attachmentSong("0123456789abcdef.mp3"), "0123456789abcdef.mp3" to recording)

        assertFound(recording, unpacked.song("a.cho").images.single())
    }

    @Test
    fun `an archive a song attaches is its attachment and is not unpacked under any name`() = runTest {
        val inner = archiveOf("b.cho" to song("chart.png"), "chart.png" to photo)

        for (name in listOf("songs.zip", "notes.txt")) {
            val unpacked = unpack("a.cho" to attachmentSong(name), name to inner)

            assertEquals(listOf("a.cho"), unpacked.map { it.name })
            assertFound(inner, unpacked.song("a.cho").images.single())
        }
    }

    @Test
    fun `the songs of nested archives bring the pictures of their own archive down to the last level`() = runTest {
        val deepest = archiveOf("c.cho" to song("chart.png"), "chart.png" to otherPhoto)
        val middle = archiveOf("b.cho" to song("chart.png"), "chart.png" to photo, "deepest.zip" to deepest)

        val unpacked = unpack("a.cho" to song("chart.png"), "middle.zip" to middle, "backup.txt" to middle)

        assertEquals(listOf("a.cho", "b.cho", "c.cho", "b.cho", "c.cho"), unpacked.map { it.name })
        assertProblem(ImportedImage.Problem.MISSING, unpacked.song("a.cho").images.single())
        unpacked.filter { it.name == "b.cho" }.forEach { assertFound(photo, it.images.single()) }
        unpacked.filter { it.name == "c.cho" }.forEach { assertFound(otherPhoto, it.images.single()) }
    }

    @Test
    fun `a missing attachment is reported with its song which still comes`() = runTest {
        val unpacked = unpack("a.cho" to attachmentSong("0123456789abcdef.pdf"))

        val attachment = unpacked.song("a.cho").images.single()
        assertTrue(attachment.isAttachment)
        assertProblem(ImportedImage.Problem.MISSING, attachment)
    }

    private suspend fun unpack(vararg entries: Pair<String, ByteArray>) = unpacks.unpack(archiveOf(*entries))

    private fun attachmentSong(vararg sources: String) =
        (listOf("{title: A}") + sources.map { "{meta: attachment $it Take}" }).joinToString("\n").encodeToByteArray()

    private suspend fun archiveOf(vararg entries: Pair<String, ByteArray>) = ZipWriter.write(entries.map { (name, bytes) -> ZipEntry(name, bytes) })

    private fun song(vararg sources: String) = (listOf("{title: A}") + sources.map { "{image: src=\"$it\"}" }).joinToString("\n").encodeToByteArray()

    private fun List<ImportedFile>.song(name: String): ImportedFile = single { it.name == name }

    private fun assertFound(expected: ByteArray, image: ImportedImage) {
        assertNull(image.problem)
        assertEquals(IncomingContent.Bytes(expected), image.content)
    }

    private fun assertProblem(expected: ImportedImage.Problem, image: ImportedImage) {
        assertEquals(expected, image.problem)
        assertNull(image.content)
    }

    private class BufferSink : ExportSink {
        private val written = mutableListOf<Byte>()
        override val position get() = written.size.toLong()

        override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
            for (index in offset until offset + length) written += bytes[index]
        }

        fun bytes() = written.toByteArray()
    }
}
