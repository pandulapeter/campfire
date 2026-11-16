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
        assertTrue(archive(size = 1_000L, unpackedSize = ImportLimits.MAX_IMPORT_SIZE + 1).isTooLargeToImport)
        assertFalse(archive(size = 1_000L, unpackedSize = ImportLimits.MAX_IMPORT_SIZE).isTooLargeToImport)
    }

    @Test
    fun `an archive is too large to import by its own size too, which may hold the songs' budget and the pictures'`() {
        assertTrue(archive(size = ImportLimits.MAX_ARCHIVE_SIZE + 1, unpackedSize = 1_000).isTooLargeToImport)
        assertFalse(archive(size = ImportLimits.MAX_ARCHIVE_SIZE, unpackedSize = 1_000).isTooLargeToImport)
    }

    @Test
    fun `pictures are judged by their own budget, never by the songs'`() {
        val songs = ImportLimits.MAX_IMPORT_SIZE
        val pictures = ImportLimits.MAX_IMPORT_IMAGES_SIZE

        assertFalse(archive(size = 1_000L, unpackedSize = songs + pictures, unpackedImageSize = pictures).isTooLargeToImport)
        assertTrue(archive(size = 1_000L, unpackedSize = songs + pictures + 1, unpackedImageSize = pictures).isTooLargeToImport)
        assertTrue(archive(size = 1_000L, unpackedSize = pictures + 1, unpackedImageSize = pictures + 1).isTooLargeToImport)
    }

    @Test
    fun `a shared copy may be no larger than every version opening a link downloads, pictures included`() {
        val limit = ImportLimits.MAX_SHARED_COPY_SIZE

        assertFalse(archive(size = 1_000L, unpackedSize = limit, unpackedImageSize = limit - 1_000).isTooLargeToShare)
        assertTrue(archive(size = 1_000L, unpackedSize = limit + 1, unpackedImageSize = limit).isTooLargeToShare)
        assertTrue(archive(size = limit + 1, unpackedSize = 1_000L).isTooLargeToShare)
    }

    @Test
    fun `a file that is no archive is never too large to import`() {
        val pdf = ExportedFile(name = "songs.pdf", mimeType = ExportedFile.PDF_MIME_TYPE, content = ExportedContent.Bytes(ByteArray(0)), unpackedSize = Long.MAX_VALUE)

        assertFalse(pdf.isTooLargeToImport)
    }

    private fun archive(size: Long, unpackedSize: Long, unpackedImageSize: Long = 0) =
        ExportedFile(
            name = "songs.zip",
            mimeType = ExportedFile.ZIP_MIME_TYPE,
            content = ExportedContent.TemporaryFile(path = "/exports/songs.zip", size = size),
            unpackedSize = unpackedSize,
            unpackedImageSize = unpackedImageSize,
        )
}
