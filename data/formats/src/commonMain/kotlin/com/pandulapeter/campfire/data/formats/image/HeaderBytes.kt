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
 * The first [ImageInfo.MAX_HEADER_BYTES] of a file, read without bounds exceptions: every read past [size] (or before
 * the start) is -1, so a header reader only has to check for a negative value to refuse a truncated or lying file.
 */
internal class HeaderBytes(private val bytes: ByteArray) {

    val size = minOf(bytes.size, ImageInfo.MAX_HEADER_BYTES)

    fun u8(offset: Int): Int = if (offset in 0 until size) bytes[offset].toInt() and 0xFF else -1

    fun u16be(offset: Int): Int = combine(u8(offset), u8(offset + 1))

    fun u16le(offset: Int): Int = combine(u8(offset + 1), u8(offset))

    fun u24le(offset: Int): Int = combine(u16le(offset + 1), u8(offset), shift = 8)

    fun u32be(offset: Int): Long = combineLong(u16be(offset), u16be(offset + 2), shift = 16)

    fun u32le(offset: Int): Long = combineLong(u16le(offset + 2), u16le(offset), shift = 16)

    /** Whether the bytes at [offset] are [ascii]'s characters. */
    fun startsWith(ascii: String, offset: Int = 0): Boolean = ascii.indices.all { u8(offset + it) == ascii[it].code }

    private fun combine(high: Int, low: Int, shift: Int = 8): Int = if (high < 0 || low < 0) -1 else (high shl shift) or low

    private fun combineLong(high: Int, low: Int, shift: Int): Long = if (high < 0 || low < 0) -1L else (high.toLong() shl shift) or low.toLong()
}
