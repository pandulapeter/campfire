/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

/** The Adler-32 checksum (RFC 1950) that ends a zlib stream. */
internal fun adler32(data: ByteArray): Int {
    var a = 1L
    var b = 0L
    var index = 0
    while (index < data.size) {
        val end = minOf(index + 5552, data.size)
        while (index < end) {
            a += data[index++].toInt() and 255
            b += a
        }
        a %= 65521
        b %= 65521
    }
    return ((b shl 16) or a).toInt()
}

/** The CRC-32 (ISO 3309, the one zip and PNG use) of [data]. */
internal fun crc32(data: ByteArray): Int {
    var crc = -1
    for (byte in data) crc = CRC_TABLE[(crc xor byte.toInt()) and 255] xor (crc ushr 8)
    return crc.inv()
}

private val CRC_TABLE = IntArray(256) { index ->
    var value = index
    repeat(8) { value = if (value and 1 != 0) 0xEDB88320.toInt() xor (value ushr 1) else value ushr 1 }
    value
}
