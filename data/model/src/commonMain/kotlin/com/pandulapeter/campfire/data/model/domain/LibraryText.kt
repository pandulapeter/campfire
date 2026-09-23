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
 * writes. A file that is not valid UTF-8 was written in one of the old 8-bit code pages, which it does not name, and
 * is read as Windows-1250 where its bytes clearly say Hungarian or Polish ([isCentralEuropean]) and as
 * Windows-1252, which is what every other Western text file is, otherwise. Either is better than a row of
 * replacement characters, and the right one of the two is what keeps the next save from writing a misreading back
 * over the user's accents. Shared by the storage layer and the import, so that a file reads the same whether it was
 * dropped into the library folder or imported. A UTF-8 file with a few stray code-page bytes in it keeps its UTF-8 and
 * reads only those bytes through the code page ([decodeUtf8OrCodePage]).
 *
 * Byte order marks at the start are stripped - a file that went through two tools that each added one has two -
 * because editors on Windows like to prefix UTF-8 files with one and it is not part of the content.
 */
fun ByteArray.decodeLibraryText(): String {
    val byteOrder = utf16ByteOrder()
    return (if (byteOrder == null) decodeUtf8OrCodePage() else decodeUtf16(byteOrder)).trimStart(BYTE_ORDER_MARK)
}

/**
 * UTF-8 where the bytes are UTF-8. A file that is not is read through a code page, the whole of it where it is a
 * code-page file ([decodeWindows1252], or Windows-1250 where [isCentralEuropean]) - but a UTF-8 file with a few stray
 * bytes in it, a line pasted from an old document into a songbook, keeps every character that is UTF-8 and reads only
 * the stray bytes through the code page. Decided whole, one such byte would turn every accented letter of two hundred
 * songs into two Latin-1 ones, which the first save then writes back as the song. The file counts as UTF-8 with stray
 * bytes when it has at least as many well-formed multi-byte characters as stray bytes: a real code-page file almost
 * never holds a well-formed multi-byte sequence, and where it does by accident, its accented letters outnumber it.
 */
private fun ByteArray.decodeUtf8OrCodePage(): String {
    var index = 0
    var multiByte = 0
    var invalid = 0
    while (index < size) {
        val length = utf8SequenceLength(index)
        if (length == 0) {
            invalid++
            index++
        } else {
            if (length > 1) multiByte++
            index += length
        }
    }
    return when {
        invalid == 0 -> decodeToString()
        multiByte > 0 && invalid <= multiByte -> decodeMixed(isCentralEuropean = withUtf8AsCodePage().isCentralEuropean())
        isCentralEuropean() -> decodeWindows1250()
        else -> decodeWindows1252()
    }
}

/** The length of the well-formed UTF-8 sequence that starts at [index], or 0 where none does (Unicode, Table 3-7). */
private fun ByteArray.utf8SequenceLength(index: Int): Int {
    fun byteAt(offset: Int) = if (index + offset < size) this[index + offset].toInt() and 0xFF else -1
    fun isContinuation(offset: Int, range: IntRange = 0x80..0xBF) = byteAt(offset) in range
    return when (byteAt(0)) {
        in 0x00..0x7F -> 1
        in 0xC2..0xDF -> if (isContinuation(1)) 2 else 0
        0xE0 -> if (isContinuation(1, 0xA0..0xBF) && isContinuation(2)) 3 else 0
        in 0xE1..0xEC, 0xEE, 0xEF -> if (isContinuation(1) && isContinuation(2)) 3 else 0
        0xED -> if (isContinuation(1, 0x80..0x9F) && isContinuation(2)) 3 else 0
        0xF0 -> if (isContinuation(1, 0x90..0xBF) && isContinuation(2) && isContinuation(3)) 4 else 0
        in 0xF1..0xF3 -> if (isContinuation(1) && isContinuation(2) && isContinuation(3)) 4 else 0
        0xF4 -> if (isContinuation(1, 0x80..0x8F) && isContinuation(2) && isContinuation(3)) 4 else 0
        else -> 0
    }
}

/** UTF-8 with the bytes that start no sequence read through Windows-1250 or -1252, decoded a run at a time. */
private fun ByteArray.decodeMixed(isCentralEuropean: Boolean) = buildString(size) {
    var runStart = 0
    var index = 0
    while (index < size) {
        val length = utf8SequenceLength(index)
        if (length == 0) {
            append(this@decodeMixed.decodeToString(runStart, index))
            append(codePageCharacter(this@decodeMixed[index].toInt() and 0xFF, isCentralEuropean))
            index++
            runStart = index
        } else {
            index += length
        }
    }
    append(this@decodeMixed.decodeToString(runStart, size))
}

/**
 * The file as [isCentralEuropean] should see it once its UTF-8 is set aside, the stray bytes left where they were.
 * A character both code pages put on the same byte - the Latin-1 letters Windows-1250 kept, `á`, `í`, `ö` and the
 * rest - becomes that byte, so that it is evidence about the language the way it would be in a code-page file; every
 * other multi-byte character becomes an `a`, a letter, which is what almost all of them are and all the Polish rule
 * needs to know about the one before a stray byte.
 */
