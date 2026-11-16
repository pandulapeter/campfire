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
 * A WebP's size, from its first chunk: the key frame of a lossy `VP8 `, the header of a lossless `VP8L`, or the canvas
 * of an extended `VP8X`. An extended file's EXIF orientation is not applied, as the platforms' decoders do not apply it.
 */
internal object WebpHeader {

    private const val CHUNK = 12
    private const val DATA = CHUNK + 8

    fun read(header: HeaderBytes): ImageInfo? {
        if (!header.startsWith("RIFF") || !header.startsWith("WEBP", offset = 8)) return null
        return when {
            header.startsWith("VP8 ", offset = CHUNK) -> lossy(header)
            header.startsWith("VP8L", offset = CHUNK) -> lossless(header)
            header.startsWith("VP8X", offset = CHUNK) -> extended(header)
            else -> null
        }
    }

    private fun lossy(header: HeaderBytes): ImageInfo? {
        // The frame tag's lowest bit is 0 on a key frame, the only kind a still image starts with, then the start code.
        val frameTag = header.u8(DATA)
        if (frameTag < 0 || frameTag and 1 != 0) return null
        if (header.u8(DATA + 3) != 0x9D || header.u8(DATA + 4) != 0x01 || header.u8(DATA + 5) != 0x2A) return null
        val width = header.u16le(DATA + 6)
        val height = header.u16le(DATA + 8)
        if (width < 0 || height < 0) return null
        // The top two bits of each are an upscaling hint the decoders draw at the coded size.
        return ImageInfo.sized(ImageFormat.WEBP, (width and 0x3FFF).toLong(), (height and 0x3FFF).toLong())
    }

    private fun lossless(header: HeaderBytes): ImageInfo? {
        if (header.u8(DATA) != 0x2F) return null
        val bits = header.u32le(DATA + 1)
        if (bits < 0) return null
        return ImageInfo.sized(ImageFormat.WEBP, (bits and 0x3FFF) + 1, ((bits shr 14) and 0x3FFF) + 1)
    }

    private fun extended(header: HeaderBytes): ImageInfo? {
        val width = header.u24le(DATA + 4)
        val height = header.u24le(DATA + 7)
        if (width < 0 || height < 0) return null
        return ImageInfo.sized(ImageFormat.WEBP, width + 1L, height + 1L)
    }
}
