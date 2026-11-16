/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalForeignApi::class)

package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import platform.posix.FILE
import platform.posix.errno
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.remove
import platform.posix.strerror

/**
 * The iOS [FileSink], on C's buffered streams rather than `NSFileHandle`, whose plain `writeData` raises an
 * Objective-C exception on a full disk, which Kotlin cannot catch: a failing `fwrite` only returns short.
 */
internal class PosixFileSink(
    private val path: String,
    private val name: String,
) : FileSink {

    private var file: CPointer<FILE>? = fopen(path, "wb") ?: throw failure()

    override var position = 0L
        private set

    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = withContext(Dispatchers.IO) {
        if (length == 0) return@withContext
        val stream = file ?: throw LibraryStorageException("\"$name\" is already closed.")
        val written = bytes.usePinned { fwrite(it.addressOf(offset), 1u, length.convert(), stream) }
        if (written.toLong() != length.toLong()) throw failure()
        position += length
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        val stream = file ?: return@withContext
        file = null
        if (fclose(stream) != 0) throw failure()
    }

    override suspend fun abandon() = withContext(NonCancellable + Dispatchers.IO) {
        file?.let { fclose(it) }
        file = null
        remove(path)
        Unit
    }

    private fun failure() = LibraryStorageException("Could not write \"$name\": ${strerror(errno)?.toKString()}")
}
