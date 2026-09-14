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
 * A byte order mark is stripped, because editors on Windows like to prefix UTF-8 files with one and it is not part of
 * the content.
 */
fun ByteArray.decodeLibraryText(): String = try {
    decodeToString(throwOnInvalidSequence = true)
} catch (_: CharacterCodingException) {
    decodeWindows1252()
}.removePrefix(BYTE_ORDER_MARK)

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

private const val BYTE_ORDER_MARK = "\uFEFF"

private val WINDOWS_1252_RANGE = 0x80..0x9F

/** 0x80-0x9F in Windows-1252; the five bytes the code page leaves undefined become the replacement character. */
private const val WINDOWS_1252_CHARACTERS =
    "€�‚ƒ„…†‡ˆ‰Š‹Œ�Ž�" +
        "�‘’“”•–—˜™š›œ�žŸ"
