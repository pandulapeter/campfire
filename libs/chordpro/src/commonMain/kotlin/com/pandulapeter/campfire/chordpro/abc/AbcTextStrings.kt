/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.abc

/**
 * Decodes the backslash sequences ABC writes accented letters with in a field's text (`Caf\'e`, `\"u`, `\ss`,
 * `\u00e9`), since a title or a verse keeps them in a file written in ASCII. A mark over a letter this table doesn't
 * precompose is written as the letter and a combining mark, which the caller's NFC normalization folds where Unicode
 * can. Any other backslash is kept as written.
 */
internal object AbcTextStrings {

    private const val ESCAPE = '\\'
    private const val UNICODE_DIGITS = 4
    private const val LITERALS = "\\%&"

    private val combiningMarks = mapOf(
        '`' to '\u0300', '\'' to '\u0301', '^' to '\u0302', '~' to '\u0303', '=' to '\u0304', 'u' to '\u0306',
        '.' to '\u0307', '"' to '\u0308', 'o' to '\u030A', 'H' to '\u030B', 'v' to '\u030C', 'c' to '\u0327',
        ',' to '\u0327', ';' to '\u0328',
    )
    private val ligatures = mapOf(
        "ss" to "ß", "AE" to "Æ", "ae" to "æ", "OE" to "Œ", "oe" to "œ", "AA" to "Å", "aa" to "å",
        "DH" to "Ð", "dh" to "ð", "TH" to "Þ", "th" to "þ", "/O" to "Ø", "/o" to "ø",
    )

    /** Each mark's letters, unaccented then precomposed, for the letters Western European and Hungarian names use. */
    private val precomposed = mapOf(
        '\u0300' to ("AEIOUaeiou" to "ÀÈÌÒÙàèìòù"),
        '\u0301' to ("AEIOUYaeiouyCcNnSsZz" to "ÁÉÍÓÚÝáéíóúýĆćŃńŚśŹź"),
        '\u0302' to ("AEIOUaeiou" to "ÂÊÎÔÛâêîôû"),
        '\u0303' to ("ANOano" to "ÃÑÕãñõ"),
        '\u0308' to ("AEIOUaeiouy" to "ÄËÏÖÜäëïöüÿ"),
        '\u030A' to ("AaUu" to "ÅåŮů"),
        '\u030B' to ("OoUu" to "ŐőŰű"),
        '\u030C' to ("CcSsZzRrEeNn" to "ČčŠšŽžŘřĚěŇň"),
        '\u0327' to ("CcSs" to "ÇçŞş"),
    )

    fun decode(text: String): String {
        if (ESCAPE !in text) return text
        val decoded = StringBuilder()
        var index = 0
        while (index < text.length) {
            val consumed = if (text[index] == ESCAPE) sequenceAt(text, index + 1, decoded) else 0
            if (consumed == 0) decoded.append(text[index])
            index += 1 + consumed
        }
        return decoded.toString()
    }

    /** Appends the character the sequence after a backslash at [start] spells and returns its length, 0 for none. */
    private fun sequenceAt(text: String, start: Int, decoded: StringBuilder): Int {
        val pair = text.substring(start, (start + 2).coerceAtMost(text.length))
        ligatures[pair]?.let {
            decoded.append(it)
            return 2
        }
        val mark = text.getOrNull(start) ?: return 0
        if (mark == 'u') unicodeAt(text, start + 1)?.let {
            decoded.append(it)
            return 1 + UNICODE_DIGITS
        }
        if (mark in LITERALS) {
            decoded.append(mark)
            return 1
        }
        val combining = combiningMarks[mark] ?: return 0
        val letter = text.getOrNull(start + 1)?.takeIf { it.isLetter() } ?: return 0
        val (plain, accented) = precomposed[combining] ?: ("" to "")
        val position = plain.indexOf(letter)
        if (position >= 0) decoded.append(accented[position]) else decoded.append(letter).append(combining)
        return 2
    }

    private fun unicodeAt(text: String, start: Int): Char? {
        val digits = text.substring(start, (start + UNICODE_DIGITS).coerceAtMost(text.length))
        return digits.takeIf { it.length == UNICODE_DIGITS && it.all { digit -> digit in '0'..'9' || digit.lowercaseChar() in 'a'..'f' } }?.toInt(16)?.toChar()
    }
}
