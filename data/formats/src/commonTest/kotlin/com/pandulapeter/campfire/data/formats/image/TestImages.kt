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
 * Tiny files of each format, built byte by byte: a valid header and as little after it as a decoder would expect. Each
 * comes with [headerEnd], the length of the shortest prefix its size can be read from.
 */
internal class TestImage(val bytes: ByteArray, val headerEnd: Int)

internal object TestImages {

    fun png(width: Int, height: Int) = TestImage(
        bytes = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + u32be(13) + ascii("IHDR") + u32be(width) + u32be(height) +
            bytes(8, 2, 0, 0, 0) + u32be(0) + u32be(0) + ascii("IEND") + u32be(0),
        headerEnd = 24,
    )

    fun gif(width: Int, height: Int) = TestImage(
        bytes = ascii("GIF89a") + u16le(width) + u16le(height) + bytes(0, 0, 0, 0x3B),
        headerEnd = 10,
    )

    fun webpLossy(width: Int, height: Int) = webp(
        chunk = "VP8 ",
        data = bytes(0x10, 0x02, 0x00, 0x9D, 0x01, 0x2A) + u16le(width) + u16le(height) + ByteArray(8),
        headerEnd = 30,
    )

    fun webpLossless(width: Int, height: Int) = webp(
        chunk = "VP8L",
        data = bytes(0x2F) + u32le((width - 1) or ((height - 1) shl 14)) + ByteArray(8),
        headerEnd = 25,
    )

    fun webpExtended(width: Int, height: Int) = webp(
        chunk = "VP8X",
        data = bytes(0x10, 0, 0, 0) + u24le(width - 1) + u24le(height - 1),
        headerEnd = 30,
    )

    /** A baseline (or [frameMarker]) JPEG with [segments] (whole marker segments) between its `SOI` and frame header. */
    fun jpeg(width: Int, height: Int, frameMarker: Int = 0xC0, segments: ByteArray = ByteArray(0)): TestImage {
        val start = bytes(0xFF, 0xD8) + segments
        val frame = bytes(0xFF, frameMarker) + u16be(17) + bytes(8) + u16be(height) + u16be(width) +
            bytes(3, 1, 0x22, 0, 2, 0x11, 1, 3, 0x11, 1)
        return TestImage(bytes = start + frame + bytes(0xFF, 0xDA) + u16be(2) + bytes(0x12, 0x34, 0xFF, 0xD9), headerEnd = start.size + 9)
    }

    /** An `APP1` segment of EXIF data whose first directory holds a camera make, then [orientation]. */
    fun exif(orientation: Int, isLittleEndian: Boolean): ByteArray {
        val u16 = if (isLittleEndian) ::u16le else ::u16be
        val u32 = if (isLittleEndian) ::u32le else ::u32be
        val make = u16(0x010F) + u16(2) + u32(4) + u32(38)
        val turn = u16(0x0112) + u16(3) + u32(1) + u16(orientation) + u16(0)
        val tiff = ascii(if (isLittleEndian) "II" else "MM") + u16(42) + u32(8) + u16(2) + make + turn + u32(0) + ascii("Cam\u0000")
        return segment(0xE1, ascii("Exif") + bytes(0, 0) + tiff)
    }

    fun segment(marker: Int, payload: ByteArray) = bytes(0xFF, marker) + u16be(payload.size + 2) + payload

    fun ascii(text: String) = text.encodeToByteArray()

    fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun webp(chunk: String, data: ByteArray, headerEnd: Int): TestImage {
        val body = ascii("WEBP") + ascii(chunk) + u32le(data.size) + data
        return TestImage(bytes = ascii("RIFF") + u32le(body.size) + body, headerEnd = headerEnd)
    }

    private fun u16be(value: Int) = bytes(value shr 8, value)

    private fun u16le(value: Int) = bytes(value, value shr 8)

    private fun u24le(value: Int) = bytes(value, value shr 8, value shr 16)

    private fun u32be(value: Int) = bytes(value ushr 24, value shr 16, value shr 8, value)

    private fun u32le(value: Int) = bytes(value, value shr 8, value shr 16, value ushr 24)
}
