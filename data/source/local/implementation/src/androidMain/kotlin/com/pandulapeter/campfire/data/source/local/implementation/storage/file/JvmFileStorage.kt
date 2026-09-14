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

import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * The [FileStorage] of every JVM based platform, on top of [java.io.File]. The root is chosen by the `actual` factory
 * of each platform, which also makes it possible to point a test at a temporary directory.
 *
 * Writes go to a temporary file first and are moved into place afterwards, so that a crash in the middle of one leaves
 * the previous content intact instead of a half written file.
 *
 * The desktop copy of this class is identical - the two platforms cannot share a source set until the `roomMain`
 * hierarchy template is gone.
 */
internal class JvmFileStorage(private val root: File) : FileStorage {

    override suspend fun list(directory: StorageDirectory) = withContext(Dispatchers.IO) {
        val directoryFile = directoryFile(directory)
        // A directory that could not be created is not there yet, which is the same as being empty. One that is
        // there but cannot be listed is a failure and has to be reported as one: passing it off as an empty library
        // would invite the user to create songs in a folder the app cannot read.
        val files = directoryFile.listFiles()
            ?: if (directoryFile.isDirectory) throw IOException("Could not read \"${directoryFile.absolutePath}\".") else emptyArray()
        files.filter { it.isFile && !it.name.endsWith(TEMPORARY_FILE_SUFFIX) }
            .map { StoredFileInfo(name = it.name, size = it.length(), lastModified = it.lastModified()) }
            .sortedBy { it.name }
    }

    override suspend fun info(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).let { if (it.isFile) StoredFileInfo(name = it.name, size = it.length(), lastModified = it.lastModified()) else null }
    }

    override suspend fun exists(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).isFile
    }

    override suspend fun readText(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).let { if (it.isFile) failingAsStorage(name) { it.readText().withoutByteOrderMark() } else null }
    }

    override suspend fun readBytes(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).let { if (it.isFile) failingAsStorage(name) { it.readBytes() } else null }
    }

    override suspend fun writeText(directory: StorageDirectory, name: String, text: String) = withContext(Dispatchers.IO) {
        failingAsStorage(name) { writeAtomically(directory, name) { it.writeText(text) } }
    }

    override suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        failingAsStorage(name) { writeAtomically(directory, name) { it.writeBytes(bytes) } }
    }

    override suspend fun delete(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        val file = file(directory, name)
        if (file.exists() && !file.delete()) {
            throw IllegalStateException("Could not delete \"$name\".")
        }
    }

    private fun writeAtomically(directory: StorageDirectory, name: String, write: (File) -> Unit) {
        val target = file(directory, name).toPath()
        // A name of its own per write, so two writes of one file cannot share a temporary file.
        val temporary = Files.createTempFile(target.parent, "$name.", TEMPORARY_FILE_SUFFIX)
        try {
            write(temporary.toFile())
            // Flushed to the device before the rename, or a power loss right after could keep the name and lose the bytes.
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                // Some file systems (network shares, some Android storage) cannot do it in one step; the plain move still
                // never leaves the target truncated, since it copies first and replaces at the end.
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
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

    private fun file(directory: StorageDirectory, name: String): File {
        requireValidFileName(name)
        return File(directoryFile(directory), name)
    }

    private fun directoryFile(directory: StorageDirectory) = directory.pathSegments
        .fold(root) { parent, segment -> File(parent, segment) }
        .also { it.mkdirs() }

    private companion object {

        const val TEMPORARY_FILE_SUFFIX = ".tmp"
    }
}
