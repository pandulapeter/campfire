package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import org.koin.core.scope.Scope

/** The folders the app keeps its data in. Each maps to one platform directory, created on first use. */
enum class StorageDirectory {
    SONGS,
    SETLISTS,
    PREFERENCES
}

data class StoredFileInfo(
    /** File name with extension, without any path. */
    val name: String,
    val size: Long,
    /** Milliseconds since the epoch, 0 if the platform cannot tell. */
    val lastModified: Long
)

/**
 * Flat file access inside the app-private data directory. No sub-directories, no paths: every operation is
 * (directory, file name). Names are validated by the callers, the storage itself only refuses names that contain a
 * path separator or are "." / "..", throwing [IllegalArgumentException].
 *
 * All functions are suspend and run off the main thread (on the web, which has a single thread, on
 * [kotlinx.coroutines.Dispatchers.Default]); errors surface as exceptions of the platform, callers map them to
 * `DataState.Failure`.
 *
 * Text is always UTF-8, and a byte order mark at the start of a file that has one is stripped while reading.
 */
interface FileStorage {

    suspend fun list(directory: StorageDirectory): List<StoredFileInfo>

    suspend fun exists(directory: StorageDirectory, name: String): Boolean

    /** Null if the file does not exist. */
    suspend fun readText(directory: StorageDirectory, name: String): String?

    /** Null if the file does not exist. */
    suspend fun readBytes(directory: StorageDirectory, name: String): ByteArray?

    /** Creates the file or overwrites it, encoded as UTF-8. */
    suspend fun writeText(directory: StorageDirectory, name: String, text: String)

    /** Creates the file or overwrites it. */
    suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray)

    /** Does nothing if the file does not exist. */
    suspend fun delete(directory: StorageDirectory, name: String)
}

internal expect fun Scope.createFileStorage(): FileStorage

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

/** Editors on Windows like to prefix UTF-8 files with a byte order mark, which is not part of the content. */
internal fun String.withoutByteOrderMark() = removePrefix("\uFEFF")

/** Songs and setlists sit next to each other so that they can be exported as a single archive. */
private const val LIBRARY_DIRECTORY = "library"
