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

import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.source.local.api.ImageLocalSource
import com.pandulapeter.campfire.data.source.local.api.ImageShelf
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.implementation.staging.ImportStaging
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.holdsBytes
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.holdsSameBytesAs
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.sha256Hex
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.use
import org.koin.core.annotation.Single

@Single
internal class ImageLocalSourceImpl(
    private val fileStorage: FileStorage,
    private val importStaging: ImportStaging,
) : ImageLocalSource {

    override suspend fun readImage(shelf: ImageShelf, name: String) = if (LibraryFiles.isLibraryFileName(name)) {
        fileStorage.readBytes(shelf.directory, name)
    } else {
        null
    }

    override suspend fun hasImage(shelf: ImageShelf, name: String) =
        LibraryFiles.isLibraryFileName(name) && fileStorage.exists(shelf.directory, name)

    override suspend fun imageSize(shelf: ImageShelf, name: String) = if (LibraryFiles.isLibraryFileName(name)) fileStorage.info(shelf.directory, name)?.size else null

    override suspend fun hashImage(shelf: ImageShelf, name: String) = if (LibraryFiles.isLibraryFileName(name)) {
        fileStorage.openSource(shelf.directory, name)?.use { it.sha256Hex() }
    } else {
        null
    }

    /**
     * The backup mark is set or cleared after the copy as after a move, since the copy carries the original's mark on iOS:
     * a draft picture copied into the library by Save would otherwise stay out of the backup.
     */
    override suspend fun copyImage(from: ImageShelf, name: String, to: ImageShelf, toName: String) {
        require(LibraryFiles.isLibraryFileName(name) && LibraryFiles.isLibraryFileName(toName)) { "Not a library file's name: \"$name\" or \"$toName\"." }
        fileStorage.copyInto(from = from.directory, fromName = name, to = to.directory, toName = toName)
        if (to == ImageShelf.LIBRARY && !isLibraryFileKeptOutOfDeviceBackup(toName)) {
            fileStorage.keepInDeviceBackup(to.directory, toName)
        } else {
            keepOutOfDeviceBackupIfDue(to, toName)
        }
    }

    /**
     * The draft's files are kept out of the device backup the way the draft's text is, after every write, since an
     * atomic write replaces the file and the mark with it; the library's as [isLibraryFileKeptOutOfDeviceBackup] says.
     */
    override suspend fun writeImage(shelf: ImageShelf, name: String, bytes: ByteArray) {
        require(LibraryFiles.isLibraryFileName(name)) { "Not a library file's name: \"$name\"." }
        fileStorage.writeBytes(shelf.directory, name, bytes)
        keepOutOfDeviceBackupIfDue(shelf, name)
    }

    private suspend fun keepOutOfDeviceBackupIfDue(shelf: ImageShelf, name: String) {
        if (shelf == ImageShelf.DRAFT || isLibraryFileKeptOutOfDeviceBackup(name)) fileStorage.keepOutOfDeviceBackup(shelf.directory, name)
    }

    override suspend fun holdsContent(shelf: ImageShelf, name: String, content: IncomingContent): Boolean {
        if (!LibraryFiles.isLibraryFileName(name) || fileStorage.info(shelf.directory, name)?.size != content.size) return false
        val held = fileStorage.openSource(shelf.directory, name) ?: return false
        return held.use {
            when (content) {
                is IncomingContent.Bytes -> held.holdsBytes(content.bytes)
                is IncomingContent.Staged -> (importStaging.openSource(content.file) ?: throw LibraryStorageException("The staged file \"${content.file.name}\" is gone."))
                    .use { staged -> held.holdsSameBytesAs(staged) }
            }
        }
    }

    override suspend fun readStagedHead(staged: StagedFile, length: Int): ByteArray? = importStaging.openSource(staged)?.use { source ->
        val head = ByteArray(minOf(length.toLong(), source.size).toInt())
        var read = 0
        while (read < head.size) {
            val count = source.read(read.toLong(), head, read, head.size - read)
            if (count <= 0) throw LibraryStorageException("The staged file \"${staged.name}\" ended before its size.")
            read += count
        }
        head
    }

    /**
     * The backup mark is set or cleared whether the file was moved, copied, or already there: a rename keeps the mark the
     * staged file carried, and a picture belongs in the backup however it reached the library.
     */
    override suspend fun moveStagedFile(staged: StagedFile, shelf: ImageShelf, name: String) {
        require(LibraryFiles.isLibraryFileName(name)) { "Not a library file's name: \"$name\"." }
        importStaging.moveOut(staged, shelf.directory, name)
        if (shelf == ImageShelf.LIBRARY && !isLibraryFileKeptOutOfDeviceBackup(name)) {
            fileStorage.keepInDeviceBackup(shelf.directory, name)
        } else {
            keepOutOfDeviceBackupIfDue(shelf, name)
        }
    }

    override suspend fun listImageNames(shelf: ImageShelf) = fileStorage.listNames(shelf.directory).filter(LibraryFiles::isLibraryFileName)

    override suspend fun listImageSizes(shelf: ImageShelf) = fileStorage.list(shelf.directory)
        .filter { LibraryFiles.isLibraryFileName(it.name) }
        .associate { it.name to it.size }

    override suspend fun deleteImage(shelf: ImageShelf, name: String) {
        if (LibraryFiles.isLibraryFileName(name)) fileStorage.delete(shelf.directory, name)
    }

    private val ImageShelf.directory
        get() = when (this) {
            ImageShelf.LIBRARY -> StorageDirectory.FILES
            ImageShelf.DRAFT -> StorageDirectory.DRAFT_IMAGES
        }
}
