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
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.posix.EINTR
import platform.posix.ENOENT
import platform.posix.O_RDONLY
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.errno
import platform.posix.fstat
import platform.posix.pread
import platform.posix.stat
import platform.posix.strerror

/**
 * The iOS [FileSource], on a POSIX descriptor read with `pread` at 64-bit offsets, which moves no shared position, so
 * reads may overlap; `NSFileHandle` is avoided for the reason [PosixFileSink] gives.
 */
internal class PosixFileSource private constructor(
    private val descriptor: Int,
    private val name: String,
    override val size: Long,
) : FileSource {

    private var isClosed = false

    override suspend fun read(position: Long, buffer: ByteArray, offset: Int, length: Int) = withContext(Dispatchers.IO) {
        if (isClosed) throw LibraryStorageException("\"$name\" is already closed.")
        var total = 0
        while (total < length) {
            val read = buffer.usePinned { pread(descriptor, it.addressOf(offset + total), (length - total).convert(), position + total) }
            when {
                read > 0 -> total += read.toInt()
                read == 0L -> break
                errno == EINTR -> continue
                else -> throw failure(name)
            }
        }
        total
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        if (!isClosed) {
            isClosed = true
            platform.posix.close(descriptor)
        }
        Unit
    }

    companion object {

        /** Null for a file that is not there and for a directory under the name, as a read answers for them. */
        fun openFile(path: String, name: String): PosixFileSource? {
            val descriptor = platform.posix.open(path, O_RDONLY)
            if (descriptor < 0) {
                if (errno == ENOENT) return null
                throw failure(name)
            }
            val size = memScoped {
                val status = alloc<stat>()
                if (fstat(descriptor, status.ptr) != 0) {
                    val failure = failure(name)
                    platform.posix.close(descriptor)
                    throw failure
                }
                if ((status.st_mode.toInt() and S_IFMT) == S_IFDIR) -1L else status.st_size
            }
            if (size < 0) {
                platform.posix.close(descriptor)
                return null
            }
            return PosixFileSource(descriptor = descriptor, name = name, size = size)
        }

        private fun failure(name: String) = LibraryStorageException("Could not read \"$name\": ${strerror(errno)?.toKString()}")
    }
}
