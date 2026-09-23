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

import com.pandulapeter.campfire.data.model.domain.LibraryFile
import com.pandulapeter.campfire.data.model.domain.LibraryFileKind

/**
 * The library as bytes, which is what sync moves around. The other local sources hand out parsed models; this one
 * deliberately does not look inside the files at all, so that a song Campfire cannot parse still travels between
 * devices unchanged.
 *
 * Everything a caller can name is a (kind, file name) pair, the same flat shape as `FileStorage`.
 */
interface LibraryFileLocalSource {

    /**
     * Every song and setlist file in the library, by the rule the library scan uses (`LibraryFiles.isSongFileName`).
     * Whatever else the user keeps in the folder - files with another extension, the hidden ones a file manager
     * writes for itself - is left where it is rather than uploaded to their cloud storage.
     */
    suspend fun loadLibraryFiles(): List<LibraryFile>

    /**
     * Null if the file does not exist, and only then: a file that is there but cannot be read throws
     * [LibraryStorageException]. Sync is why the two must never be confused - a file reported as missing is planned
     * as a deletion, and that deletion reaches every other device.
     */
    suspend fun readLibraryFile(kind: LibraryFileKind, name: String): ByteArray?

    /** Creates the file or overwrites it. */
    suspend fun writeLibraryFile(kind: LibraryFileKind, name: String, bytes: ByteArray)

    /**
     * Writes [bytes] under the first free variant of [desiredName] (" (2)", " (3)"…) and returns the name it got.
     * This is what an incoming copy of a file that changed on both sides lands under: nothing is ever overwritten
     * implicitly, here as everywhere else in the library.
     */
    suspend fun writeLibraryFileToFreeName(kind: LibraryFileKind, desiredName: String, bytes: ByteArray): String

    suspend fun deleteLibraryFile(kind: LibraryFileKind, name: String)

    /**
     * Whether this device's file system can hold a library file called [name] at all. Every platform answers no for a
     * name that is a path or nothing (a `/` or `\` in it, `.`, `..`), and Windows also for a name another platform
     * allows (`? : * " < > |` among others); sync leaves such a file where it is rather than failing on it every run.
     */
    fun canHoldFileName(kind: LibraryFileKind, name: String): Boolean
}

/**
 * A library file that is there but could not be read or written: data protection before the first unlock, a file
 * being replaced by another app in the middle of a read, a permission that was taken away. Thrown instead of being
 * passed off as a missing file, which a caller would act on - see [LibraryFileLocalSource.readLibraryFile].
 */
class LibraryStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
