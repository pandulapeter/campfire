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

import kotlin.random.Random

/** Pictures the import takes for what they are: a PNG header the library reads, then bytes of [seed] up to [size]. */
internal object TestPictures {

    fun png(seed: Int, size: Int, width: Int = 64, height: Int = 48): ByteArray {
        fun u32(value: Int) = byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())
        val header = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + u32(13) + "IHDR".encodeToByteArray() +
            u32(width) + u32(height) + byteArrayOf(8, 2, 0, 0, 0) + u32(0)
        return header + Random(seed).nextBytes(maxOf(0, size - header.size))
    }
}
