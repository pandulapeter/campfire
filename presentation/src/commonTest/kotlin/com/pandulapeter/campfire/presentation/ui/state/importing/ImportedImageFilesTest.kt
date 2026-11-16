/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportedImageFilesTest {

    private val pictures = listOf(ImportedFile(name = "Chart page 1.jpg", bytes = byteArrayOf(1)), ImportedFile(name = "page 2.png", bytes = byteArrayOf(2)))

    @Test
    fun `pictures alone are one new song showing them in order, titled by the first one's name`() {
        val song = ImportPlan(imageFiles = pictures).pictureSong()!!
        assertEquals("Chart page 1", song.title)
        assertEquals(listOf("Chart page 1.jpg", "page 2.png"), song.images.map { it.source })
        assertEquals(listOf(1.toByte(), 2.toByte()), song.images.map { it.bytes!!.single() })
        assertEquals(ImportPlan.Status.NEW, song.status)
    }

    @Test
    fun `pictures beside anything else make no song, and are reported as skipped`() {
        val plan = ImportPlan(imageFiles = pictures, skippedFileNames = listOf("notes.docx"))
        assertNull(plan.pictureSong())
        assertEquals(listOf("notes.docx", "Chart page 1.jpg", "page 2.png"), plan.withImageFilesSkipped().skippedFileNames)
        assertEquals(emptyList(), plan.withImageFilesSkipped().imageFiles)
    }

    @Test
    fun `a batch with no pictures makes no picture song`() {
        assertNull(ImportPlan().pictureSong())
    }
}
