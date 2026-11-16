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
 * The Orientation tag of a JPEG's EXIF data, the way a camera says the sensor was held. Values 5 to 8 turn the picture
 * by a quarter (with or without a mirror), which every platform's decoder applies, so the displayed sides are swapped.
 * Only the first image file directory (the main picture's) is read; every offset in it is checked against the segment.
 */
internal object ExifOrientation {

    private const val TAG = 0x0112
    private const val TYPE_SHORT = 3

    /** Whether the `APP1` payload of [length] bytes at [start] is EXIF data turning the picture by a quarter. */
    fun isQuarterTurn(header: HeaderBytes, start: Int, length: Int): Boolean {
        if (length < 14 || !header.startsWith("Exif", offset = start) || header.u16be(start + 4) != 0) return false
        val tiff = start + 6
        val tiffLength = length - 6
        val isLittleEndian = when {
            header.startsWith("II", offset = tiff) -> true
            header.startsWith("MM", offset = tiff) -> false
            else -> return false
        }
        fun u16(at: Int) = if (isLittleEndian) header.u16le(tiff + at) else header.u16be(tiff + at)
        fun u32(at: Int) = if (isLittleEndian) header.u32le(tiff + at) else header.u32be(tiff + at)
        if (u16(2) != 42) return false
        val directory = u32(4)
        if (directory < 8 || directory + 2 > tiffLength) return false
        val entries = u16(directory.toInt())
        for (index in 0 until entries) {
            val entry = directory.toInt() + 2 + index * 12
            if (entry + 12 > tiffLength) return false
            if (u16(entry) == TAG) {
                if (u16(entry + 2) != TYPE_SHORT || u32(entry + 4) != 1L) return false
                return u16(entry + 8) in 5..8
            }
        }
        return false
    }
}
