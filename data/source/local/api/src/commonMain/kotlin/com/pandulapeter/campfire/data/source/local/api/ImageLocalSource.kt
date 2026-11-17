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

import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.data.model.importing.StagedFile

/**
 * The files songs keep, the pictures they show and the files attached to them, by name, on one of two shelves: the
 * library's (`library/files`), which syncs, exports, and holds only what a saved song names, and the editor draft's,
 * where a file picked for unsaved text waits for Save. Named for pictures, which it held first.
 *
 * Only names `LibraryFiles.isLibraryFileName` accepts are listed or deleted: anything else in either folder (a storage's
 * temporary file, something put there by hand) is not this source's. A read answers null only for a picture that is not
 * there; one that is there and cannot be read, and a write or deletion that fails, throw `LibraryStorageException`.
 */
interface ImageLocalSource {

    suspend fun readImage(shelf: ImageShelf, name: String): ByteArray?

    suspend fun hasImage(shelf: ImageShelf, name: String): Boolean

    /** The size of the file on disk without reading it, null where it is not there. */
    suspend fun imageSize(shelf: ImageShelf, name: String): Long?

    /** The file's SHA-256 as lowercase hex, read a slice at a time and never held whole; null where it is not there. */
    suspend fun hashImage(shelf: ImageShelf, name: String): String?

    /**
     * Writes a copy of [name] of the shelf [from] as [toName] of [to], replacing a file there atomically and never holding
     * it whole (but on the web, one file at a time); the original stays. Kept out of the device backup as [writeImage]
     * keeps a file it writes. A file that is not there throws `LibraryStorageException`.
     */
    suspend fun copyImage(from: ImageShelf, name: String, to: ImageShelf, toName: String)

    /** Creates the picture or overwrites it, which only ever writes the same bytes again: a picture's name is its content. */
    suspend fun writeImage(shelf: ImageShelf, name: String, bytes: ByteArray)

    /**
     * Whether the shelf's [name] holds exactly [content]: false where it is not there or of another size, else the two
     * compared a slice at a time, neither ever read whole. A staged [content] that is gone throws `LibraryStorageException`.
     */
    suspend fun holdsContent(shelf: ImageShelf, name: String, content: IncomingContent): Boolean

    /**
     * The first [length] bytes of a staged file (all of it where it is shorter), where it is now (see [moveStagedFile]);
     * null where it is gone or no longer of its size.
     */
    suspend fun readStagedHead(staged: StagedFile, length: Int): ByteArray?

    /**
     * Puts a file the import staged into the shelf as [name], its content name. A rename where the platform has one
     * (atomic, the file never read), on the web the file written whole with the atomic write of [writeImage], then the
     * staged copy deleted; never written in place. A name the shelf already holds, which the caller found to hold the
     * same bytes, is left as it is and the staged copy deleted. A staged file one call put on a shelf is copied from there
     * by the next, which a file a plan brings under two names needs. Kept out of the device backup as [writeImage] keeps
     * a file it writes, and a library picture put back into it, since the staging folder is out of it. A staged file that
     * is gone throws `LibraryStorageException`, having written nothing.
     */
    suspend fun moveStagedFile(staged: StagedFile, shelf: ImageShelf, name: String)

    suspend fun listImageNames(shelf: ImageShelf): List<String>

    /** The size on disk of every file [listImageNames] lists, by name, read from the listing without reading a file. */
    suspend fun listImageSizes(shelf: ImageShelf): Map<String, Long>

    /** Does nothing where the picture is not there. */
    suspend fun deleteImage(shelf: ImageShelf, name: String)
}

enum class ImageShelf {
    LIBRARY,
    DRAFT,
}
