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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

/**
 * The [FileStorage] of every JVM based platform, on top of [java.io.File]. The root is chosen by the `actual` factory
 * of each platform, which also makes it possible to point a test at a temporary directory.
 *
 * Writes go to a temporary file first and are moved into place afterwards, so that a crash in the middle of one leaves
 * the previous content intact instead of a half written file.
 *
 * The Android copy of this class is identical - the two platforms cannot share a source set until the `roomMain`
 * hierarchy template is gone.
 */
internal class JvmFileStorage(
    private val root: File,
    private val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("windows", ignoreCase = true),
) : FileStorage {

    private val sweptDirectories = ConcurrentHashMap.newKeySet<StorageDirectory>()

    override suspend fun list(directory: StorageDirectory) = withContext(Dispatchers.IO) {
        val directoryFile = directoryFile(directory)
        // A directory that could not be created is not there yet, which is the same as being empty. One that is
        // there but cannot be listed is a failure and has to be reported as one: passing it off as an empty library
        // would invite the user to create songs in a folder the app cannot read.
        val files = directoryFile.listFiles()
            ?: if (directoryFile.isDirectory) throw IOException("Could not read \"${directoryFile.absolutePath}\".") else emptyArray()
        files.filter { it.isFile && !it.name.endsWith(TEMPORARY_FILE_SUFFIX) }
            .map { StoredFileInfo(name = it.name.toLibraryName(), size = it.length(), lastModified = it.lastModified()) }
            .sortedBy { it.name }
    }

    override suspend fun listNames(directory: StorageDirectory) = withContext(Dispatchers.IO) {
        val directoryFile = directoryFile(directory)
        directoryFile.list()?.toList() ?: if (directoryFile.isDirectory) throw IOException("Could not read \"${directoryFile.absolutePath}\".") else emptyList()
    }

    override suspend fun info(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).let { if (it.isFile) StoredFileInfo(name = name, size = it.length(), lastModified = it.lastModified()) else null }
    }

    override suspend fun exists(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).isFile
    }

    override suspend fun readText(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).let { if (it.isFile) readingAsStorage(name) { it.readAllBytes() }?.decodeLibraryText() else null }
    }

    override suspend fun readBytes(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).let { if (it.isFile) readingAsStorage(name) { it.readAllBytes() } else null }
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
    override fun canHoldFileName(name: String) = !isWindows || (
        name.none { it in WINDOWS_RESERVED_CHARACTERS || it < ' ' } && !name.endsWith(' ') && !name.endsWith('.')
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

    private suspend fun writeAtomically(directory: StorageDirectory, name: String, write: (File) -> Unit) {
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
     * [failingAsStorage] for a read, which has one more answer: a file removed between the [File.isFile] check and
     * the read is not there, and null is what the contract says about a file that is not there. Anything else is a
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

    private fun directoryFile(directory: StorageDirectory) = directory.pathSegments
        .fold(root) { parent, segment -> File(parent, segment) }
        .also {
            it.mkdirs()
            if (sweptDirectories.add(directory)) removeLeftovers(it)
        }

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
