/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.library

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ExportedFileTest {

    @Test
    fun `an archive is too large to import by what it unpacks to, however small it is compressed`() {
        assertTrue(archive(size = 1_000, unpackedSize = ImportLimits.MAX_IMPORT_SIZE + 1).isTooLargeToImport)
        assertFalse(archive(size = 1_000, unpackedSize = ImportLimits.MAX_IMPORT_SIZE).isTooLargeToImport)
    }

    @Test
    fun `an archive is too large to import by its own size too`() {
        assertTrue(archive(size = ImportLimits.MAX_IMPORT_SIZE.toInt() + 1, unpackedSize = 1_000).isTooLargeToImport)
    }

    @Test
    fun `a file that is no archive is never too large to import`() {
        val pdf = ExportedFile(name = "songs.pdf", mimeType = ExportedFile.PDF_MIME_TYPE, bytes = ByteArray(0), unpackedSize = Long.MAX_VALUE)

        assertFalse(pdf.isTooLargeToImport)
    }

    private fun archive(size: Int, unpackedSize: Long) =
        ExportedFile(name = "songs.zip", mimeType = ExportedFile.ZIP_MIME_TYPE, bytes = ByteArray(size), unpackedSize = unpackedSize)
}
