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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rules of `:data:model`'s [ImportBudget], which every platform reads its incoming files through. */
internal class ImportLimitsTest {

    @Test
    fun aFileTheImportWouldNotLookInsideIsNeverRead() {
        val file = ImportBudget().read(name = "video.mp4", size = 1) { error("read") }

        assertEquals(ImportedFile.unread("video.mp4"), file)
        assertFalse(file!!.isTooLarge)
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
}
