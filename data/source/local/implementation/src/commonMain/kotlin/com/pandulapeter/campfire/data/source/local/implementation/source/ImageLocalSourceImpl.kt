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
import org.koin.core.annotation.Single

@Single
internal class ImageLocalSourceImpl(
    private val fileStorage: FileStorage,
) : ImageLocalSource {

    override suspend fun readImage(shelf: ImageShelf, name: String) = if (LibraryFiles.isLibraryImageName(name)) {
        fileStorage.readBytes(shelf.directory, name)
    } else {
        null
    }

    override suspend fun hasImage(shelf: ImageShelf, name: String) =
        LibraryFiles.isLibraryImageName(name) && fileStorage.exists(shelf.directory, name)

    /**
     * The draft's pictures are kept out of the device backup the way the draft's text is, after every write, since an
     * atomic write replaces the file and the mark with it. The library's go wherever the library goes.
     */
    override suspend fun writeImage(shelf: ImageShelf, name: String, bytes: ByteArray) {
        require(LibraryFiles.isLibraryImageName(name)) { "Not a picture's name: \"$name\"." }
        fileStorage.writeBytes(shelf.directory, name, bytes)
        if (shelf == ImageShelf.DRAFT) fileStorage.keepOutOfDeviceBackup(shelf.directory, name)
    }

    override suspend fun listImageNames(shelf: ImageShelf) = fileStorage.listNames(shelf.directory).filter(LibraryFiles::isLibraryImageName)

    override suspend fun deleteImage(shelf: ImageShelf, name: String) {
        if (LibraryFiles.isLibraryImageName(name)) fileStorage.delete(shelf.directory, name)
    }

    private val ImageShelf.directory
        get() = when (this) {
            ImageShelf.LIBRARY -> StorageDirectory.FILES
            ImageShelf.DRAFT -> StorageDirectory.DRAFT_IMAGES
        }
}
