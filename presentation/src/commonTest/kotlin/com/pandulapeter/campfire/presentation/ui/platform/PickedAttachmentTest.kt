/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class PickedAttachmentTest {

    @Test
    fun `a file of a type a song takes is read whole`() {
        assertEquals(ImportedFile(name = "Take.M4A", bytes = byteArrayOf(1, 2)), pickedAttachment("Take.M4A", size = 2) { byteArrayOf(1, 2) })
    }

    @Test
    fun `a file of another type, or of none, is never read`() {
        assertEquals(ImportedFile.unread("setup.exe"), pickedAttachment("setup.exe", size = 2) { fail("read") })
        assertEquals(ImportedFile.unread("README"), pickedAttachment("README", size = 2) { fail("read") })
        assertFalse(isAttachmentFileName("pdf"))
        assertTrue(isAttachmentFileName("Sheet.PDF"))
    }

    @Test
    fun `a file larger than a song carries is too large, whether its size says so or its read does`() {
        val limit = ImportLimits.MAX_PICKED_ATTACHMENT_SIZE
        assertTrue(pickedAttachment("concert.mp4", size = limit + 1) { fail("read") }.isTooLarge)
        assertTrue(pickedAttachment("concert.mp4", size = null) { ByteArray(it.toInt() + 1) }.isTooLarge)
    }

    @Test
    fun `what the messages call 50 MB is taken and a byte more is not`() {
        assertFalse(pickedAttachment("concert.mp4", size = 50_000_000) { ByteArray(50_000_000) }.isTooLarge)
        assertTrue(pickedAttachment("concert.mp4", size = 50_000_001) { fail("read") }.isTooLarge)
        assertTrue(pickedAttachment("concert.mp4", size = null) { ByteArray(50_000_001) }.isTooLarge)
    }

    @Test
    fun `a file that cannot be read is handed back empty`() {
        assertEquals(ImportedFile.unread("sheet.pdf"), pickedAttachment("sheet.pdf", size = null) { null })
    }
}
