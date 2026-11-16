/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.api

/**
 * The pictures songs show, by name, on one of two shelves: the library's (`library/files`), which syncs, exports, and
 * holds only what a saved song names, and the editor draft's, where a picture picked for unsaved text waits for Save.
 *
 * Only names `LibraryFiles.isLibraryImageName` accepts are listed or deleted: anything else in either folder (a storage's
 * temporary file, something put there by hand) is not this source's. A read answers null only for a picture that is not
 * there; one that is there and cannot be read, and a write or deletion that fails, throw `LibraryStorageException`.
 */
interface ImageLocalSource {

    suspend fun readImage(shelf: ImageShelf, name: String): ByteArray?

    suspend fun hasImage(shelf: ImageShelf, name: String): Boolean

    /** Creates the picture or overwrites it, which only ever writes the same bytes again: a picture's name is its content. */
    suspend fun writeImage(shelf: ImageShelf, name: String, bytes: ByteArray)

    suspend fun listImageNames(shelf: ImageShelf): List<String>

    /** Does nothing where the picture is not there. */
    suspend fun deleteImage(shelf: ImageShelf, name: String)
}

enum class ImageShelf {
    LIBRARY,
    DRAFT,
}
