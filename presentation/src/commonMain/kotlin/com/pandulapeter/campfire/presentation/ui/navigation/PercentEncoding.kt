/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

/**
 * `encodeURIComponent`, and a tilde too, which it leaves alone: GitHub Pages hands a deep address to the app through
 * its 404 page, which carries `&` in the query as `~and~`, and a name that held those five characters would come
 * back as an ampersand. Every byte of the text's UTF-8 is kept as it is where it is one of the characters
 * `encodeURIComponent` leaves alone, and written as `%XX` in uppercase hexadecimal otherwise, as it writes them. A lone
 * surrogate, on which `encodeURIComponent` throws, is replaced by the encoding instead; no library file name holds
 * one, since names are NFC text.
 */
internal fun encodePathSegment(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and 0xFF
        val char = code.toChar()
        if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in UNESCAPED) {
            append(char)
        } else {
            append('%')
            append(HEX_DIGITS[code shr 4])
            append(HEX_DIGITS[code and 0xF])
        }
    }
}

/**
 * `decodeURIComponent`: null for a segment that is not valid percent-encoding, which is an address the app did not
 * write - a `%` without two hexadecimal digits after it, or escapes that are not UTF-8, an encoded surrogate and an
 * overlong form included, which is exactly where `decodeURIComponent` throws. A `+` is a plus sign, not a space.
 */
internal fun decodePathSegment(value: String): String? {
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        if (value[index] == '%') {
            val high = value.getOrNull(index + 1)?.hexDigitValue() ?: return null
            val low = value.getOrNull(index + 2)?.hexDigitValue() ?: return null
            bytes += ((high shl 4) or low).toByte()
            index += 3
        } else {
            val end = value.indexOf('%', index).takeIf { it >= 0 } ?: value.length
            value.substring(index, end).encodeToByteArray().forEach { bytes += it }
            index = end
        }
    }
    return try {
        bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
    } catch (_: CharacterCodingException) {
        null
    }
}

/** The value of an ASCII hexadecimal digit of either case, or null for anything else, Unicode digits included. */
private fun Char.hexDigitValue() = when (this) {
    in '0'..'9' -> this - '0'
    in 'a'..'f' -> this - 'a' + 10
    in 'A'..'F' -> this - 'A' + 10
    else -> null
}

/** The characters besides letters and digits `encodeURIComponent` leaves alone, the tilde aside. */
private const val UNESCAPED = "-_.!*'()"

private const val HEX_DIGITS = "0123456789ABCDEF"
