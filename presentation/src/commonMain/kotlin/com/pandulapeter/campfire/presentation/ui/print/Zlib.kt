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

import com.pandulapeter.campfire.data.formats.zip.Deflater

/**
 * Appends [data] to [destination] as a zlib stream (RFC 1950), which is what a PDF's FlateDecode and a PNG's image data
 * read: the raw deflate stream between zlib's two-byte header and the Adler-32 of [data].
 */
internal suspend fun Deflater.deflateZlib(data: ByteArray, destination: PrintBytes) {
    // CMF 0x78 is deflate with a 32 KB window, and FLG 0x01 makes the pair a multiple of 31 with no dictionary.
    destination.byte(0x78)
    destination.byte(0x01)
    deflate(data, destination)
    val checksum = adler32(data)
    for (shift in 24 downTo 0 step 8) destination.byte(checksum ushr shift and 255)
}
