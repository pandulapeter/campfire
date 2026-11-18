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
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.source.local.implementation.InMemoryFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.staging.ImportStaging
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The unpack of an archive staged into a request of its own over [storage], with its staged pictures and attachments
 * read back as bytes, checked to hold exactly what their names say (each named by its SHA-256, of its size), and the
 * staging folder checked to hold nothing of the unpack but the archive and the files its result names.
 */
internal class ArchiveUnpacks(val storage: FileStorage = InMemoryFileStorage()) {

    val staging = ImportStaging(storage, Logger.Standard)
    val archiveLocalSource = ArchiveLocalSourceImpl(logger = Logger.Standard, fileStorage = storage, importStaging = staging)

    /** [archive] received into a request of its own and unpacked from there, its staged files read back as bytes. */
    suspend fun unpack(archive: ByteArray, maxSize: Long = ImportLimits.MAX_IMPORT_SIZE): List<ImportedFile> {
        val staged = stage(archive)
        val request = staging.requestOf(staged)
        val unpacked = archiveLocalSource.unpack(archive = staged, maxSize = maxSize)
        val named = unpacked.flatMap { it.images }.mapNotNull { (it.content as? IncomingContent.Staged)?.file?.name }
        assertEquals((named + staged.name).toSet(), stagedNames().toSet())
        assertTrue(unpacked.none { it.staged != null })
        return unpacked.map { file -> file.copy(images = file.images.map { it.withStagedRead() }) }
            .also { staging.deleteRequest(request) }
    }

    /** [archive] as a new request receives it. */
    suspend fun stage(archive: ByteArray): StagedFile {
        archiveRecorder?.invoke(archive)
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

/**
 * Handed every archive an [ArchiveUnpacks] stages while it is set, so that `ArchiveImportTest` can run every archive
 * the unpack tests build through the whole import without a second list of them to keep in step.
 */
internal var archiveRecorder: ((ByteArray) -> Unit)? = null
