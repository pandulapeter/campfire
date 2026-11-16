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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** The JVM's [FileSink]: a buffered stream over [path], which the caller has created the directory of. */
internal class JvmFileSink(
    private val path: Path,
    private val name: String,
) : FileSink {

    private val output = failingAsStorage { BufferedOutputStream(Files.newOutputStream(path), BUFFER_SIZE) }

    override var position = 0L
        private set

    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = withContext(Dispatchers.IO) {
        failingAsStorage { output.write(bytes, offset, length) }
        position += length
    }

    override suspend fun close() = withContext(Dispatchers.IO) { failingAsStorage { output.close() } }

    override suspend fun abandon() = withContext(NonCancellable + Dispatchers.IO) {
        try {
            output.close()
        } catch (_: IOException) {
        }
        try {
            Files.deleteIfExists(path)
        } catch (_: IOException) {
        }
        Unit
    }

    private inline fun <T> failingAsStorage(operation: () -> T): T = try {
        operation()
    } catch (exception: IOException) {
        throw LibraryStorageException("Could not write \"$name\".", exception)
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
