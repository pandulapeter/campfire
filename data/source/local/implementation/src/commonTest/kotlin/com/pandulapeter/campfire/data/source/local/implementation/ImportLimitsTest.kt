/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation

import com.pandulapeter.campfire.data.model.domain.ImportBudget
import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.source.local.implementation.zip.Inflater
import com.pandulapeter.campfire.data.source.local.implementation.zip.ZipEntry
import com.pandulapeter.campfire.data.source.local.implementation.zip.ZipWriter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rules of `:data:model`'s [ImportBudget], which every platform reads its incoming files through. */
internal class ImportLimitsTest {

    @Test
    fun aFileOfAnUnknownTypeIsKeptOnlyWhereItIsAnArchive() {
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
    fun aFileOfAnUnknownTypeLargerThanTheSelectionIsNotRead() {
        val file = ImportBudget().read(name = "video.mp4", size = ImportLimits.MAX_IMPORT_SIZE + 1) { error("read") }

        assertEquals(ImportedFile.unread("video.mp4"), file)
        assertFalse(file!!.isTooLarge)
    }

    @Test
    fun anotherAppsBackupIsReadAsAnArchive() {
        assertEquals(ImportLimits.MAX_IMPORT_SIZE, ImportLimits.maxSizeOf("Library.SBPBACKUP"))
        assertEquals(ImportLimits.MAX_IMPORT_SIZE, ImportLimits.maxSizeOf("set.sbp"))
    }

    @Test
    fun aFileOverItsDeclaredSizeIsNotRead() {
        val file = ImportBudget().read(name = "a.cho", size = ImportLimits.MAX_TEXT_FILE_SIZE + 1) { error("read") }

        assertTrue(file!!.isTooLarge)
    }

    @Test
    fun aFileOfUnknownSizeIsFoundOutWhileReading() {
        var givenLimit: Long? = null

        val file = ImportBudget().read(name = "a.cho", size = null) { limit ->
            givenLimit = limit
            ByteArray((limit + 1).toInt())
        }

        assertTrue(file!!.isTooLarge)
        assertEquals(ImportLimits.MAX_TEXT_FILE_SIZE, givenLimit)
    }

    @Test
    fun theSelectionSharesOneBudget() {
        val budget = ImportBudget()
        val size = 10L shl 20

        val archives = List(3) { index -> budget.read(name = "$index.zip", size = size) { ByteArray(size.toInt()) }!! }
        val song = budget.read(name = "a.cho", size = 1L shl 20) { ByteArray(1 shl 20) }!!

        assertEquals(listOf(false, false, true), archives.map { it.isTooLarge })
        assertFalse(song.isTooLarge)
        assertEquals(1 shl 20, song.bytes.size)
    }

    @Test
    fun anUnreadableFileIsLeftOut() {
        assertNull(ImportBudget().read(name = "a.cho", size = 10) { null })
    }

    @Test
    fun theInflaterAllowsWhatAnImportDoes() {
        assertEquals(ImportLimits.MAX_IMPORT_SIZE, Inflater.MAX_ENTRY_SIZE.toLong())
    }

    @Test
    fun documentsHaveTheirOwnLimit() {
        for (name in listOf("song.pdf", "song.DOCX", "song.doc")) {
            assertEquals(ImportLimits.MAX_DOCUMENT_FILE_SIZE, ImportLimits.maxSizeOf(name))
            assertTrue(ImportBudget().read(name, ImportLimits.MAX_DOCUMENT_FILE_SIZE + 1) { error("read") }!!.isTooLarge)
        }
        assertEquals(ImportLimits.MAX_TEXT_FILE_SIZE, ImportLimits.maxSizeOf("song.txt"))
    }
}
