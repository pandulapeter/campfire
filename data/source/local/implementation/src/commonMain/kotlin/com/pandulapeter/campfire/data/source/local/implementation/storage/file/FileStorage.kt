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

import com.pandulapeter.campfire.data.model.library.decodeLibraryText
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** The folders the app keeps its data in. Each maps to one platform directory, created on first use. */
enum class StorageDirectory {
    SONGS,
    SETLISTS,

    /**
     * The pictures songs show and the files attached to them, named by their content (`LibraryFiles.isLibraryFileName`)
     * and kept while a song names one. Part of the library: exported and synced; the pictures are in the iOS backup, the
     * attachments are not, and none are in Android's, whose 25 MB quota photos would use up and cost the user the backup
     * of their songs.
     */
    FILES,
    PREFERENCES,

    /**
     * The pictures the editor's open draft names that no saved song holds yet: the library only ever holds what a saved
     * song names, so a picture picked in the editor waits here until Save moves it, and goes with the draft otherwise.
     * Out of every backup and sync, like the draft itself.
     */
    DRAFT_IMAGES,

    /**
     * The copies of the cover images the songs name. Outside the library, so that nothing that can be downloaded again
     * is exported or synced — the address travels in the song file — and kept out of every device backup.
     */
    COVERS,

    /**
     * The files exports are written into before they are saved or shared, each removed once handed over and the whole
     * folder emptied on launch. Outside the library and kept out of every device backup (a cache directory on iOS).
     */
    EXPORTS,

    /**
     * What an import receives before it is read: an archive copied in by streaming, and the files unpacked from it. Out
     * of every backup and sync; on Android under `filesDir` (never the cache, which the system may clear while the app
     * runs), on iOS under Application Support with the folder kept out of the backup (never Caches, which the system may
     * purge while a review waits), on the desktop and the web beside the exports.
     */
    IMPORTS,
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
     * never the batch's, as for [readTexts]. A size the listing knew is not proof the file is still there, so a file it
     * calls too large is asked about once more: one deleted since is missing.
     */
    suspend fun readScan(directory: StorageDirectory, entries: List<ScanEntry>, maxSize: Long): List<ScannedFile> = coroutineScope {
        entries.map { entry ->
            async {
                try {
                    val info = entry.info ?: info(directory, entry.name)
                    when {
                        info == null -> ScannedFile.Missing
                        info.size > maxSize && entry.info != null && !exists(directory, entry.name) -> ScannedFile.Missing
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

    /**
     * Creates the file, replacing one of that name, to be written front to back through the [FileSink] returned. Not
     * atomic, so only for [StorageDirectory.EXPORTS] and [StorageDirectory.IMPORTS], whose files nothing reads before they
     * are whole.
     */
    suspend fun openSink(directory: StorageDirectory, name: String): FileSink

    /**
     * The file opened for reads at any position (see [FileSource]), null if it does not exist; one that exists and cannot
     * be opened throws [LibraryStorageException]. The caller closes it.
     */
    suspend fun openSource(directory: StorageDirectory, name: String): FileSource?

    /**
     * Moves [fromName] of [from] to [toName] of [to], replacing a file there. A rename where the platform has one (every
     * directory is on the app's one volume), so atomic and without reading the file; the web, which has none, reads the
     * file and writes it whole with [writeBytes], one file at a time, then deletes the original. A file that is not there throws [LibraryStorageException].
     */
    suspend fun moveInto(from: StorageDirectory, fromName: String, to: StorageDirectory, toName: String)

    /**
     * Writes a copy of [fromName] of [from] as [toName] of [to], replacing a file there atomically, as [writeBytes]
     * does, without holding the file whole where the platform can copy it (on the web, which reads it whole, one file at
     * a time). A file that is not there throws [LibraryStorageException]. The copy carries the original's backup mark
     * on iOS, so the storage sets it after the copy as [isInDeviceBackup] says, as after a write, a move, and a take.
     */
    suspend fun copyInto(from: StorageDirectory, fromName: String, to: StorageDirectory, toName: String)

    /**
     * Puts [file], a platform's own handle of a file from outside the app's folders, at [name] in [directory] (only
     * [StorageDirectory.IMPORTS]), replacing a file there, without its bytes passing through Kotlin: the web copies a
     * `File` in JavaScript, a slice at a time, telling [onProgress] the bytes copied so far; iOS moves a file the app owns
     * by its path, a rename within the volume. A copy that fails leaves nothing at [name]. Throws
     * `UnsupportedOperationException` where the platform takes no such handle (Android and the desktop, which copy a
     * stream through `FileSink`s), and [LibraryStorageException] where the copy or the move fails.
     */
    suspend fun takeInto(directory: StorageDirectory, name: String, file: Any, onProgress: (copiedBytes: Long) -> Unit): Unit =
        throw UnsupportedOperationException("This storage does not take a platform's file handle.")

    /**
     * About how many bytes may still be written into [directory]: the volume's free space for the app, the origin's quota
     * left on the web. Only an early answer, imprecise on purpose in Safari; a write that runs out of space anyway fails
     * as any other write does. Null where the storage cannot tell at all (Chrome's estimate, see `OpfsFileStorage`).
     */
    suspend fun availableSpace(directory: StorageDirectory): Long?

    /**
     * Whether [availableSpace] is what is left of a quota the storage was granted (the web's origin quota) rather than
     * a volume's free space, which decides how much of it an import leaves free (`ImportLimits.minFreeSpaceFor`).
     */
    val isSpaceAQuota: Boolean get() = false

    /**
     * Where the file is as the platform names it, for handing it to the system (a share sheet, a save dialog): an
     * absolute path, on the web the path from the root of the origin private file system.
     */
    fun pathOf(directory: StorageDirectory, name: String): String

    /** Does nothing if the file does not exist. */
    suspend fun delete(directory: StorageDirectory, name: String)

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
        StorageDirectory.FILES -> listOf(LIBRARY_DIRECTORY, "files")
        StorageDirectory.DRAFT_IMAGES -> listOf("editor-draft-images")
        StorageDirectory.PREFERENCES -> listOf("preferences")
        StorageDirectory.COVERS -> listOf("covers")
        StorageDirectory.EXPORTS -> listOf("exports")
        StorageDirectory.IMPORTS -> listOf("imports")
    }

/** Whether [name] is a name rather than a path or nothing: what every storage can be asked about at all. */
internal fun isValidFileName(name: String) =
    name.isNotEmpty() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\')

internal fun requireValidFileName(name: String) = require(isValidFileName(name)) { "Invalid file name: \"$name\"." }

/** Songs, setlists, and their pictures sit next to each other so that they can be exported as a single archive. */
private const val LIBRARY_DIRECTORY = "library"
