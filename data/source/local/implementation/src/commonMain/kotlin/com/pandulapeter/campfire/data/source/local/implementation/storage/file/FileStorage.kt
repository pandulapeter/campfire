/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import com.pandulapeter.campfire.data.model.domain.decodeLibraryText
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException

/** The folders the app keeps its data in. Each maps to one platform directory, created on first use. */
enum class StorageDirectory {
    SONGS,
    SETLISTS,
    PREFERENCES,
}

data class StoredFileInfo(
    /** File name with extension, without any path. */
    val name: String,
    val size: Long,
    /** Milliseconds since the epoch, 0 if the platform cannot tell. */
    val lastModified: Long,
)

/**
 * Flat file access inside the app-private data directory. No sub-directories, no paths: every operation is
 * (directory, file name). Names are validated by the callers, the storage itself only refuses names that contain a
 * path separator or are "." / "..", throwing [IllegalArgumentException].
 *
 * All functions are suspend and run off the main thread (on the web, which has a single thread, on
 * [kotlinx.coroutines.Dispatchers.Default]). A file or directory that is there and cannot be listed, read, written or
 * deleted throws `LibraryStorageException` on every platform (sync reports that as a storage failure rather than an
 * unknown one); a name that is a path throws [IllegalArgumentException] (see `requireValidFileName`). Callers map either
 * to `DataState.Failure`.
 *
 * Text is always UTF-8, and a byte order mark at the start of a file that has one is stripped while reading.
 */
interface FileStorage {

    suspend fun list(directory: StorageDirectory): List<StoredFileInfo>

    /** The unfiltered directory names, without opening each entry. */
    suspend fun listNames(directory: StorageDirectory): List<String>

    /**
     * What [list] would report about one file, or null if it does not exist. Saving a song needs the metadata of that
     * one file, and listing the whole directory for it made every save (and every file of an import) cost as much as
     * a rescan.
     */
    suspend fun info(directory: StorageDirectory, name: String): StoredFileInfo?

    suspend fun exists(directory: StorageDirectory, name: String): Boolean

    /**
     * Null if the file does not exist; one that exists and cannot be read throws [LibraryStorageException]. Decoded with
     * [decodeLibraryText], so a file that is not UTF-8 still reads.
     */
    suspend fun readText(directory: StorageDirectory, name: String): String?

    /** Null if the file does not exist; one that exists and cannot be read throws [LibraryStorageException]. */
    suspend fun readBytes(directory: StorageDirectory, name: String): ByteArray?

    /** Creates the file or overwrites it, encoded as UTF-8. Throws [LibraryStorageException] if it cannot. */
    suspend fun writeText(directory: StorageDirectory, name: String, text: String)

    /** Creates the file or overwrites it. Throws [LibraryStorageException] if it cannot. */
    suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray)

    /** Does nothing if the file does not exist. */
    suspend fun delete(directory: StorageDirectory, name: String)

    /**
     * Keeps a file that describes this installation rather than the user's work out of the system's backup and its
     * transfer to a new device. Called after every write, since a platform may carry the mark on the file itself and
     * an atomic write replaces the file. Only iOS acts on it: Android's backup rules are an allow-list in
     * `:app:android` that names what travels, and the desktop and the web have no backup of the app's own.
     */
    suspend fun keepOutOfDeviceBackup(directory: StorageDirectory, name: String) = Unit

    /**
     * Whether a file called [name] can exist in this storage at all. Only Windows answers no: `? : * " < > |` are
     * legal in a name on iOS, macOS, Linux, Android and OPFS, and a library assembled on any of those can hold one.
     * Asked rather than attempted because the attempt is an `InvalidPathException` from deep inside the JVM, which is
     * not an `IOException` and so is not a storage failure the caller could tell from any other.
     */
    fun canHoldFileName(name: String): Boolean = true
}

/** The path of a [StorageDirectory] relative to the platform's root, as segments each platform joins its own way. */
internal val StorageDirectory.pathSegments: List<String>
    get() = when (this) {
        StorageDirectory.SONGS -> listOf(LIBRARY_DIRECTORY, "songs")
        StorageDirectory.SETLISTS -> listOf(LIBRARY_DIRECTORY, "setlists")
        StorageDirectory.PREFERENCES -> listOf("preferences")
    }

internal fun requireValidFileName(name: String) = require(
    name.isNotEmpty() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\')
) { "Invalid file name: \"$name\"." }

/** Songs and setlists sit next to each other so that they can be exported as a single archive. */
private const val LIBRARY_DIRECTORY = "library"
