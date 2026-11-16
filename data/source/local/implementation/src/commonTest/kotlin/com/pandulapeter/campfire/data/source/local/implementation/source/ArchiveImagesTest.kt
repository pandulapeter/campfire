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
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.ImportedImage
import com.pandulapeter.campfire.data.model.importing.UnpackedArchive
import com.pandulapeter.campfire.data.model.library.ArchiveEntry
import com.pandulapeter.campfire.data.model.library.ExportSink
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pictures an archive's songs bring with them, found in the same archive by the names their directives give. */
internal class ArchiveImagesTest {

    private val archiveLocalSource = ArchiveLocalSourceImpl(Logger.Standard)
    private val photo = Random(1).nextBytes(1_000)
    private val otherPhoto = Random(2).nextBytes(1_000)

    @Test
    fun `a picture beside the song comes with it and is not reported as skipped`() = runTest {
        val unpacked = unpack("songs/a.cho" to song("chart.png"), "songs/chart.png" to photo)

        assertEquals(listOf("a.cho"), unpacked.files.map { it.name })
        assertFound(photo, unpacked.song("a.cho").images.single())
        assertEquals("chart.png", unpacked.song("a.cho").images.single().source)
        assertEquals(photo.size.toLong(), unpacked.imageSize)
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
        assertEquals(0, unpacked.imageSize)
    }

    @Test
    fun `a path the song gives is followed within the archive and never out of it`() = runTest {
        val relative = unpack("songs/a.cho" to song("../art/chart.png"), "art/chart.png" to photo)
        val climbing = unpack("a.cho" to song("../../chart.png"), "x/chart.png" to photo)
        val absolute = unpack("a.cho" to song("/Users/me/Pictures/chart.png"), "chart.png" to photo)
        val address = unpack("a.cho" to song("https://example.com/chart.png"), "chart.png" to photo)
        val backslashes = unpack("songs\\a.cho" to song("art\\chart.png"), "songs\\art\\chart.png" to photo)

        assertFound(photo, relative.song("a.cho").images.single())
        assertFound(photo, climbing.song("a.cho").images.single())
        assertFound(photo, absolute.song("a.cho").images.single())
        assertProblem(ImportedImage.Problem.MISSING, address.song("a.cho").images.single())
        assertFound(photo, backslashes.song("a.cho").images.single())
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

        assertEquals(listOf("a.cho", "holiday.jpg"), unpacked.files.map { it.name })
        assertTrue(unpacked.files.single { it.name == "holiday.jpg" }.bytes.isEmpty())
        assertEquals(photo.size.toLong(), unpacked.imageSize)
    }

    @Test
    fun `an archive whose songs name no picture reads none`() = runTest {
        val unpacked = unpack("a.cho" to "{title: A}".encodeToByteArray(), "chart.png" to photo)

        assertTrue(unpacked.song("a.cho").images.isEmpty())
        assertEquals(listOf("a.cho", "chart.png"), unpacked.files.map { it.name })
        assertEquals(0, unpacked.imageSize)
    }

    @Test
    fun `only a ChordPro song brings pictures and never a text another reader converts`() = runTest {
        val unpacked = unpack("a.txt" to song("chart.png"), "chart.png" to photo)

        assertTrue(unpacked.files.single { it.name == "a.txt" }.images.isEmpty())
        assertEquals(0, unpacked.imageSize)
    }

    @Test
    fun `a hidden file is never taken for a picture`() = runTest {
        val unpacked = unpack("a.cho" to song("chart.png"), "__MACOSX/._chart.png" to otherPhoto, ".cache/chart.png" to otherPhoto)

        assertProblem(ImportedImage.Problem.MISSING, unpacked.song("a.cho").images.single())
        assertEquals(listOf("a.cho"), unpacked.files.map { it.name })
    }

    @Test
    fun `a picture named by several songs is read once`() = runTest {
        val unpacked = unpack("a.cho" to song("chart.png"), "b.cho" to song("chart.png"), "images/chart.png" to photo)

        assertFound(photo, unpacked.song("a.cho").images.single())
        assertFound(photo, unpacked.song("b.cho").images.single())
        assertEquals(photo.size.toLong(), unpacked.imageSize)
    }

