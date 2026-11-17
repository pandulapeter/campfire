/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import java.io.InputStream

/**
 * To the end of the stream or to [limit] bytes, whichever comes first, holding the bytes once: into an array of the
 * provider's [size] where it says one within the limit (a byte more is asked for, since the claim may be wrong), else
 * in chunks joined at the end. A stream copied into a growing buffer held a 50 MB attachment more than twice over.
 */
internal fun InputStream.readAtMost(limit: Int, size: Long?): ByteArray {
    if (size == null || size !in 0 until limit) return readChunks(limit = limit, head = ByteArray(0))
    val bytes = ByteArray(size.toInt())
    val filled = readInto(bytes)
    if (filled < bytes.size) return bytes.copyOf(filled)
    val next = read()
    return if (next < 0) bytes else readChunks(limit = limit, head = bytes + next.toByte())
}

private fun InputStream.readChunks(limit: Int, head: ByteArray): ByteArray {
    val chunks = mutableListOf(head)
    var total = head.size
    while (total < limit) {
        val chunk = ByteArray(minOf(READ_CHUNK_SIZE, limit - total))
        val filled = readInto(chunk)
        if (filled > 0) chunks += if (filled == chunk.size) chunk else chunk.copyOf(filled)
        total += filled
        if (filled < chunk.size) break
    }
    if (chunks.size == 1) return chunks.single()
    val bytes = ByteArray(total)
    var offset = 0
    chunks.forEach { chunk ->
        chunk.copyInto(bytes, offset)
        offset += chunk.size
    }
    return bytes
}

/** Fills [target] from the stream and answers how much of it, less only where the stream ended first. */
private fun InputStream.readInto(target: ByteArray): Int {
    var filled = 0
    while (filled < target.size) {
        val count = read(target, filled, target.size - filled)
        if (count < 0) break
        filled += count
    }
    return filled
}

private const val READ_CHUNK_SIZE = 1024 * 1024
