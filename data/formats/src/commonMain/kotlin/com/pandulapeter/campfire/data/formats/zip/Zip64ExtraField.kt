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
 * A central directory header's uncompressed size, compressed size, and local header offset, each one at its marker
 * (`0xFFFFFFFF`) replaced by its value from the header's extended information extra field (`0x0001`), which holds them
 * in that order, each only where its field is at its marker; values past those are ignored (a disk number, which is
 * never read). Null where a marked value cannot be had: no such field, one too short for its markers, or a value with
 * its top bit set. The extra field is walked within its [length] at [at] in [bytes], each block within what is left of
 * it, and a block running past it ends the walk.
 *
 * Only a central header is ever read this way. A local header's `0x0001` field is never read or validated: Java's and
 * Android's `ZipOutputStream` write none where the central header has one, and `ditto` writes zeroes in it, so the
 * central directory stays the truth.
 */
internal fun zip64Values(
    bytes: ByteArray,
    at: Int,
    length: Int,
    uncompressedSize: Long,
    compressedSize: Long,
    localHeaderOffset: Long,
): LongArray? {
    val end = at + length
    var position = at
    while (end - position >= 4) {
        val blockLength = bytes.u16(position + 2)
        if (blockLength > end - position - 4) return null
        if (bytes.u16(position) == ZIP64_EXTRA_FIELD_ID) {
            return valuesFrom(bytes, position + 4, blockLength, longArrayOf(uncompressedSize, compressedSize, localHeaderOffset))
        }
        position += 4 + blockLength
    }
    return null
}

private fun valuesFrom(bytes: ByteArray, at: Int, length: Int, values: LongArray): LongArray? {
    var position = at
    for (index in values.indices) {
        if (values[index] != ZIP64_MARKER) continue
        if (position + 8 > at + length) return null
        values[index] = bytes.u64(position).takeIf { it >= 0 } ?: return null
        position += 8
    }
    return values
}

/** What a 32-bit size or offset is set to when its value is in a ZIP64 field. */
internal const val ZIP64_MARKER = 0xFFFFFFFFL

private const val ZIP64_EXTRA_FIELD_ID = 0x0001
