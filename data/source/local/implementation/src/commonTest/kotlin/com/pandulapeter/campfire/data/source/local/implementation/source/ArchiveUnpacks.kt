/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.hashing.Sha256
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedImage
import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.model.importing.UnpackedArchive
import com.pandulapeter.campfire.data.source.local.implementation.InMemoryFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.staging.ImportStaging
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Both unpacks of an archive, the in-memory one and the streaming one from the staging folder over [storage], and the
 * check that they agree: the streaming one's staged files hold exactly the bytes the in-memory one read (each named by
 * its SHA-256, of its size), and everything else is equal, while the staging folder holds nothing of the unpack but
 * the archive and the files its result names.
 */
internal class ArchiveUnpacks(val storage: FileStorage = InMemoryFileStorage()) {

    val staging = ImportStaging(storage, Logger.Standard)
    val archiveLocalSource = ArchiveLocalSourceImpl(logger = Logger.Standard, fileStorage = storage, importStaging = staging)

    /** [archive] unpacked both ways with the whole picture and attachment budgets, which must agree; the in-memory one. */
    suspend fun both(archive: ByteArray, maxSize: Long = ImportLimits.MAX_IMPORT_SIZE): UnpackedArchive {
        val inMemory = inMemory(archive, maxSize)
        assertEquals(inMemory, streamed(archive, maxSize))
        return inMemory
    }

    suspend fun inMemory(
        archive: ByteArray,
        maxSize: Long = ImportLimits.MAX_IMPORT_SIZE,
        maxImageSize: Long = ImportLimits.MAX_IMPORT_IMAGES_SIZE,
        maxAttachmentSize: Long = ImportLimits.MAX_IMPORT_ATTACHMENTS_SIZE,
    ) = archiveLocalSource.unpack(archive = archive, maxSize = maxSize, maxImageSize = maxImageSize, maxAttachmentSize = maxAttachmentSize)

    /** [archive] received into a request of its own and unpacked from there, its staged files read back as bytes. */
    suspend fun streamed(archive: ByteArray, maxSize: Long = ImportLimits.MAX_IMPORT_SIZE): UnpackedArchive {
        val staged = stage(archive)
        val request = staging.requestOf(staged)
        val unpacked = archiveLocalSource.unpack(archive = staged, maxSize = maxSize)
        val named = unpacked.files.flatMap { it.images }.mapNotNull { (it.content as? IncomingContent.Staged)?.file?.name }
        assertEquals((named + staged.name).toSet(), stagedNames().toSet())
        assertTrue(unpacked.files.none { it.staged != null })
        return unpacked.copy(files = unpacked.files.map { file -> file.copy(images = file.images.map { it.withStagedRead() }) })
            .also { staging.deleteRequest(request) }
    }

    /** [archive] as a new request receives it. */
    suspend fun stage(archive: ByteArray): StagedFile {
        val sink = staging.openArchive(staging.newRequest(), announcedSize = archive.size.toLong())
        sink.write(archive, 0, archive.size)
        return sink.finish()
    }

    suspend fun stagedNames() = storage.listNames(StorageDirectory.IMPORTS)

    private suspend fun ImportedImage.withStagedRead(): ImportedImage {
        val file = (content as? IncomingContent.Staged)?.file ?: return this
        val bytes = assertNotNull(storage.readBytes(StorageDirectory.IMPORTS, file.name))
        assertEquals(Sha256.hashToHex(bytes), file.sha256Hex)
        assertEquals(bytes.size.toLong(), file.size)
        return ImportedImage(source = source, content = IncomingContent.Bytes(bytes), problem = problem, isAttachment = isAttachment)
    }
}
