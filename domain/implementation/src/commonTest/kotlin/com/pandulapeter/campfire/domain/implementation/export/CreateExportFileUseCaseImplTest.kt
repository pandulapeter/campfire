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

import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.domain.implementation.FakeExportFileRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CreateExportFileUseCaseImplTest {

    private val exportFiles = FakeExportFileRepository()
    private val useCase = CreateExportFileUseCaseImpl(exportFileRepository = exportFiles)

    @Test
    fun `two exports of one name in flight keep their own bytes and discarding one leaves the other`() = runTest {
        val first = useCase("songs.pdf")
        val second = useCase("songs.pdf")
        first.write(byteArrayOf(1), 0, 1)
        second.write(byteArrayOf(2, 2), 0, 2)
        val firstFile = ExportedFile(name = "songs.pdf", mimeType = ExportedFile.PDF_MIME_TYPE, content = first.finish())
        val secondFile = ExportedFile(name = "songs.pdf", mimeType = ExportedFile.PDF_MIME_TYPE, content = second.finish())

        exportFiles.discard(firstFile)

        assertNotEquals(first.name, second.name)
        assertNull(exportFiles.read(firstFile))
        assertContentEquals(byteArrayOf(2, 2), exportFiles.read(secondFile))
    }
}
