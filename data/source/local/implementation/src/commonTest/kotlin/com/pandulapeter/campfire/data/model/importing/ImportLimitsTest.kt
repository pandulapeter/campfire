/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.importing

import com.pandulapeter.campfire.data.formats.zip.ZipEntry
import com.pandulapeter.campfire.data.formats.zip.ZipWriter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules of `:data:model`'s [ImportBudget], which every platform reads its incoming files through. Tested here rather than
 * in `:data:model`, which cannot depend on the zip writer of `:data:formats` that builds the archive the rules recognise.
 */
internal class ImportLimitsTest {

    @Test
    fun `a file of an unknown type is kept only where it is an archive`() {
        val budget = ImportBudget()
        val video = ByteArray(10 shl 20)
        val archive = ZipWriter.write(listOf(ZipEntry("a.cho", "{title: A}".encodeToByteArray())))

        val skipped = List(3) { index -> budget.read(name = "$index.mp4", size = video.size.toLong()) { video } }
        val backup = budget.read(name = "library.sbpbackup2", size = archive.size.toLong()) { archive }

        // Unread and unsupported rather than too large, and giving back what they took, so the archive after them fits.
        assertEquals(List(3) { index -> ImportedFile.unread("$index.mp4") }, skipped)
        assertContentEquals(archive, backup!!.bytes)
    }

    @Test
    fun `an unknown loose file that is text is kept flagged, a binary one is not`() {
        val budget = ImportBudget()
        val sheet = "G   D\nDown by the river\n".encodeToByteArray()
        val binary = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 0, 1, 2)

        val text = budget.read(name = "riverlight.onsong", size = sheet.size.toLong()) { sheet }
        val program = budget.read(name = "riverlight", size = binary.size.toLong()) { binary }
        val photo = budget.read(name = "cover.png", size = sheet.size.toLong()) { sheet }

        assertTrue(text!!.isPlainTextByContent)
        assertContentEquals(sheet, text.bytes)
        assertEquals(ImportedFile.unread("riverlight"), program)
        assertEquals(ImportedFile.unread("cover.png"), photo)
    }

    @Test
    fun `a file of an unknown type larger than the selection is not read`() {
        val file = ImportBudget().read(name = "video.mp4", size = ImportLimits.MAX_IMPORT_SIZE + 1) { error("read") }

        assertEquals(ImportedFile.unread("video.mp4"), file)
        assertFalse(file!!.isTooLarge)
    }

    @Test
    fun `another app's backup is read as an archive`() {
        assertEquals(ImportLimits.MAX_IMPORT_SIZE, ImportLimits.maxSizeOf("Library.SBPBACKUP"))
        assertEquals(ImportLimits.MAX_IMPORT_SIZE, ImportLimits.maxSizeOf("set.sbp"))
    }

    @Test
    fun `a file over what is left of the selection is not read`() {
        val file = ImportBudget().read(name = "a.cho", size = ImportLimits.MAX_IMPORT_SIZE + 1) { error("read") }

        assertTrue(file!!.isTooLarge)
    }

    @Test
    fun `a file of unknown size is found out while reading`() {
        var givenLimit: Long? = null

        val file = ImportBudget().read(name = "a.cho", size = null) { limit ->
            givenLimit = limit
            ByteArray((limit + 1).toInt())
        }

        assertTrue(file!!.isTooLarge)
        assertEquals(ImportLimits.MAX_IMPORT_SIZE, givenLimit)
    }

    @Test
    fun `a zip over the text limit under a text name is read whole`() {
        val budget = ImportBudget()
        val archive = ZipWriter.write(listOf(ZipEntry("a.cho", ByteArray(ImportLimits.MAX_TEXT_FILE_SIZE.toInt()))))

        val file = budget.read(name = "backup.txt", size = archive.size.toLong()) { archive }

        assertFalse(file!!.isTooLarge)
        assertContentEquals(archive, file.bytes)
    }

    @Test
    fun `a text over its limit is still reported too large and gives its budget back`() {
        val budget = ImportBudget()
        val text = ByteArray(ImportLimits.MAX_TEXT_FILE_SIZE.toInt() + 1) { 'a'.code.toByte() }
        val rest = ByteArray((ImportLimits.MAX_IMPORT_SIZE - 1).toInt())

        val file = budget.read(name = "huge.txt", size = text.size.toLong()) { text }
        val after = budget.read(name = "after.zip", size = rest.size.toLong()) { rest }

        assertTrue(file!!.isTooLarge)
        assertFalse(after!!.isTooLarge)
    }

    @Test
    fun `the selection shares one budget`() {
        val budget = ImportBudget()
        val size = 10L shl 20

        val archives = List(3) { index -> budget.read(name = "$index.zip", size = size) { ByteArray(size.toInt()) }!! }
        val song = budget.read(name = "a.cho", size = 1L shl 20) { ByteArray(1 shl 20) }!!

        assertEquals(listOf(false, false, true), archives.map { it.isTooLarge })
        assertFalse(song.isTooLarge)
        assertEquals(1 shl 20, song.bytes.size)
    }

    @Test
    fun `an unreadable file is left out`() {
        assertNull(ImportBudget().read(name = "a.cho", size = 10) { null })
    }

    @Test
    fun `documents have their own limit`() {
        for (name in listOf("song.pdf", "song.DOCX", "song.doc")) {
            assertEquals(ImportLimits.MAX_DOCUMENT_FILE_SIZE, ImportLimits.maxSizeOf(name))
            assertTrue(ImportBudget().read(name, ImportLimits.MAX_DOCUMENT_FILE_SIZE + 1) { error("read") }!!.isTooLarge)
        }
        assertEquals(ImportLimits.MAX_TEXT_FILE_SIZE, ImportLimits.maxSizeOf("song.txt"))
    }
}
