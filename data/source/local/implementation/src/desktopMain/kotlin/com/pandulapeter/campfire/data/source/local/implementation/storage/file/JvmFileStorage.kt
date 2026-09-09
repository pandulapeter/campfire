package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

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

    override suspend fun exists(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).isFile
    }

    override suspend fun readText(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).let { if (it.isFile) it.readText().withoutByteOrderMark() else null }
    }

    override suspend fun readBytes(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        file(directory, name).let { if (it.isFile) it.readBytes() else null }
    }

    override suspend fun writeText(directory: StorageDirectory, name: String, text: String) = withContext(Dispatchers.IO) {
        writeAtomically(directory, name) { it.writeText(text) }
    }

    override suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        writeAtomically(directory, name) { it.writeBytes(bytes) }
    }

    override suspend fun delete(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        val file = file(directory, name)
        if (file.exists() && !file.delete()) {
            throw IllegalStateException("Could not delete \"$name\".")
        }
    }

    private fun writeAtomically(directory: StorageDirectory, name: String, write: (File) -> Unit) {
        val target = file(directory, name)
        val temporaryFile = File(target.parentFile, name + TEMPORARY_FILE_SUFFIX)
        try {
            write(temporaryFile)
            // Windows refuses to rename onto an existing file, so the target has to go first there.
            if (IS_WINDOWS) {
                target.delete()
            }
            if (!temporaryFile.renameTo(target)) {
                // Some file systems (network shares, some Android storage) fail the atomic move: fall back to a copy.
                temporaryFile.copyTo(target, overwrite = true)
            }
        } finally {
            temporaryFile.delete()
        }
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
        val IS_WINDOWS = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    }
}
