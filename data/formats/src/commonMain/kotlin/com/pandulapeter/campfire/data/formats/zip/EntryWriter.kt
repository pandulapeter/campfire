/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.zip

import com.pandulapeter.campfire.data.model.hashing.Sha256
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Reads one entry into this sink through [write], which hands it a [EntryWriter], and ends the sink the way
 * [EntrySink] promises: closed once exactly [declaredSize] bytes with the CRC-32 [crc] arrived, abandoned on anything
 * else, a cancellation included. A stored entry's copy and a deflated entry's inflate both come through here, so the
 * checks are the same for both.
 */
internal suspend fun EntrySink.receive(declaredSize: Long, crc: Long, write: suspend (EntryWriter) -> Unit) {
    try {
        if (declaredSize < 0) {
            throw ZipException("An entry cannot declare $declaredSize bytes.")
        }
        val writer = EntryWriter(this, declaredSize)
        write(writer)
        if (writer.size != declaredSize) {
            throw ZipException("The entry produced ${writer.size} bytes instead of the $declaredSize it declared.")
        }
        if (writer.crc != crc) {
            throw ZipException("Checksum mismatch.")
        }
        close(writer.sha256Hex())
    } catch (throwable: Throwable) {
        // Under cancellation every suspension point throws again at once, and a staged file has to be deleted all the same.
        withContext(NonCancellable) {
            try {
                abandon()
            } catch (abandonFailure: Throwable) {
                throwable.addSuppressed(abandonFailure)
            }
        }
        throw throwable
    }
}

/**
 * Passes an entry's bytes on to its sink, held to the size the entry declared as they arrive rather than once it ended,
 * with its CRC-32 and SHA-256 taken from the same slices. It yields every [YIELD_INTERVAL] bytes, so that a large entry
 * can be cancelled and the web's one thread keeps painting.
 */
internal class EntryWriter(private val sink: EntrySink, private val declaredSize: Long) {

    private var runningCrc = Crc32.INITIAL
    private val digest = Sha256.Digest()
    private var sinceYield = 0L

    var size = 0L
        private set

    val crc get() = Crc32.finish(runningCrc)

    suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (length > declaredSize - size) {
            throw ZipException("The entry produces more than the $declaredSize bytes it declared.")
        }
        runningCrc = Crc32.update(runningCrc, bytes, offset, length)
        digest.update(bytes, offset, length)
        size += length
        sink.write(bytes, offset, length)
        sinceYield += length
        if (sinceYield >= YIELD_INTERVAL) {
            sinceYield = 0
            yield()
        }
    }

    fun sha256Hex() = digest.finishToHex()
}

/** How many bytes the zip reader reads, inflates, or writes between two yields. */
internal const val YIELD_INTERVAL = 256L * 1024
