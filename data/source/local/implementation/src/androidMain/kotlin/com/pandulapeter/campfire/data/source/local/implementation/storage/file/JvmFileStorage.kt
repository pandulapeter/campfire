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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap

/**
 * The [FileStorage] of every JVM based platform, on top of [java.io.File]. The root is chosen by the `actual` factory
 * of each platform, which also makes it possible to point a test at a temporary directory.
 *
 * Writes go to a temporary file first and are moved into place afterwards, so that a crash in the middle of one leaves
 * the previous content intact instead of a half written file.
 *
 * The desktop copy of this class is identical: the `campfire-library` convention plugin declares no source set shared
 * by `androidMain` and `desktopMain`, so a JVM class both need is kept twice, and a change to one is made to the other.
 */
internal class JvmFileStorage(
    private val root: File,
    private val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("windows", ignoreCase = true),
) : FileStorage {

    /**
     * Each directory resolved once. It is only created by a write ([writeAtomically]): a read or a listing of one that
     * is not there answers what it answers for a file that is not there, and a folder deleted under the running app is
     * recreated by the next save, as it would be by the first one.
     */
    private val directoryFiles = ConcurrentHashMap<StorageDirectory, File>()
    private val sweptDirectories = ConcurrentHashMap.newKeySet<StorageDirectory>()

    /**
     * One attribute read per entry, which answers whether it is a file, its size and its date at once, where asking
     * [File] for the three is three calls into the file system each.
     */
    override suspend fun list(directory: StorageDirectory) = withContext(Dispatchers.IO) {
        listPaths(directory)
            .mapNotNull { path ->
                // Gone since the listing, or not something the attributes can be read of: left out, as File.isFile
                // leaves it out.
                val attributes = try {
                    Files.readAttributes(path, BasicFileAttributes::class.java)
                } catch (_: IOException) {
                    null
                }
                attributes?.takeIf { it.isRegularFile }?.let {
                    StoredFileInfo(
                        name = path.fileName.toString().toLibraryName(),
                        size = it.size(),
                        lastModified = it.lastModifiedTime().toMillis(),
                    )
                }
            }
            .sortedBy { it.name }
    }

    /** Names only: the attributes are read with each file, by [readScan], rather than all of them before the first read. */
    override suspend fun listForScan(directory: StorageDirectory) = withContext(Dispatchers.IO) {
        listPaths(directory)
            .map { path -> ScanEntry(name = path.fileName.toString().toLibraryName(), info = null) }
            .sortedBy { it.name }
    }

    /**
     * One attribute read and one read of the content per file, in parallel; the caller's batch is what bounds how many
     * are open at once. Something under the name that is not a regular file is missing, as [list] leaves it out.
     */
    override suspend fun readScan(directory: StorageDirectory, entries: List<ScanEntry>, maxSize: Long) = withContext(Dispatchers.IO) {
        coroutineScope {
            entries.map { entry ->
                async {
                    try {
                        scan(directory, entry, maxSize)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        ScannedFile.Failed(exception)
                    }
                }
            }.awaitAll()
        }
    }

    private fun scan(directory: StorageDirectory, entry: ScanEntry, maxSize: Long): ScannedFile {
        val file = file(directory, entry.name)
        val attributes = try {
            Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
        } catch (_: NoSuchFileException) {
            return ScannedFile.Missing
        } catch (exception: IOException) {
            throw LibraryStorageException("Could not access \"${entry.name}\".", exception)
        }
        if (!attributes.isRegularFile) return ScannedFile.Missing
        val info = StoredFileInfo(name = entry.name, size = attributes.size(), lastModified = attributes.lastModifiedTime().toMillis())
        if (info.size > maxSize) return ScannedFile.TooLarge(info)
        return file.readIfFile(entry.name)?.decodeLibraryText()?.let { ScannedFile.Text(info = info, text = it) } ?: ScannedFile.Missing
    }

    /**
     * Every entry of the directory but the temporary files of writes in progress. A directory that is not there yet is
     * the same as an empty one. One that is there but cannot be listed is a failure and has to be reported as one:
     * passing it off as an empty library would invite the user to create songs in a folder the app cannot read.
     */
    private fun listPaths(directory: StorageDirectory): List<Path> {
        val directoryFile = directoryFile(directory)
        val paths = try {
            Files.newDirectoryStream(directoryFile.toPath()).use { it.toList() }
        } catch (_: NoSuchFileException) {
            emptyList()
        } catch (exception: IOException) {
            throw LibraryStorageException("Could not read \"${directoryFile.absolutePath}\".", exception)
        }
        return paths.filterNot { it.fileName.toString().endsWith(TEMPORARY_FILE_SUFFIX) }
    }

    override suspend fun listNames(directory: StorageDirectory) = withContext(Dispatchers.IO) {
        val directoryFile = directoryFile(directory)
        directoryFile.list()?.toList() ?: if (directoryFile.isDirectory) throw LibraryStorageException("Could not read \"${directoryFile.absolutePath}\".") else emptyList()
    }

    override suspend fun info(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).let { if (it.isFile) StoredFileInfo(name = name, size = it.length(), lastModified = it.lastModified()) else null }
    }

    override suspend fun exists(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).isFile
    }

    override suspend fun readText(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).readIfFile(name)?.decodeLibraryText()
    }

    override suspend fun readBytes(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).readIfFile(name)
    }

    override suspend fun writeText(directory: StorageDirectory, name: String, text: String) = withContext(Dispatchers.IO) {
        failingAsStorage(name) { writeAtomically(directory, name) { it.writeText(text) } }
    }

    override suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        failingAsStorage(name) { writeAtomically(directory, name) { it.writeBytes(bytes) } }
    }

    override suspend fun delete(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        val file = file(directory, name)
        // Not File.delete(), whose false says nothing about why: an AccessDeniedException is a file somebody else is
        // holding open and worth retrying, and anything else belongs in the message.
        failingAsStorage(name) { retryingWhileDenied { Files.deleteIfExists(file.toPath()) } }
        Unit
    }

    /**
     * Device names are not refused here, since they are mapped (see [toStoredName]) and so can be held. These cannot:
     * no escape character exists that could not also be part of a name, and a name is a song's identity, so a mapping
     * that failed to reverse on one name would upload a second copy of that song from this device to every other one.
     * A trailing space or dot is one Windows strips, and a control character one it refuses.
     */
    override fun canHoldFileName(name: String) = isValidFileName(name) && (
        !isWindows || (name.none { it in WINDOWS_RESERVED_CHARACTERS || it < ' ' } && !name.endsWith(' ') && !name.endsWith('.'))
    )

    /**
     * Retries [operation] while Windows refuses it because somebody else is holding the file open. An anti-virus
     * scanner opens every file that appears, and a rename over it or a deletion of it is refused for as long as it
     * does - for tens of milliseconds, not for seconds. A bounded retry of one named operating-system failure rather
     * than a wait for a race to settle: the app's own reads share deletion (see [readAllBytes]), so anything still
     * holding the file is outside the process and will let go or will not. Anywhere else the same exception is a
     * permission that will not clear, and is thrown at once.
     */
    internal suspend fun <T> retryingWhileDenied(operation: () -> T): T {
        if (isWindows) {
            repeat(DENIED_RETRIES) { attempt ->
                try {
                    return operation()
                } catch (_: AccessDeniedException) {
                    delay(DENIED_RETRY_DELAY_MILLIS shl attempt)
                }
            }
        }
        return operation()
    }

    /**
     * Not [File.readBytes], which opens a `FileInputStream`: on Windows the JDK opens one without
     * `FILE_SHARE_DELETE`, so for as long as a scan holds the stream, a rename over that file or a deletion of it is
     * refused - and a scan reads sixty-four files at a time while a sync run is writing. The NIO channel behind
     * `Files.readAllBytes` shares deletion, so the same read leaves the file movable.
     */
    private fun File.readAllBytes(): ByteArray = Files.readAllBytes(toPath())

    /**
     * Read without asking first whether it is a file, which is a call into the file system per read: a file that is
     * not there answers [NoSuchFileException] (see [readingAsStorage]), and a directory under the name, which is not a
     * file either, is only asked about once the read has failed.
     */
    private fun File.readIfFile(name: String): ByteArray? = try {
        readingAsStorage(name) { readAllBytes() }
    } catch (exception: LibraryStorageException) {
        if (isDirectory) null else throw exception
    }

    private suspend fun writeAtomically(directory: StorageDirectory, name: String, write: (File) -> Unit) {
        directoryFile(directory).mkdirs()
        val target = file(directory, name).toPath()
        // A name of its own per write, so two writes of one file cannot share a temporary file.
        val temporary = Files.createTempFile(target.parent, TEMPORARY_FILE_PREFIX, TEMPORARY_FILE_SUFFIX)
        try {
            write(temporary.toFile())
            // Flushed to the device before the rename, or a power loss right after could keep the name and lose the bytes.
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
            retryingWhileDenied {
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    // Some file systems (network shares, some Android storage) cannot do it in one step; the plain move
                    // still never leaves the target truncated, since it copies first and replaces at the end.
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /** A file that is there but will not be read or written is a failure of the storage, not of whoever asked. */
    private inline fun <T> failingAsStorage(name: String, operation: () -> T): T = try {
        operation()
    } catch (exception: IOException) {
        throw LibraryStorageException("Could not access \"$name\".", exception)
    }

    /**
     * [failingAsStorage] for a read, which has one more answer: a file that is not there, or was removed just before
     * the read, is not there, and null is what the contract says about a file that is not there. Anything else is a
     * file that is there and could not be read. Internal so that the test can hand it the exception.
     */
    internal inline fun <T : Any> readingAsStorage(name: String, read: () -> T): T? = try {
        read()
    } catch (_: NoSuchFileException) {
        null
    } catch (exception: IOException) {
        throw LibraryStorageException("Could not access \"$name\".", exception)
    }

    private fun file(directory: StorageDirectory, name: String): File {
        requireValidFileName(name)
        return File(directoryFile(directory), name.toStoredName())
    }

    private fun directoryFile(directory: StorageDirectory) = directoryFiles
        .getOrPut(directory) { directory.pathSegments.fold(root) { parent, segment -> File(parent, segment) } }
        .also { if (sweptDirectories.add(directory)) removeLeftovers(it) }

    private fun removeLeftovers(directoryFile: File) {
        val newestLeftover = System.currentTimeMillis() - LEFTOVER_AGE_MILLIS
        directoryFile.listFiles { _, name -> LEFTOVER_NAME.matches(name) }
            ?.filter { it.isFile && it.lastModified() in 1..newestLeftover }
            ?.forEach { it.delete() }
    }

    private fun String.toStoredName() = if (isWindows && trimStart(DEVICE_NAME_ESCAPE).isDeviceName()) DEVICE_NAME_ESCAPE + this else this

    private fun String.toLibraryName() = if (isWindows && startsWith(DEVICE_NAME_ESCAPE) && trimStart(DEVICE_NAME_ESCAPE).isDeviceName()) drop(1) else this

    private fun String.isDeviceName() = DEVICE_NAME.matches(substringBefore('.').trimEnd(' '))

    private companion object {

        const val TEMPORARY_FILE_PREFIX = ".campfire-"
        const val TEMPORARY_FILE_SUFFIX = ".tmp"
        val LEFTOVER_NAME = Regex("""\.campfire-\d+\.tmp|.+\.\d+\.tmp""")
        const val LEFTOVER_AGE_MILLIS = 60L * 60L * 1000L
        const val DEVICE_NAME_ESCAPE = '_'
        /** The characters Windows refuses in a file name; `/` and `\` are already refused everywhere by `requireValidFileName`. */
        const val WINDOWS_RESERVED_CHARACTERS = "?:*\"<>|"
        const val DENIED_RETRIES = 3
        const val DENIED_RETRY_DELAY_MILLIS = 20L
        val DEVICE_NAME = Regex("""con|prn|aux|nul|(com|lpt)[0-9¹²³]""", RegexOption.IGNORE_CASE)
    }
}
