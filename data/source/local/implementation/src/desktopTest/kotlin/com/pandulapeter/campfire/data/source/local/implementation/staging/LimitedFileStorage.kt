/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.staging

import com.pandulapeter.campfire.data.model.OutOfSpaceException
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.implementation.InMemoryFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileSink
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.yield

/**
 * An in-memory storage of [capacity] bytes, refusing a write past it as a full disk does (an [OutOfSpaceException] as
 * the cause). [reportedSpace] is what it answers about its free space, the truth by default; a storage that reports
 * more than it has (Safari's deliberately imprecise estimate) is one whose writes fail anyway. [beforeWrite] runs before
 * every write of a sink, for a test to fail or hold one.
 */
internal class LimitedFileStorage(
    private val capacity: Long,
    override val isSpaceAQuota: Boolean = false,
    private val delegate: InMemoryFileStorage = InMemoryFileStorage(),
) : FileStorage by delegate {

    var reportedSpace: (used: Long) -> Long = { capacity - it }
    var beforeWrite: suspend () -> Unit = {}

    suspend fun used() = StorageDirectory.entries.sumOf { directory -> delegate.list(directory).sumOf { it.size } }

    suspend fun stagedNames() = delegate.listNames(StorageDirectory.IMPORTS).sorted()

    override suspend fun availableSpace(directory: StorageDirectory): Long {
        // Suspends as a real storage's question does, so that two reservations asking at once interleave.
        yield()
        return reportedSpace(used())
    }

    override suspend fun openSink(directory: StorageDirectory, name: String): FileSink {
        val sink = delegate.openSink(directory, name)
        return object : FileSink by sink {
            override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
                beforeWrite()
                if (used() + length > capacity) throw LibraryStorageException("No space left for \"$name\".", OutOfSpaceException())
                sink.write(bytes, offset, length)
            }
        }
    }
}
