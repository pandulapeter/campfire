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

    /** Every song and setlist file in the library. Files with an unknown extension are left out. */
    suspend fun loadLibraryFiles(): List<LibraryFile>

    /** Null if the file does not exist. */
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
}
