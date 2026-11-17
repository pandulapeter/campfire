/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.export

import com.pandulapeter.campfire.data.model.library.ExportedContent
import com.pandulapeter.campfire.domain.implementation.FakeExportFileRepository
import com.pandulapeter.campfire.domain.implementation.FakeImageRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ExportAttachmentUseCaseImplTest {

    @Test
    fun `an attachment is copied into the exports under the name the song shows it by`() = runTest {
        val bytes = "%PDF-1.7".encodeToByteArray()
        val exports = FakeExportFileRepository()
        val useCase = ExportAttachmentUseCaseImpl(FakeImageRepository(mutableMapOf(SOURCE to bytes)), exports)

        val file = useCase(source = SOURCE, name = "Rehearsal take.pdf")

        assertEquals("Rehearsal take.pdf", file?.name)
        assertEquals("application/pdf", file?.mimeType)
        assertIs<ExportedContent.TemporaryFile>(file?.content)
        assertContentEquals(bytes, exports.files["Rehearsal take.pdf"])
    }

    @Test
    fun `a file neither shelf holds is no copy`() = runTest {
        val exports = FakeExportFileRepository()

        assertNull(ExportAttachmentUseCaseImpl(FakeImageRepository(), exports)(source = SOURCE, name = "Sheet.pdf"))
        assertEquals(emptyMap(), exports.files)
    }

    @Test
    fun `the copy's name keeps the file's extension and nothing a file system refuses`() {
        assertEquals("Sheet music.pdf", attachmentFileName(SOURCE, "Sheet music"))
        assertEquals("Sheet music.PDF", attachmentFileName(SOURCE, "Sheet music.PDF"))
        assertEquals("a_b_c_.pdf", attachmentFileName(SOURCE, "a/b\\c?"))
        assertEquals("hidden.pdf", attachmentFileName(SOURCE, "..hidden"))
        assertEquals("attachment.pdf", attachmentFileName(SOURCE, "  "))
        assertEquals("x".repeat(120) + ".pdf", attachmentFileName(SOURCE, "x".repeat(300)))
    }

    private companion object {
        const val SOURCE = "3fa9c2e81b7d4f06.pdf"
    }
}
