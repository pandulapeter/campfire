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
    fun `an archive is too large to import by what it unpacks to - however small it is compressed`() {
        assertTrue(archive(size = 1_000L, unpackedSize = ImportLimits.MAX_IMPORT_SIZE + 1).isTooLargeToImport)
        assertFalse(archive(size = 1_000L, unpackedSize = ImportLimits.MAX_IMPORT_SIZE).isTooLargeToImport)
    }

    @Test
    fun `an archive is too large to import only past what the import receives`() {
        val songs = ImportLimits.MAX_IMPORT_SIZE

        assertTrue(archive(size = ImportLimits.MAX_STAGED_ARCHIVE_SIZE + 1, unpackedSize = songs, unpackedImageSize = songs).isTooLargeToImport)
        assertFalse(archive(size = ImportLimits.MAX_STAGED_ARCHIVE_SIZE, unpackedSize = songs, unpackedImageSize = songs).isTooLargeToImport)
    }

    @Test
    fun `an archive past 4 GB or 65534 entries imports back as ZIP64`() {
        val pictures = 5L shl 30

        assertFalse(archive(size = pictures, unpackedSize = pictures + 1_000, unpackedImageSize = pictures, entryCount = 70_000).isTooLargeToImport)
    }

    @Test
    fun `an archive of more entries than the import reads is too large to import`() {
        assertTrue(archive(size = 1_000L, unpackedSize = 1_000L, entryCount = ImportLimits.MAX_ARCHIVE_ENTRIES + 1).isTooLargeToImport)
        assertFalse(archive(size = 1_000L, unpackedSize = 1_000L, entryCount = ImportLimits.MAX_ARCHIVE_ENTRIES).isTooLargeToImport)
    }

    @Test
    fun `pictures and attachments are judged by what the import stages never by the songs' budget`() {
        val songs = ImportLimits.MAX_IMPORT_SIZE
        val staged = ImportLimits.MAX_IMPORT_STAGED_SIZE
        val pictures = staged / 2

        assertFalse(archive(size = 1_000L, unpackedSize = songs + staged, unpackedImageSize = pictures, unpackedAttachmentSize = staged - pictures).isTooLargeToImport)
        assertTrue(archive(size = 1_000L, unpackedSize = songs + staged + 1, unpackedImageSize = pictures, unpackedAttachmentSize = staged - pictures + 1).isTooLargeToImport)
        assertTrue(archive(size = 1_000L, unpackedSize = songs + staged + 1, unpackedImageSize = pictures, unpackedAttachmentSize = staged - pictures).isTooLargeToImport)
    }

    @Test
    fun `a shared copy may be no larger than every version opening a link downloads - pictures included`() {
        val limit = ImportLimits.MAX_SHARED_COPY_SIZE

        assertFalse(archive(size = 1_000L, unpackedSize = limit, unpackedImageSize = limit - 1_000).isTooLargeToShare)
        assertTrue(archive(size = 1_000L, unpackedSize = limit + 1, unpackedImageSize = limit).isTooLargeToShare)
        assertTrue(archive(size = limit + 1, unpackedSize = limit + 1).isTooLargeToShare)
    }

    @Test
    fun `a shared copy's attachments have a budget of their own beside its songs and pictures`() {
        val limit = ImportLimits.MAX_SHARED_COPY_SIZE
        val attachments = ImportLimits.MAX_SHARED_ATTACHMENTS_SIZE

        assertFalse(archive(size = limit + attachments, unpackedSize = limit + attachments, unpackedAttachmentSize = attachments).isTooLargeToShare)
        assertTrue(archive(size = 1_000L, unpackedSize = limit + attachments + 1, unpackedAttachmentSize = attachments + 1).isTooLargeToShare)
        assertTrue(archive(size = limit + attachments + 1, unpackedSize = 1_000L).isTooLargeToShare)
    }

    @Test
    fun `a file that is no archive is never too large to import`() {
        val pdf = ExportedFile(name = "songs.pdf", mimeType = ExportedFile.PDF_MIME_TYPE, content = ExportedContent.Bytes(ByteArray(0)), unpackedSize = Long.MAX_VALUE)

        assertFalse(pdf.isTooLargeToImport)
    }

    private fun archive(size: Long, unpackedSize: Long, unpackedImageSize: Long = 0, unpackedAttachmentSize: Long = 0, entryCount: Int = 1) =
        ExportedFile(
            name = "songs.zip",
            mimeType = ExportedFile.ZIP_MIME_TYPE,
            content = ExportedContent.TemporaryFile(path = "/exports/songs.zip", size = size, storedName = "songs.zip"),
            unpackedSize = unpackedSize,
            unpackedImageSize = unpackedImageSize,
            unpackedAttachmentSize = unpackedAttachmentSize,
            entryCount = entryCount,
        )
}
