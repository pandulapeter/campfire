/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation

import com.pandulapeter.campfire.data.model.library.normalizedToNfc
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileSink
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileSource
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StoredFileInfo

/**
 * A [FileStorage] in memory. With [foldsNames] it behaves like APFS: a file keeps the spelling it was first written
 * with, and every question about a name is answered by its folded form, so that two spellings of one name are one
 * file. Without it every spelling is a file of its own, as on ext4.
 */
internal class InMemoryFileStorage(private val foldsNames: Boolean = false) : FileStorage {

    private class StoredFile(val name: String, val bytes: ByteArray)

    private val files = mutableMapOf<Pair<StorageDirectory, String>, StoredFile>()
    var existsCalls = 0
    var listCalls = 0

    private fun key(directory: StorageDirectory, name: String) = directory to if (foldsNames) name.normalizedToNfc().lowercase() else name

    override suspend fun list(directory: StorageDirectory) = files.filterKeys { it.first == directory }.values
        .map { StoredFileInfo(name = it.name, size = it.bytes.size.toLong(), lastModified = 0) }
        .also { listCalls++ }

    override suspend fun listNames(directory: StorageDirectory) = files.filterKeys { it.first == directory }.values.map { it.name }.also { listCalls++ }

    override suspend fun info(directory: StorageDirectory, name: String) =
        files[key(directory, name)]?.let { StoredFileInfo(name = it.name, size = it.bytes.size.toLong(), lastModified = 0) }

    override suspend fun exists(directory: StorageDirectory, name: String) = (key(directory, name) in files).also { existsCalls++ }

    override suspend fun readText(directory: StorageDirectory, name: String) = readBytes(directory, name)?.decodeToString()

    override suspend fun readBytes(directory: StorageDirectory, name: String) = files[key(directory, name)]?.bytes

    override suspend fun writeText(directory: StorageDirectory, name: String, text: String) = writeBytes(directory, name, text.encodeToByteArray())

    override suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray) {
        val key = key(directory, name)
        files[key] = StoredFile(name = files[key]?.name ?: name, bytes = bytes)
    }

    override suspend fun openSink(directory: StorageDirectory, name: String): FileSink {
        writeBytes(directory, name, ByteArray(0))
        return object : FileSink {
            override var position = 0L
                private set

            override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
                writeBytes(directory, name, readBytes(directory, name)!! + bytes.copyOfRange(offset, offset + length))
                position += length
            }

            override suspend fun close() = Unit

            override suspend fun abandon() = delete(directory, name)
        }
    }

    override suspend fun openSource(directory: StorageDirectory, name: String): FileSource? = readBytes(directory, name)?.let { bytes ->
        object : FileSource {
            override val size = bytes.size.toLong()

            override suspend fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
                if (position >= size) return 0
                val end = minOf(size, position + length).toInt()
                bytes.copyInto(buffer, offset, position.toInt(), end)
                return end - position.toInt()
            }

            override suspend fun close() = Unit
        }
    }

    override suspend fun moveInto(from: StorageDirectory, fromName: String, to: StorageDirectory, toName: String) {
        val file = files.remove(key(from, fromName)) ?: throw LibraryStorageException("$fromName is not there to be moved.")
        files[key(to, toName)] = StoredFile(name = toName, bytes = file.bytes)
    }

    override suspend fun copyInto(from: StorageDirectory, fromName: String, to: StorageDirectory, toName: String) =
        writeBytes(to, toName, readBytes(from, fromName) ?: throw LibraryStorageException("$fromName is not there to be copied."))

    override suspend fun availableSpace(directory: StorageDirectory) = Long.MAX_VALUE

    override fun pathOf(directory: StorageDirectory, name: String) = "${directory.name}/$name"

    override suspend fun delete(directory: StorageDirectory, name: String) {
        files -= key(directory, name)
    }
}
