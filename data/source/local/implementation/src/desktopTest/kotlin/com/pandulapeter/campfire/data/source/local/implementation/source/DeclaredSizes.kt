/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

/** Sets the uncompressed size the central directory declares for the entry [name] of this archive to [declared]. */
internal fun ByteArray.declareUncompressedSize(name: String, declared: Long) {
    fun u16(at: Int) = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)
    val end = size - 22
    var position = u16(end + 16) or (u16(end + 18) shl 16)
    repeat(u16(end + 10)) {
        val nameLength = u16(position + 28)
        if (decodeToString(position + 46, position + 46 + nameLength) == name) {
            for (byte in 0 until 4) this[position + 24 + byte] = (declared ushr (8 * byte)).toByte()
        }
        position += 46 + nameLength + u16(position + 30) + u16(position + 32)
    }
}
