/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.syntax

/**
 * The text a ChordPro `<sym name/>` stands for, and the character references markup is written with, for
 * [ChordProMarkup].
 *
 * ChordPro draws its symbols from a font of its own, whose code points mean nothing elsewhere, so a symbol is the plain
 * text a chart writes it as instead: the accidentals the bundled fonts draw, a strum's arrow whatever its accent or
 * mute, a circled letter as the letter, and bar lines as the bars a chord row writes.
 */
internal object MarkupSymbols {

    private val symbols = mapOf(
        "flat" to "♭",
        "natural" to "♮",
        "sharp" to "♯",
        "arrow-mute" to "×",
        "delta" to "Δ",
        "bar" to "|",
        "start-bar" to "|",
        "thick-bar" to "|",
        "double-bar" to "||",
        "double-thick-bar" to "||",
        "end-bar" to "||",
        "repeat-start" to "|:",
        "repeat-end" to ":|",
        "repeat-end-start" to ":|:",
        "repeat-colon" to ":",
        "repeat1" to "1.",
        "repeat2" to "2.",
    )

    private val namedEntities = mapOf(
        "lt" to "<",
        "gt" to ">",
        "amp" to "&",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
    )
    private const val LONGEST_ENTITY = 10

    /** The text of the symbol [name] (any letter case), or null for a name ChordPro does not define. */
    fun symbolOf(name: String): String? {
        val lowercase = name.lowercase()
        return when {
            lowercase.startsWith("arrow-up") -> "↑"
            lowercase.startsWith("arrow-down") -> "↓"
            lowercase.startsWith("circle-") && lowercase.length == "circle-".length + 1 -> lowercase.last().uppercase()
            else -> symbols[lowercase]
        }
    }

    /**
     * The character reference at [start] (`&amp;`, `&#233;`, `&#xE9;`) with its length, or null where there is none:
     * Pango markup writes a literal `<` and `&` this way, so a song written for it shows `&lt;3` as `<3`. Only XML's own
     * names and `&nbsp;` are known, and the closing `;` is required, so a lyric's "Rock & Roll" stays as it is.
     */
    fun entityAt(text: String, start: Int): Pair<String, Int>? {
        val end = text.indexOf(';', start + 1).takeIf { it > start + 1 && it - start <= LONGEST_ENTITY } ?: return null
        val name = text.substring(start + 1, end)
        val decoded = if (name.startsWith('#')) {
            val codePoint = if (name.startsWith("#x") || name.startsWith("#X")) name.drop(2).toIntOrNull(16) else name.drop(1).toIntOrNull()
            codePoint?.let(::textOf)
        } else {
            namedEntities[name]
        }
        return decoded?.let { it to end + 1 - start }
    }

    /** The text of [codePoint], or null for one that is no character (a surrogate, a control, past Unicode). */
    private fun textOf(codePoint: Int): String? = when {
        codePoint < 0x20 && codePoint != '\t'.code -> null
        codePoint in 0xD800..0xDFFF || codePoint > 0x10FFFF -> null
        codePoint < 0x10000 -> Char(codePoint).toString()
        else -> (codePoint - 0x10000).let { charArrayOf(Char(0xD800 + (it shr 10)), Char(0xDC00 + (it and 0x3FF))).concatToString() }
    }
}
