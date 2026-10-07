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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** The folders the app keeps its data in. Each maps to one platform directory, created on first use. */
enum class StorageDirectory {
    SONGS,
    SETLISTS,
    PREFERENCES,

    /**
     * The copies of the cover images the songs name. Outside the library, so that nothing that can be downloaded again
     * is exported or synced — the address travels in the song file — and kept out of every device backup.
     */
    COVERS,
}

data class StoredFileInfo(
    /** File name with extension, without any path. */
    val name: String,
    val size: Long,
    /** Milliseconds since the epoch, 0 if the platform cannot tell. */
    val lastModified: Long,
)

/** What [FileStorage.readTexts] answers about one file. */
sealed interface BatchRead {

    data class Text(val text: String) : BatchRead

    /** The file is not there, which is what [FileStorage.readText]'s null says. */
    data object Missing : BatchRead

    /** The file is there and could not be read; the exception is what [FileStorage.readText] would have thrown. */
    class Failed(val cause: Exception) : BatchRead
}

/** A file a library scan is going to read, and what the listing already knew about it, where it asked. */
data class ScanEntry(val name: String, val info: StoredFileInfo?)

/** What [FileStorage.readScan] answers about one file. */
sealed interface ScannedFile {

    data class Text(val info: StoredFileInfo, val text: String) : ScannedFile

    /** There, and larger than the scan reads, so never read. */
    data class TooLarge(val info: StoredFileInfo) : ScannedFile

    /** The file is not there, or is not a file: what leaving it out of [FileStorage.list] says. */
    data object Missing : ScannedFile

    /** The file is there and could not be read; the exception is what [FileStorage.readText] would have thrown. */
    class Failed(val cause: Exception) : ScannedFile
}

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

    /**
     * [readText] for every one of [names] at once, one answer per name and in the same order. A file that fails is its
     * own answer, never the batch's, so that one unreadable song does not take the rest of a scan with it. The web
     * overrides it to read the whole batch in one call into the browser, where each file read one at a time costs
     * several round trips to its storage; everywhere else the reads simply run in parallel.
     */
    suspend fun readTexts(directory: StorageDirectory, names: List<String>): List<BatchRead> = coroutineScope {
        names.map { name ->
            async {
                try {
                    readText(directory, name)?.let(BatchRead::Text) ?: BatchRead.Missing
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    BatchRead.Failed(exception)
                }
            }
        }.awaitAll()
    }

    /**
     * The files a library scan reads, in [list]'s order and with its filtering. Where the platform can say more about a
     * file without asking each one, the entries carry it; otherwise [readScan] asks each file as it reads it, so that
     * the first batch of a scan does not wait for every file of the directory to be asked about first.
     */
    suspend fun listForScan(directory: StorageDirectory): List<ScanEntry> = list(directory).map { ScanEntry(name = it.name, info = it) }

    /**
     * Every one of [entries] read at once, one answer each and in the same order, with its size and date. A file larger
     * than [maxSize] is reported as [ScannedFile.TooLarge] without being read, and a file that fails is its own answer,
     * never the batch's, as for [readTexts].
     */
    suspend fun readScan(directory: StorageDirectory, entries: List<ScanEntry>, maxSize: Long): List<ScannedFile> = coroutineScope {
        entries.map { entry ->
            async {
                try {
                    val info = entry.info ?: info(directory, entry.name)
                    when {
                        info == null -> ScannedFile.Missing
                        info.size > maxSize -> ScannedFile.TooLarge(info)
                        else -> readText(directory, entry.name)?.let { ScannedFile.Text(info = info, text = it) } ?: ScannedFile.Missing
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    ScannedFile.Failed(exception)
                }
            }
        }.awaitAll()
    }

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
     * Whether a file called [name] can exist in this storage at all. No storage takes a path or nothing for a name
     * ([isValidFileName]: a `/` or `\` anywhere in it, `.`, `..`), and Windows also refuses `? : * " < > |`, which are
     * legal on iOS, macOS, Linux, Android and OPFS. Asked rather than attempted because the attempt is an
     * `IllegalArgumentException` from [requireValidFileName] or an `InvalidPathException` from deep inside the JVM,
     * neither of which is a storage failure the caller could tell from any other.
     */
    fun canHoldFileName(name: String): Boolean = isValidFileName(name)
}

/** The path of a [StorageDirectory] relative to the platform's root, as segments each platform joins its own way. */
internal val StorageDirectory.pathSegments: List<String>
    get() = when (this) {
        StorageDirectory.SONGS -> listOf(LIBRARY_DIRECTORY, "songs")
        StorageDirectory.SETLISTS -> listOf(LIBRARY_DIRECTORY, "setlists")
        StorageDirectory.PREFERENCES -> listOf("preferences")
        StorageDirectory.COVERS -> listOf("covers")
    }

/** Whether [name] is a name rather than a path or nothing: what every storage can be asked about at all. */
internal fun isValidFileName(name: String) =
    name.isNotEmpty() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\')

internal fun requireValidFileName(name: String) = require(isValidFileName(name)) { "Invalid file name: \"$name\"." }

/** Songs and setlists sit next to each other so that they can be exported as a single archive. */
private const val LIBRARY_DIRECTORY = "library"
