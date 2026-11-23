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
 * What a line reads as on its own, a copy of `tools/page-model/lines.py`'s rules that the trainer's golden file keeps
 * equal to it. The patterns use only what every platform's engine reads the same way: no `\s`, `\b`, or `\w` (ASCII on
 * some engines, Unicode in Python), and no ignore-case flag, a lowercase pattern matching [folded] text instead. Each takes
 * a stripped line with its format characters already removed.
 */
internal object LineRules {

    private val chord = Regex(
        """^[(\[]?(?:[A-H]|Do|Re|Ré|Mi|Fa|Sol|La|Si)(?:#|b|♯|♭|is|es)?""" +
            """(?:m|min|maj|M|dim|aug|sus|add|°|ø|\+|-|Δ|[0-9]|#|b|♯|♭|\(|\)|/[0-9])*""" +
            """(?:/(?:[A-H]|Do|Re|Mi|Fa|Sol|La|Si)(?:#|b|♯|♭)?)?[)\]]?$""",
    )
    private val furniture = Regex("""^(?:\||\|\||/|-|x[0-9]+|[0-9]+x|\(x?[0-9]+x?\)|\.+|:\|\||\|\|:|%|n\.c\.?|nc)$""")
    private val tab = Regex("""^$SPACE_CLASS*[a-gA-G]?[#b]?$SPACE_CLASS*[|:]?[-0-9hpbr/\\~x|*().^ ]{8,}\|?$SPACE_CLASS*$""")
    private val heading = Regex(
        """^$SPACE_CLASS*[\[(]?$SPACE_CLASS*(?:verse|chorus|bridge|intro|outro|pre-?chorus|refrain|coda|interlude|solo|""" +
            """instrumental|ending|tag|strofa|refren|ritornello|estribillo|estrofa|puente|couplet|refrão|ponte|strophe|vers|""" +
            """refrén|sloka|zwrotka|versszak|refrén|bridge|pont|verso|coro|ref\.?|r\.?|v\.?)$SPACE_CLASS*[0-9]*""" +
            """$SPACE_CLASS*[\])]?$SPACE_CLASS*:?$SPACE_CLASS*$""",
    )

    /**
     * `LABELLED`'s names, tried in its order. Python's `\b` after them is a Unicode word boundary, which is checked by hand
     * ([isWordCharacter]) since a JavaScript engine's is ASCII; every name is tried, as the alternation backtracks into a
     * longer one (`capo` failing on `capotraste:`).
     */
    private val labels = listOf(
        "key", "capo", "tempo", "bpm", "time", "tuning", "tonalidad", "tom", "tonalité", "tonart", "hangnem", "tonalitate",
        "tonacja", "tónina", "cejilla", "capotraste", "kapodaszter", "kapodastr", "afinación", "afinação", "accordatura",
        "stimmung", "hangolás", "words", "music", "lyrics", "composer", "written by", "letra", "música", "paroles", "musique",
        "text", "musik", "szöveg", "zene", "versuri", "muzica", "słowa", "muzyka", "hudba",
    )

    /** `is_chord_row`: every word a chord or a chord row's furniture (a bar, a repeat, N.C.), and at least one chord. */
    fun isChordRow(text: String): Boolean {
        val words = wordsOf(text)
        return words.any { chord.containsMatchIn(it) } &&
            words.all { chord.containsMatchIn(it) || furniture.containsMatchIn(it.folded()) }
    }

    fun isTab(text: String) = tab.containsMatchIn(text) && text.count { it == '-' } >= 6

    fun isHeading(line: LineText) = line.codePoints.size < 40 && heading.containsMatchIn(line.text.folded())

    fun isLabelled(text: String): Boolean {
        val folded = text.folded()
        val start = folded.skipSpaces(0)
        return labels.any { label ->
            if (!folded.startsWith(label, start)) return@any false
            val end = start + label.length
            if (end < folded.length && isWordCharacter(folded.codePointAt(end))) return@any false
            val sign = folded.skipSpaces(end)
            sign < folded.length && folded[sign] in ":=-"
        }
    }

    /** `https?://|www\.|\.com\b`: an address in the line. */
    fun hasAddress(text: String): Boolean {
        if ("http://" in text || "https://" in text || "www." in text) return true
        var index = text.indexOf(".com")
        while (index >= 0) {
            val end = index + 4
            if (end == text.length || !isWordCharacter(text.codePointAt(end))) return true
            index = text.indexOf(".com", index + 1)
        }
        return false
    }

    fun hasDigit(text: String) = text.any { it in '0'..'9' }

    /** Python's `str.split()`: runs of [isSpace] apart, none empty. */
    private fun wordsOf(text: String): List<String> {
        val words = mutableListOf<String>()
        var start = -1
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val width = if (codePoint >= 0x10000) 2 else 1
            if (isSpace(codePoint)) {
                if (start >= 0) words += text.substring(start, index)
                start = -1
            } else if (start < 0) {
                start = index
            }
            index += width
        }
        if (start >= 0) words += text.substring(start)
        return words
    }

    private fun String.skipSpaces(from: Int): Int {
        var index = from
        while (index < length && isSpace(this[index].code)) index++
        return index
    }
}
