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
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** The JVM's [FileSource]: positional reads of a [FileChannel], which move no shared position, so reads may overlap. */
internal class JvmFileSource(
    private val channel: FileChannel,
    private val name: String,
) : FileSource {

    override val size = failingAsStorage { channel.size() }

    override suspend fun read(position: Long, buffer: ByteArray, offset: Int, length: Int) = withContext(Dispatchers.IO) {
        val target = ByteBuffer.wrap(buffer, offset, length)
        failingAsStorage {
            while (target.hasRemaining()) {
                if (channel.read(target, position + target.position() - offset) < 0) break
            }
        }
        target.position() - offset
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        try {
            channel.close()
        } catch (_: IOException) {
        }
    }

    private inline fun <T> failingAsStorage(operation: () -> T): T = try {
        operation()
    } catch (exception: IOException) {
        throw LibraryStorageException("Could not read \"$name\".", exception)
    }
}
