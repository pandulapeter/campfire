/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.OutOfSpaceException
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileSink
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * A [JvmFileStorage] over [root] that keeps count of what every file it wrote holds, as a disk of [capacity] bytes
 * would: it answers its free space as what is left of that capacity (or [reportedSpace], for a storage whose estimate
 * is wrong), refuses a write past it as a full disk does, and remembers the most the staging folder ever held
 * ([peakStagedBytes]). [onStagedGrowth] runs after every write into the staging folder with what it holds then, for a
 * test to hold that against what the staging reserved; [beforeChange] runs before every write, move, copy, and
 * deletion, for a test to fail or cancel one.
 */
internal class MeteredFileStorage(
    val root: File,
    var capacity: Long = Long.MAX_VALUE / 4,
    private val delegate: JvmFileStorage = JvmFileStorage(root),
) : FileStorage by delegate {

    private val sizes = mutableMapOf<Pair<StorageDirectory, String>, Long>()

    /** Everything the storage holds, by the sizes it was told of. */
    var usedBytes = 0L
        private set

    /** What the staging folder holds now. */
    var stagedBytes = 0L
        private set

    var peakStagedBytes = 0L
        private set

    private val stagedFilesByRequest = mutableMapOf<Long, Int>()

    /** The requests with a file in the staging folder, by the number their names carry after the session. */
    val stagingRequests: Set<Long> get() = stagedFilesByRequest.keys

    var reportedSpace: (() -> Long)? = null
    var onStagedGrowth: suspend (stagedBytes: Long) -> Unit = {}
    var beforeChange: suspend (directory: StorageDirectory, name: String) -> Unit = { _, _ -> }

    init {
        runBlocking { StorageDirectory.entries.forEach { directory -> delegate.list(directory).forEach { record(directory, it.name, it.size) } } }
    }

    /** The names of the staging folder, as the storage lists them. */
    suspend fun stagedNames() = delegate.listNames(StorageDirectory.IMPORTS).sorted()

    override suspend fun availableSpace(directory: StorageDirectory) = reportedSpace?.invoke() ?: (capacity - usedBytes)

    override suspend fun writeText(directory: StorageDirectory, name: String, text: String) = writeBytes(directory, name, text.encodeToByteArray())

    override suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray) {
        beforeChange(directory, name)
        requireRoom(bytes.size.toLong() - (sizes[directory to name] ?: 0L), name)
        delegate.writeBytes(directory, name, bytes)
        refresh(directory, name)
    }

    override suspend fun openSink(directory: StorageDirectory, name: String): FileSink {
        beforeChange(directory, name)
        val sink = delegate.openSink(directory, name)
        record(directory, name, 0)
        return object : FileSink by sink {
            override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
                beforeChange(directory, name)
                requireRoom(length.toLong(), name)
                sink.write(bytes, offset, length)
                record(directory, name, (sizes[directory to name] ?: 0L) + length)
                if (directory == StorageDirectory.IMPORTS) onStagedGrowth(stagedBytes)
            }

            override suspend fun close() {
                sink.close()
                refresh(directory, name)
            }

            override suspend fun abandon() {
                sink.abandon()
                refresh(directory, name)
            }
        }
    }

    override suspend fun moveInto(from: StorageDirectory, fromName: String, to: StorageDirectory, toName: String) {
        beforeChange(from, fromName)
        delegate.moveInto(from, fromName, to, toName)
        refresh(from, fromName)
        refresh(to, toName)
    }

    override suspend fun copyInto(from: StorageDirectory, fromName: String, to: StorageDirectory, toName: String) {
        beforeChange(to, toName)
        requireRoom(delegate.info(from, fromName)?.size ?: 0L, toName)
        delegate.copyInto(from, fromName, to, toName)
        refresh(to, toName)
    }

    override suspend fun delete(directory: StorageDirectory, name: String) {
        beforeChange(directory, name)
        delegate.delete(directory, name)
        refresh(directory, name)
    }

    private fun requireRoom(bytes: Long, name: String) {
        if (usedBytes + bytes > capacity) throw LibraryStorageException("No space left for \"$name\".", OutOfSpaceException())
    }

    private suspend fun refresh(directory: StorageDirectory, name: String) = record(directory, name, delegate.info(directory, name)?.size)

    private fun record(directory: StorageDirectory, name: String, size: Long?) {
        val key = directory to name
        val change = (size ?: 0L) - (sizes[key] ?: 0L)
        val isNew = key !in sizes
        if (size == null) sizes -= key else sizes[key] = size
        usedBytes += change
        if (directory == StorageDirectory.IMPORTS) {
            stagedBytes += change
            val request = name.split('-').getOrNull(1)?.toLongOrNull()
            if (request != null && isNew && size != null) stagedFilesByRequest[request] = (stagedFilesByRequest[request] ?: 0) + 1
            if (request != null && !isNew && size == null) stagedFilesByRequest.computeIfPresent(request) { _, count -> (count - 1).takeIf { it > 0 } }
            peakStagedBytes = maxOf(peakStagedBytes, stagedBytes)
        }
    }
}
