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

import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.source.local.api.ImageLocalSource
import com.pandulapeter.campfire.data.source.local.api.ImageShelf
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.sha256Hex
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.use
import org.koin.core.annotation.Single

@Single
internal class ImageLocalSourceImpl(
    private val fileStorage: FileStorage,
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

    /** A copy is a new file, which carries no backup mark of the original's: a draft picture reaches the backup this way. */
    override suspend fun copyImage(from: ImageShelf, name: String, to: ImageShelf, toName: String) {
        require(LibraryFiles.isLibraryFileName(name) && LibraryFiles.isLibraryFileName(toName)) { "Not a library file's name: \"$name\" or \"$toName\"." }
        fileStorage.copyInto(from = from.directory, fromName = name, to = to.directory, toName = toName)
        keepOutOfDeviceBackupIfDue(to, toName)
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