private fun ByteArray.withUtf8AsCodePage(): ByteArray {
    val bytes = ByteArray(size)
    var count = 0
    var index = 0
    while (index < size) {
        val length = utf8SequenceLength(index)
        if (length > 1) {
            // Only a two-byte sequence can be below U+0100.
            val codePoint = if (length == 2) ((this[index].toInt() and 0x1F) shl 6) or (this[index + 1].toInt() and 0x3F) else -1
            val isShared = codePoint in 0xA0..0xFF && WINDOWS_1250_CHARACTERS[codePoint - 0x80].code == codePoint
            bytes[count++] = if (isShared) codePoint.toByte() else 'a'.code.toByte()
            index += length
        } else {
            bytes[count++] = this[index]
            index++
        }
    }
    return bytes.copyOf(count)
}

private fun codePageCharacter(value: Int, isCentralEuropean: Boolean) = when {
    value < 0x80 -> value.toChar()
    isCentralEuropean -> WINDOWS_1250_CHARACTERS[value - 0x80]
    value in WINDOWS_1252_RANGE -> WINDOWS_1252_CHARACTERS[value - WINDOWS_1252_RANGE.first]
    else -> value.toChar()
}

/**
 * A table decode rather than a platform charset, which the common standard library does not have: every byte maps to
 * the code point of the same number (Latin-1), except for 0x80-0x9F, where Windows-1252 put its typographic
 * characters instead of control codes.
 */
private fun ByteArray.decodeWindows1252() = buildString(size) {
    for (byte in this@decodeWindows1252) append(codePageCharacter(byte.toInt() and 0xFF, isCentralEuropean = false))
}

/**
 * Every byte of 0x80-0xFF through Windows-1250's table of the 128 characters it puts there. Windows-1250 moves too
 * many of Latin-1's letters to be written as exceptions to it the way [decodeWindows1252] is.
 */
private fun ByteArray.decodeWindows1250() = buildString(size) {
    for (byte in this@decodeWindows1250) append(codePageCharacter(byte.toInt() and 0xFF, isCentralEuropean = true))
}

/**
 * Whether a file that is not UTF-8 is Windows-1250 rather than 1252. The two agree on most accented letters and
 * nothing in the bytes says which one wrote them, so only what cannot be anything else counts: the Hungarian
 * double acute (`ő`, `ű`) next to an `á` or `í` somewhere in the text, or a Polish letter (`ą`, `ł`, `ż`, `ź`)
 * right after another letter, where 1252 has `¹`, `³`, `¿` and `Ÿ`, which never follow a letter in Western text.
 * One letter that neither language uses and the Western ones do (`à`, `ã`, `è`, `ä`, `ç`…) settles it for 1252:
 * it is what tells a Portuguese `õ`, a French `û` or an Estonian `õ` from a Hungarian one. Czech, Slovak and
 * Romanian stay on 1252: their `č`, `ř`, `ě` and `ă` share their bytes with the Western `è`, `ø`, `ì` and `ã`, and
 * telling them apart would mean guessing the language, wrongly for some of the Western files that read right.
 */
private fun ByteArray.isCentralEuropean(): Boolean {
    var hasDoubleAcute = false
    var hasAcuteAOrI = false
    var hasPolishLetter = false
    for (index in indices) {
        when (this[index].toInt() and 0xFF) {
            0xE0, 0xE3, 0xE4, 0xE5, 0xE7, 0xE8, 0xEC, 0xF2, 0xF8, 0xF9,
            0xC0, 0xC3, 0xC4, 0xC5, 0xC7, 0xC8, 0xCC, 0xD2, 0xD8, 0xD9 -> return false
            0xD5, 0xDB, 0xF5, 0xFB -> hasDoubleAcute = true
            0xC1, 0xCD, 0xE1, 0xED -> hasAcuteAOrI = true
            0x8F, 0x9F, 0xA3, 0xA5, 0xAF, 0xB3, 0xB9, 0xBF -> if (index > 0 && isLetterByte(index - 1)) hasPolishLetter = true
        }
    }
    return (hasDoubleAcute && hasAcuteAOrI) || hasPolishLetter
}

/** An ASCII letter, or a byte both code pages put a letter on (0xC0 and up, apart from × and ÷). */
private fun ByteArray.isLetterByte(index: Int): Boolean {
    val value = this[index].toInt() and 0xFF
    return value in 0x41..0x5A || value in 0x61..0x7A || (value >= 0xC0 && value != 0xD7 && value != 0xF7)
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

/**
 * 0x80-0xFF in Windows-1250, the Central European code page. The five bytes it leaves undefined become the
 * replacement character.
 */
private const val WINDOWS_1250_CHARACTERS =
    "€\uFFFD‚\uFFFD„…†‡\uFFFD‰Š‹ŚŤŽŹ" +
        "\uFFFD‘’“”•–—\uFFFD™š›śťžź" +
        "\u00A0ˇ˘Ł¤Ą¦§¨©Ş«¬\u00AD®Ż" +
        "°±˛ł´µ¶·¸ąş»Ľ˝ľż" +
        "ŔÁÂĂÄĹĆÇČÉĘËĚÍÎĎ" +
        "ĐŃŇÓÔŐÖ×ŘŮÚŰÜÝŢß" +
        "ŕáâăäĺćçčéęëěíîď" +
        "đńňóôőö÷řůúűüýţ˙"