    @Test
    fun `a picture over the size one may have is reported too large and not read`() = runTest {
        val huge = Random(3).nextBytes(ImportLimits.MAX_IMAGE_FILE_SIZE.toInt() + 1)

        val unpacked = unpack("a.cho" to song("huge.png", "chart.png"), "huge.png" to huge, "chart.png" to photo)

        val images = unpacked.song("a.cho").images.associateBy { it.source }
        assertProblem(ImportedImage.Problem.TOO_LARGE, images.getValue("huge.png"))
        assertFound(photo, images.getValue("chart.png"))
        assertEquals(photo.size.toLong(), unpacked.imageSize)
    }

    @Test
    fun `pictures past the import's picture budget are reported too large apart from the songs' budget`() = runTest {
        val archive = archiveOf("a.cho" to song("one.png", "two.png"), "one.png" to photo, "two.png" to otherPhoto)

        val unpacked = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE, maxImageSize = photo.size + 10L)

        val problems = unpacked.song("a.cho").images.map { it.problem }
        assertEquals(listOf(null, ImportedImage.Problem.TOO_LARGE), problems)
        assertEquals(photo.size.toLong(), unpacked.imageSize)
    }

    @Test
    fun `a nested archive's songs find pictures in their own archive within the same picture budget`() = runTest {
        val inner = archiveOf("b.cho" to song("chart.png"), "chart.png" to otherPhoto)
        val archive = archiveOf("a.cho" to song("chart.png"), "chart.png" to photo, "inner.zip" to inner)

        val unpacked = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE, maxImageSize = photo.size + 10L)

        assertFound(photo, unpacked.song("a.cho").images.single())
        assertProblem(ImportedImage.Problem.TOO_LARGE, unpacked.song("b.cho").images.single())
    }

    @Test
    fun `an export of a song with its pictures unpacks to the same text and the same pictures`() = runTest {
        val text = "{title: A}\n{image: src=3f9a0c1d2e3f4a5b.png}\n{image: src=0123456789abcdef.jpg}\n"
        val sink = BufferSink()
        archiveLocalSource.pack(
            entries = listOf(
                { ArchiveEntry("a.cho", text.encodeToByteArray()) },
                { ArchiveEntry("images/0123456789abcdef.jpg", otherPhoto) },
                { ArchiveEntry("images/3f9a0c1d2e3f4a5b.png", photo) },
            ),
            isDated = false,
            sink = sink,
        )

        val unpacked = archiveLocalSource.unpack(archive = sink.bytes(), maxSize = ImportLimits.MAX_IMPORT_SIZE, maxImageSize = ImportLimits.MAX_IMPORT_IMAGES_SIZE)

        assertEquals(listOf("a.cho"), unpacked.files.map { it.name })
        assertEquals(text, unpacked.song("a.cho").bytes.decodeToString())
        val images = unpacked.song("a.cho").images.associateBy { it.source }
        assertFound(photo, images.getValue("3f9a0c1d2e3f4a5b.png"))
        assertFound(otherPhoto, images.getValue("0123456789abcdef.jpg"))
    }

    private suspend fun unpack(vararg entries: Pair<String, ByteArray>) = archiveLocalSource.unpack(
        archive = archiveOf(*entries),
        maxSize = ImportLimits.MAX_IMPORT_SIZE,
        maxImageSize = ImportLimits.MAX_IMPORT_IMAGES_SIZE,
    )

    private suspend fun archiveOf(vararg entries: Pair<String, ByteArray>) = ZipWriter.write(entries.map { (name, bytes) -> ZipEntry(name, bytes) })

    private fun song(vararg sources: String) = (listOf("{title: A}") + sources.map { "{image: src=\"$it\"}" }).joinToString("\n").encodeToByteArray()

    private fun UnpackedArchive.song(name: String): ImportedFile = files.single { it.name == name }

    private fun assertFound(expected: ByteArray, image: ImportedImage) {
        assertNull(image.problem)
        assertContentEquals(expected, image.bytes)
    }

    private fun assertProblem(expected: ImportedImage.Problem, image: ImportedImage) {
        assertEquals(expected, image.problem)
        assertNull(image.bytes)
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
