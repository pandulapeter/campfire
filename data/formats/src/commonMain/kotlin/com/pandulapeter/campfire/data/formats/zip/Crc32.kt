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

/** Table driven CRC-32 (polynomial 0xEDB88320), the checksum zip archives store for every entry. */
internal object Crc32 {

    private val table = IntArray(256) { index ->
        var value = index
        repeat(8) {
            value = if (value and 1 != 0) (value ushr 1) xor POLYNOMIAL else value ushr 1
        }
        value
    }

    /** The unsigned CRC-32 of [bytes]. */
    fun of(bytes: ByteArray): Long {
        var crc = -1
        for (byte in bytes) {
            crc = (crc ushr 8) xor table[(crc xor byte.toInt()) and 0xFF]
        }
        return crc.inv().toLong() and 0xFFFFFFFFL
    }

    /** What [update] starts from, before any byte. */
    const val INITIAL = -1

    /** Continues [crc], [INITIAL] or what an earlier call returned, over [length] bytes of [bytes] from [offset]. */
    fun update(crc: Int, bytes: ByteArray, offset: Int, length: Int): Int {
        var value = crc
        for (index in offset until offset + length) {
            value = (value ushr 8) xor table[(value xor bytes[index].toInt()) and 0xFF]
        }
        return value
    }

    /** The unsigned CRC-32 that a value [update] returned stands for. */
    fun finish(crc: Int) = crc.inv().toLong() and 0xFFFFFFFFL
}

private const val POLYNOMIAL = 0xEDB88320.toInt()
