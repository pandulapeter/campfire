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
 * A [ZipSink] that only counts the bytes [isData] says are a large entry's data and keeps every other write at its
 * offset, so an archive past 4 GiB is written in CI without its gigabytes and read back through [source]. It starts at
 * [start], as an archive written after that many bytes would, the bytes before it read as zeroes.
 */
internal class HeaderKeepingZipSink(start: Long = 0, private val isData: (ByteArray) -> Boolean = { false }) : ZipSink {

    private val pieces = mutableListOf<Pair<Long, ByteArrayBuilder>>()

    override var position = start
        private set

    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (!isData(bytes)) {
            val last = pieces.lastOrNull()
            val piece = if (last != null && last.first + last.second.size == position) {
                last.second
            } else {
                ByteArrayBuilder().also { pieces += position to it }
            }
            piece.bytes(bytes, offset, length)
        }
        position += length
    }

    fun source() = SparseZipSource(position, pieces.map { (offset, piece) -> offset to piece.build() })
}
