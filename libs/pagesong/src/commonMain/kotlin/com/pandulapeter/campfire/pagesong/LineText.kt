/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.pagesong

/**
 * A line's text as the trainer reads it, in Python's terms: code points, not UTF-16 units, since every length and every
 * character the model reads is one (`len`, `ord`, `isalpha`), and an emoji counted twice would move every feature after it.
 */
internal class LineText(
    val codePoints: IntArray,
) {
    val text: String = codePoints.toText()
}

/** The text without its invisible format characters (Unicode category Cf), as `lines.py`'s `visible`. */
internal fun visibleTextOf(text: String): LineText {
    val codePoints = IntArray(text.length)
    var count = 0
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        index += if (codePoint >= SUPPLEMENTARY) 2 else 1
        if (!isFormat(codePoint)) codePoints[count++] = codePoint
    }
    return LineText(codePoints.copyOf(count))
}

/** The code point at [index], a lone surrogate standing for itself, as Python reads one from a decoded string. */
internal fun String.codePointAt(index: Int): Int {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) {
        return SUPPLEMENTARY + ((high.code - 0xD800) shl 10) + (this[index + 1].code - 0xDC00)
    }
    return high.code
}

private fun IntArray.toText() = buildString(size) {
    for (codePoint in this@toText) {
        if (codePoint >= SUPPLEMENTARY) {
            val offset = codePoint - SUPPLEMENTARY
            append((0xD800 + (offset shr 10)).toChar())
            append((0xDC00 + (offset and 0x3FF)).toChar())
        } else {
            append(codePoint.toChar())
        }
    }
}

/**
 * Category Cf as Python's Unicode 16 tables list it, written out rather than read from the platform, whose tables differ
 * by version and, past the BMP, are not reachable from common code at all.
 */
private val FORMAT_RANGES = intArrayOf(
    0xAD, 0xAD, 0x600, 0x605, 0x61C, 0x61C, 0x6DD, 0x6DD, 0x70F, 0x70F, 0x890, 0x891, 0x8E2, 0x8E2, 0x180E, 0x180E,
    0x200B, 0x200F, 0x202A, 0x202E, 0x2060, 0x2064, 0x2066, 0x206F, 0xFEFF, 0xFEFF, 0xFFF9, 0xFFFB, 0x110BD, 0x110BD,
    0x110CD, 0x110CD, 0x13430, 0x1343F, 0x1BCA0, 0x1BCA3, 0x1D173, 0x1D17A, 0xE0001, 0xE0001, 0xE0020, 0xE007F,
)

private fun isFormat(codePoint: Int): Boolean {
    for (index in FORMAT_RANGES.indices step 2) {
        if (codePoint < FORMAT_RANGES[index]) return false
        if (codePoint <= FORMAT_RANGES[index + 1]) return true
    }
    return false
}

/**
 * Python's `str.isspace`, which `strip`, `split`, and the regular expressions' `\s` all use: wider than ASCII (a no-break
 * space, the ideographic space, the information separators), and the same on every platform, unlike each regex engine's.
 */
internal fun isSpace(codePoint: Int) = when (codePoint) {
    in 0x09..0x0D, in 0x1C..0x20, 0x85, 0xA0, 0x1680, in 0x2000..0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000 -> true
    else -> false
}

/** [isSpace]'s characters as a regular expression class, standing in for Python's `\s`. */
internal const val SPACE_CLASS = "[\t\n\u000B\u000C\r\u001C-\u001F \u0085   -     　]"

/**
 * Python's `str.isalpha`. Past the BMP, where common code cannot ask for a category, only the CJK ideograph extensions
 * count as letters, which every app language's text and an emoji are read right by; a historic script's letter is not.
 */
internal fun isLetter(codePoint: Int) = when {
    codePoint < SUPPLEMENTARY -> codePoint.toChar().isLetter()
    else -> codePoint in 0x20000..0x323AF
}

/** Python's `\w`: a letter, a number of any kind, or an underscore. */
internal fun isWordCharacter(codePoint: Int) = when {
    codePoint == '_'.code -> true
    codePoint < SUPPLEMENTARY -> codePoint.toChar().let {
        it.isLetter() ||
            it.category == CharCategory.DECIMAL_DIGIT_NUMBER ||
            it.category == CharCategory.LETTER_NUMBER ||
            it.category == CharCategory.OTHER_NUMBER
    }
    else -> isLetter(codePoint)
}

/**
 * The text as Python's case-insensitive matching sees it, one character for one: each character's lowercase of its
 * uppercase, which also matches a dotless ı, a dotted İ, and the Kelvin sign to their ASCII letters as Python does. A
 * lowercase pattern matched against this needs no ignore-case flag, whose reach past ASCII differs between engines.
 */
internal fun String.folded() = buildString(length) {
    for (character in this@folded) append(character.uppercaseChar().lowercaseChar())
}

private const val SUPPLEMENTARY = 0x10000
