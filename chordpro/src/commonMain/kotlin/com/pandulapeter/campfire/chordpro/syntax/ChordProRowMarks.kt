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

import com.pandulapeter.campfire.chordpro.model.ChordProLine

/**
 * The marks a chord row carries between and after its chords: repeat counts (`x4`, `(2x)`, `×3`), bar lines and repeat
 * signs (`|`, `|:`, `%`, `/`), and a no chord written as text (`N.C.`). ChordPro has no syntax for them, so a line of
 * chords ending in `x4` would be a lyric line saying "x4" under its chords; the parser reads them as
 * [ChordProLine.Lyrics.Chord.isMark] chords instead, and the highlighter colours them as it does annotations.
 *
 * Only a chord row has marks: a line with a bracket in it whose every word outside the brackets is one. A sung line
 * that ends in `x2` keeps it as lyrics, since the count belongs to its words and lyrics-only mode needs it, and so does
 * a line of nothing but `x4`, which reads right as it is ("the line above, four times"). Words (`twice`, `(let ring)`)
 * are never marks: a row of chords with one may be a sung backing line, and `[*twice]` says otherwise.
 */
internal object ChordProRowMarks {

    /** One repeat count, `x2`, `(2x)` or `×2`, as a mark of a chord row and after a heading the converter reads. */
    val repeatCount = Regex("\\(?(?:[0-9]+[x\u00d7]|[x\u00d7][0-9]+)\\)?", RegexOption.IGNORE_CASE)

    private val signs = setOf("|", "||", "|:", ":|", ":|:", "%", "%%", "/", "-", "N.C.", "NC")

    fun isMark(word: String) = word in signs || repeatCount.matches(word)

    /**
     * The ranges of [text] that are marks, where [text] is the text of a line with chords at [chordPositions] that makes
     * it a chord row, or null where it is not one: no chords, a word that is no mark, or a chord standing inside a word
     * (`[G]x[C]4`), which makes it a word of the lyrics. A line with no words is a chord row without marks.
     */
    fun marksOf(text: String, chordPositions: List<Int>): List<IntRange>? {
        if (chordPositions.isEmpty()) return null
        val marks = mutableListOf<IntRange>()
        var index = 0
        while (index < text.length) {
            if (text[index].isWhitespace()) {
                index++
                continue
            }
            val start = index
            while (index < text.length && !text[index].isWhitespace()) index++
            if (!isMark(text.substring(start, index)) || chordPositions.any { it in start + 1 until index }) return null
            marks += start until index
        }
        return marks
    }

    /**
     * The ranges of [line], as it is written, that the parser reads as marks, read by the parser's own steps (the
     * brackets taken out, then the markup), for the highlighter to colour. Empty where the line is no chord row.
     */
    fun marksIn(line: String): List<IntRange> {
        if (!ChordProDirectives.hasBrackets(line)) return emptyList()
        val text = StringBuilder()
        val writtenAt = mutableListOf<Int>()
        val chordPositions = mutableListOf<Int>()
        var consumedUntil = 0
        fun keep(until: Int) {
            for (index in consumedUntil until until) {
                writtenAt += index
                text.append(line[index])
            }
        }
        ChordProDirectives.brackets(line).forEach { bracket ->
            keep(bracket.range.first)
            if (bracket.content.isNotBlank()) chordPositions += text.length
            consumedUntil = bracket.range.last + 1
        }
        keep(line.length)
        val stripped = ChordProMarkup.strip(text.toString())
        val marks = marksOf(stripped.text, chordPositions.map { stripped.offsetOf(it) }) ?: return emptyList()
        // A tag maps to the character after it, so the last character to land on an offset is the one written there.
        val strippedAt = IntArray(stripped.text.length)
        text.indices.forEach { index -> stripped.offsetOf(index).takeIf { it < strippedAt.size }?.let { strippedAt[it] = index } }
        return marks.map { writtenAt[strippedAt[it.first]]..writtenAt[strippedAt[it.last]] }
    }
}
