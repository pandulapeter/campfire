/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.image

/** A PNG's size, from the `IHDR` chunk the format requires to come first. */
internal object PngHeader {

    private val SIGNATURE = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun read(header: HeaderBytes): ImageInfo? {
        if (SIGNATURE.indices.any { header.u8(it) != SIGNATURE[it] }) return null
        if (header.u32be(8) != 13L || !header.startsWith("IHDR", offset = 12)) return null
        return ImageInfo.sized(ImageFormat.PNG, header.u32be(16), header.u32be(20))
    }
}
