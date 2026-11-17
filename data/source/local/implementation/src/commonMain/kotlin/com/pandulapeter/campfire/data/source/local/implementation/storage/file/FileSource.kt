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

import com.pandulapeter.campfire.data.model.hashing.Sha256
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException

/**
 * A file opened by [FileStorage.openSource] for reads at any position, so that a file of any size is hashed, compared,
 * or copied a slice at a time and never held whole. [size] is the file's as it was opened. A source of a file deleted
 * or replaced meanwhile either goes on reading the bytes it was opened on or throws `LibraryStorageException`, never
 * mixes the two. A failed read throws `LibraryStorageException`; [close] is quiet.
 */
interface FileSource {

    val size: Long

    /**
     * Reads up to [length] bytes from [position] into [buffer] at [offset] and answers how many it read: [length], or
     * fewer only where the file ends first, 0 at or past its end.
     */
    suspend fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int

    suspend fun close()
}

/** Runs [block] on the source and closes it, however the block ends. */
internal suspend inline fun <T> FileSource.use(block: (FileSource) -> T): T = try {
    block(this)
} finally {
    close()
}

/**
 * The file's SHA-256 as lowercase hex, read [Sha256.YIELD_CHUNK_SIZE] at a time, so that hashing a 50 MB attachment
 * holds a slice of it and not the file.
 */
internal suspend fun FileSource.sha256Hex(): String {
    val digest = Sha256.Digest()
    val buffer = ByteArray(Sha256.YIELD_CHUNK_SIZE)
    var position = 0L
    while (position < size) {
        val read = read(position, buffer, 0, minOf(buffer.size.toLong(), size - position).toInt())
        if (read <= 0) throw LibraryStorageException("The file ended before the size it was opened with.")
        digest.update(buffer, 0, read)
        position += read
    }
    return digest.finishToHex()
}
