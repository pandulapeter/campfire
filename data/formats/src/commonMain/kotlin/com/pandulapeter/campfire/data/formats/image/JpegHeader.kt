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

/**
 * A JPEG's size, from its first frame header (`SOFn`), turned by the EXIF orientation of an `APP1` segment before it.
 * The segments are walked by their declared lengths, so the scan data is never reached; a file whose frame header lies
 * past [ImageInfo.MAX_HEADER_BYTES] (behind very large metadata segments) is not read.
 */
internal object JpegHeader {

    fun read(header: HeaderBytes): ImageInfo? {
        if (header.u8(0) != 0xFF || header.u8(1) != 0xD8) return null
        var offset = 2
        var isTurned = false
        while (true) {
            if (header.u8(offset) != 0xFF) return null
            // Any number of 0xFF bytes may pad the space before a marker.
            while (header.u8(offset) == 0xFF) offset++
            val marker = header.u8(offset)
            offset++
            when {
                marker < 0 || marker == 0x00 -> return null
                isStandalone(marker) -> continue
                marker == 0xD9 || marker == 0xDA -> return null
            }
            val length = header.u16be(offset)
            if (length < 2) return null
            if (isFrameHeader(marker)) return frameSize(header, offset, length, isTurned)
            if (marker == 0xE1 && !isTurned) isTurned = ExifOrientation.isQuarterTurn(header, offset + 2, length - 2)
            offset += length
        }
    }

    /** `SOF0`–`SOF15`, except `DHT` (C4), `JPG` (C8), and `DAC` (CC), which share the range. */
    private fun isFrameHeader(marker: Int) = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC

    /** `TEM`, the restart markers, and a repeated `SOI`, which carry no length. */
    private fun isStandalone(marker: Int) = marker == 0x01 || marker in 0xD0..0xD8

    private fun frameSize(header: HeaderBytes, offset: Int, length: Int, isTurned: Boolean): ImageInfo? {
        // Length, precision, height, width: a height of 0 is defined later by a DNL marker, past where this reads.
        if (length < 8) return null
        val height = header.u16be(offset + 3)
        val width = header.u16be(offset + 5)
        if (height < 0 || width < 0) return null
        return if (isTurned) {
            ImageInfo.sized(ImageFormat.JPEG, height.toLong(), width.toLong())
        } else {
            ImageInfo.sized(ImageFormat.JPEG, width.toLong(), height.toLong())
        }
    }
}
