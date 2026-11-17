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

/** Reads the unsigned byte at [offset]. */
internal fun ByteArray.u8(offset: Int): Int {
    requireBytes(offset, 1)
    return this[offset].toInt() and 0xFF
}

/** Reads the little-endian unsigned 16 bit integer at [offset]. */
internal fun ByteArray.u16(offset: Int): Int {
    requireBytes(offset, 2)
    return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
}

/** Reads the little-endian unsigned 32 bit integer at [offset]. Returned as a [Long] so that it stays unsigned. */
internal fun ByteArray.u32(offset: Int): Long {
    requireBytes(offset, 4)
    return (this[offset].toLong() and 0xFF) or
            ((this[offset + 1].toLong() and 0xFF) shl 8) or
            ((this[offset + 2].toLong() and 0xFF) shl 16) or
            ((this[offset + 3].toLong() and 0xFF) shl 24)
}

/**
 * Reads the little-endian 64 bit integer at [offset]. A value with its top bit set comes back negative, which is how a
 * ZIP64 field the archive set past what a [Long] holds is told apart and refused.
 */
internal fun ByteArray.u64(offset: Int): Long {
    requireBytes(offset, 8)
    return u32(offset) or (u32(offset + 4) shl 32)
}

private fun ByteArray.requireBytes(offset: Int, count: Int) {
    if (offset < 0 || offset > size - count) {
        throw ZipException("Truncated archive: $count byte(s) at offset $offset are outside the $size byte input.")
    }
}
