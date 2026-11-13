/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

/**
 * The character references of a copied HTML fragment. Numeric ones are read whole; of the named ones only those a
 * browser writes into a copy of lyrics are known (it writes most characters as themselves, in UTF-8), and any other is
 * left as it was written, since a lyric may well hold an ampersand.
 */
internal object ChordSheetHtmlEntities {

    fun decode(text: String): String = if ('&' !in text) text else REFERENCE.replace(text) { match ->
        val name = match.groupValues[1]
        val code = when {
            name.startsWith("#x", ignoreCase = true) -> name.drop(2).toIntOrNull(16)
            name.startsWith("#") -> name.drop(1).toIntOrNull()
            else -> null
        }
        when {
            code != null -> if (code in 1..0x10FFFF && code !in 0xD800..0xDFFF) codePointText(code) else match.value
            else -> NAMED[name] ?: match.value
        }
    }

    private fun codePointText(code: Int): String = if (code < 0x10000) {
        code.toChar().toString()
    } else {
        val offset = code - 0x10000
        charArrayOf((0xD800 + (offset shr 10)).toChar(), (0xDC00 + (offset and 0x3FF)).toChar()).concatToString()
    }

    private val REFERENCE = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z][A-Za-z0-9]{1,31});")

    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to "\u00a0", "shy" to "",
        "lsquo" to "\u2018", "rsquo" to "\u2019", "ldquo" to "\u201c", "rdquo" to "\u201d", "laquo" to "\u00ab",
        "raquo" to "\u00bb", "ndash" to "\u2013", "mdash" to "\u2014", "hellip" to "\u2026", "middot" to "\u00b7",
        "sharp" to "\u266f", "flat" to "\u266d", "natur" to "\u266e", "deg" to "\u00b0", "times" to "\u00d7",
        "iexcl" to "\u00a1", "iquest" to "\u00bf", "szlig" to "\u00df", "oelig" to "\u0153", "OElig" to "\u0152",
        "aelig" to "\u00e6", "AElig" to "\u00c6", "oslash" to "\u00f8", "Oslash" to "\u00d8",
    ) + accentedLetters()

    /** `&eacute;`, `&Uuml;` and their kind, for the letters of the Latin-1 block that have them. */
    private fun accentedLetters() = mapOf(
        "grave" to "AEIOU\u00c0\u00c8\u00cc\u00d2\u00d9",
        "acute" to "AEIOUY\u00c1\u00c9\u00cd\u00d3\u00da\u00dd",
        "circ" to "AEIOU\u00c2\u00ca\u00ce\u00d4\u00db",
        "uml" to "AEIOUY\u00c4\u00cb\u00cf\u00d6\u00dc\u0178",
        "tilde" to "ANO\u00c3\u00d1\u00d5",
        "ring" to "A\u00c5",
        "cedil" to "C\u00c7",
    ).flatMap { (accent, table) ->
        val count = table.length / 2
        (0 until count).flatMap { index ->
            val base = table[index]
            val upper = table[count + index]
            listOf("$base$accent" to upper.toString(), "${base.lowercaseChar()}$accent" to upper.lowercase())
        }
    }.toMap()
}
