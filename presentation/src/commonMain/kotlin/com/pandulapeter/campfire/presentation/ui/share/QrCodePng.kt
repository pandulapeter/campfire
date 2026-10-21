/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.share

import com.pandulapeter.campfire.presentation.ui.print.PrintBytes
import com.pandulapeter.campfire.presentation.ui.print.PrintDeflater
import com.pandulapeter.campfire.presentation.ui.print.crc32

/**
 * [code] as a 1-bit grayscale PNG, [moduleSize] pixels to a module, black on white, inside the quiet zone of four
 * modules a reader needs to find the symbol. A module is a whole number of pixels, since a scaled one blurs its edges.
 */
internal suspend fun qrCodePng(code: QrCode, moduleSize: Int = 8): ByteArray {
    require(moduleSize > 0) { "A module needs at least one pixel" }
    val width = (code.size + 2 * QUIET_ZONE) * moduleSize
    val rowLength = 1 + (width + 7) / 8
    // Each row starts with the filter type byte, 0 for none; a set bit is white.
    val scanlines = ByteArray(rowLength * width)
    for (y in 0 until width) {
        val moduleY = y / moduleSize - QUIET_ZONE
        for (x in 0 until width) {
            val moduleX = x / moduleSize - QUIET_ZONE
            val isDark = moduleX in 0 until code.size && moduleY in 0 until code.size && code.isDark(moduleX, moduleY)
            if (!isDark) {
                val index = y * rowLength + 1 + x / 8
                scanlines[index] = (scanlines[index].toInt() or (0x80 ushr (x and 7))).toByte()
            }
        }
    }
    val compressed = PrintBytes().also { PrintDeflater().deflate(scanlines, it) }.result()
    val header = ByteArray(13).apply {
        putInt(0, width)
        putInt(4, width)
        // A bit depth of 1; the zeros after it are grayscale, deflate, the adaptive filters, and no interlacing.
        this[8] = 1
    }
    return PrintBytes().apply {
        bytes(SIGNATURE)
        chunk("IHDR", header)
        chunk("IDAT", compressed)
        chunk("IEND", ByteArray(0))
    }.result()
}

/** One PNG chunk: its length, its type, its data, and the CRC-32 of the type and the data. */
private fun PrintBytes.chunk(type: String, data: ByteArray) {
    val typeAndData = type.encodeToByteArray() + data
    val length = ByteArray(4).apply { putInt(0, data.size) }
    bytes(length)
    bytes(typeAndData)
    bytes(ByteArray(4).apply { putInt(0, crc32(typeAndData)) })
}

private fun ByteArray.putInt(offset: Int, value: Int) {
    for (index in 0 until 4) this[offset + index] = (value ushr (24 - index * 8)).toByte()
}

private const val QUIET_ZONE = 4
private val SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
