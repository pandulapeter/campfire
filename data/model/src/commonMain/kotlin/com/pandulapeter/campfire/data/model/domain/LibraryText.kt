/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/**
 * The text of a library file, or of a file on its way into the library. UTF-8 first, since that is what Campfire
 * writes; a file that is not valid UTF-8 is read as Windows-1252, which is what every other Western text file is,
 * rather than as a row of replacement characters that the next save would write back over the user's accents. Shared
 * by the storage layer and the import, so that a file reads the same whether it was dropped into the library folder
 * or imported.
 *
 * Byte order marks at the start are stripped - a file that went through two tools that each added one has two -
 * because editors on Windows like to prefix UTF-8 files with one and it is not part of the content.
 */
fun ByteArray.decodeLibraryText(): String {
    val byteOrder = utf16ByteOrder()
    return if (byteOrder == null) {
        try {
            decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            decodeWindows1252()
        }
    } else {
        decodeUtf16(byteOrder)
    }.trimStart(BYTE_ORDER_MARK)
}

/**
 * A table decode rather than a platform charset, which the common standard library does not have: every byte maps to
 * the code point of the same number (Latin-1), except for 0x80-0x9F, where Windows-1252 put its typographic
 * characters instead of control codes.
 */
private fun ByteArray.decodeWindows1252() = buildString(size) {
    for (byte in this@decodeWindows1252) {
        val value = byte.toInt() and 0xFF
        append(if (value in WINDOWS_1252_RANGE) WINDOWS_1252_CHARACTERS[value - WINDOWS_1252_RANGE.first] else value.toChar())
    }
}

private enum class Utf16ByteOrder { LITTLE_ENDIAN, BIG_ENDIAN }

/** Recognises a byte-order mark or the one-sided NULs UTF-16 text carries. */
private fun ByteArray.utf16ByteOrder(): Utf16ByteOrder? {
    if (size < 2) return null
    val first = this[0].toInt() and 0xFF
    val second = this[1].toInt() and 0xFF
    if (first == 0xFF && second == 0xFE) return Utf16ByteOrder.LITTLE_ENDIAN
    if (first == 0xFE && second == 0xFF) return Utf16ByteOrder.BIG_ENDIAN
    val sampledUnits = minOf(size, UTF_16_SAMPLE_SIZE) / 2
    var leadingZeros = 0
    var trailingZeros = 0
    for (unit in 0 until sampledUnits) {
        if (this[unit * 2] == ZERO_BYTE) leadingZeros++
        if (this[unit * 2 + 1] == ZERO_BYTE) trailingZeros++
    }
    return when {
        trailingZeros * UTF_16_MINIMUM_ZERO_SHARE >= sampledUnits && leadingZeros == 0 -> Utf16ByteOrder.LITTLE_ENDIAN
        leadingZeros * UTF_16_MINIMUM_ZERO_SHARE >= sampledUnits && trailingZeros == 0 -> Utf16ByteOrder.BIG_ENDIAN
        else -> null
    }
}

/** Decodes UTF-16 without platform charsets and replaces malformed surrogate sequences consistently. */
private fun ByteArray.decodeUtf16(byteOrder: Utf16ByteOrder): String {
    val units = CharArray(size / 2) { index ->
        val first = this[index * 2].toInt() and 0xFF
        val second = this[index * 2 + 1].toInt() and 0xFF
        (if (byteOrder == Utf16ByteOrder.LITTLE_ENDIAN) (second shl 8) or first else (first shl 8) or second).toChar()
    }
    val hasOddByte = size % 2 != 0
    return buildString(units.size + 1) {
        var index = 0
        while (index < units.size) {
            val unit = units[index]
            val next = units.getOrNull(index + 1)
            when {
                unit.isHighSurrogate() && next != null && next.isLowSurrogate() -> { append(unit).append(next); index++ }
                unit.isSurrogate() -> append(REPLACEMENT_CHARACTER)
                else -> append(unit)
            }
            index++
        }
        if (hasOddByte) append(REPLACEMENT_CHARACTER)
    }
}

private const val BYTE_ORDER_MARK = '\uFEFF'
private const val REPLACEMENT_CHARACTER = '\uFFFD'
private const val ZERO_BYTE: Byte = 0
private const val UTF_16_SAMPLE_SIZE = 256
private const val UTF_16_MINIMUM_ZERO_SHARE = 8

private val WINDOWS_1252_RANGE = 0x80..0x9F

/** 0x80-0x9F in Windows-1252; the five bytes the code page leaves undefined become the replacement character. */
private const val WINDOWS_1252_CHARACTERS =
    "€�‚ƒ„…†‡ˆ‰Š‹Œ�Ž�" +
        "�‘’“”•–—˜™š›œ�žŸ"
