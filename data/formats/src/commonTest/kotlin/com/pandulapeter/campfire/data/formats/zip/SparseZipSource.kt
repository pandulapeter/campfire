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

/**
 * A file of [size] bytes that holds only [pieces] (each at its offset, none overlapping) and zeroes everywhere else, so
 * that an archive past 4 GiB is read in CI in milliseconds: its headers and small entries at their far offsets, the
 * gigabytes of a large entry's data between them never held. It keeps the ranges it was asked for, so a test can tell
 * what a reader touched.
 */
internal class SparseZipSource(override val size: Long, private val pieces: List<Pair<Long, ByteArray>>) : ZipSource {

    val reads = mutableListOf<LongRange>()

    init {
        pieces.forEach { (offset, bytes) -> require(offset >= 0 && offset + bytes.size <= size) { "A piece at $offset is outside the file." } }
    }

    override suspend fun read(position: Long, buffer: ByteArray, offset: Int, length: Int) {
        require(position >= 0 && length >= 0 && position + length <= size) { "Read $length bytes at $position of $size." }
        reads += position until position + length
        buffer.fill(0, offset, offset + length)
        val end = position + length
        for ((start, bytes) in pieces) {
            val from = maxOf(start, position)
            val to = minOf(start + bytes.size, end)
            if (from < to) bytes.copyInto(buffer, offset + (from - position).toInt(), (from - start).toInt(), (to - start).toInt())
        }
    }
}

/** An archive described by its pieces as hex, the rest of its [size] bytes zeroes: see [SparseZipSource]. */
internal class SparseArchive(val size: Long, val pieces: List<Pair<Long, String>>) {

    fun source() = SparseZipSource(size, pieces.map { (offset, hex) -> offset to hex.hexToByteArray() })
}
