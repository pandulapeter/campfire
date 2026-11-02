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

import com.pandulapeter.campfire.chordpro.syntax.ChordProLineScanner
import com.pandulapeter.campfire.chordpro.syntax.ChordProLines
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary

/**
 * The `%%transpose` lines a tune moves itself by. abc2svg adds such a line in a tune's header (after its `X:`, before its
 * `K:`) to the transposition the renderer asks for, so a transposition of the song is written into each tune as one,
 * leaving its notes and chord symbols as written, the way `{transpose}` moves a ChordPro song.
 */
public object AbcTranspositions {

    /**
     * The semitones the first tune of [tune] moves itself by on top of the reader's transposition: the sum of the
     * `%%transpose <n>` and `I:transpose <n>` lines between its first `X:` and its first `K:`, read as abc2svg reads
     * them (a whole number from −36 to 36, an optional `#` or `b` after it; anything else counts 0, as abc2svg ignores
     * it). 0 where there is no `X:`, which abc2svg renders nothing of. A line before `X:` is not counted: it replaces
     * the reader's transposition rather than adding to it.
     */
    public fun of(tune: String): Int {
        val lines = tune.lines()
        val start = lines.indexOfFirst { AbcLines.field(it)?.letter == TUNE_START }.takeIf { it >= 0 } ?: return 0
        return lines.drop(start + 1)
            .takeWhile { AbcLines.field(it)?.letter.let { letter -> letter != KEY && letter != TUNE_START } }
            .sumOf { line -> TranspositionLine.read(line)?.semitones ?: 0 }
    }

    /** Whether [line] is a transposition line of a tune, readable or not, which a transposition may add or remove. */
    internal fun isTranspositionLine(line: String) = TRANSPOSITION_DIRECTIVE.containsMatchIn(line)

    /**
     * [text] with each tune of each `abc` block moved by [semitones]: its readable transposition line rewritten to the
     * plain sum (removed where that is 0 and the line says nothing else, so a step up and one down give the file back
     * byte for byte), or a `%%transpose` line written after its `X:` where it has none. A sum abc2svg would ignore
     * (past ±36) is brought an octave toward 0. A block with no `X:` renders nothing, so it is left alone.
     */
    internal fun moved(text: String, semitones: Int): String {
        if (semitones == 0) return text
        val lines = ChordProLines.splitLines(text)
        val scanned = ChordProLineScanner.scan(lines).toList()
        val isTuneLine = BooleanArray(lines.size) { scanned[it].environment == ChordProVocabulary.ABC && scanned[it].directive == null }
        val result = mutableListOf<String>()
        var index = 0
        var hasChanged = false
        while (index < lines.size) {
            val line = lines[index]
            result += line
            index++
            if (!isTuneLine[index - 1] || AbcLines.field(line)?.letter != TUNE_START) continue
            var headerEnd = index
            while (headerEnd < lines.size && isTuneLine[headerEnd] && AbcLines.field(lines[headerEnd])?.letter.let { it != KEY && it != TUNE_START }) headerEnd++
            val existing = (index until headerEnd).firstOrNull { TranspositionLine.read(lines[it]) != null }
            hasChanged = true
            if (existing == null) {
                val sum = folded(semitones)
                if (sum != 0) result += "$NEW_LINE_PREFIX$sum"
                continue
            }
            (index until existing).forEach { result += lines[it] }
            val transposition = TranspositionLine.read(lines[existing])!!
            val sum = folded(transposition.semitones + semitones)
            if (sum != 0 || !transposition.isBare) result += transposition.withSemitones(sum)
            index = existing + 1
        }
        return if (hasChanged) ChordProLines.joinLines(result, text) else text
    }

    /** [semitones] within what abc2svg reads, an octave at a time toward 0. */
    private fun folded(semitones: Int): Int {
        var value = semitones
        while (value > MAX_SEMITONES) value -= OCTAVE
        while (value < -MAX_SEMITONES) value += OCTAVE
        return value
    }

    /** A transposition line abc2svg reads, in pieces, so that a rewrite keeps everything but its number. */
    private class TranspositionLine(
        private val prefix: String,
        private val hasPlus: Boolean,
        val semitones: Int,
        private val rest: String,
    ) {
        /** Whether the line says nothing but its number: no `#` or `b` after it, and no comment. */
        val isBare get() = rest.isBlank()

        fun withSemitones(value: Int) = prefix + (if (hasPlus && value >= 0) "+" else "") + value + rest

        companion object {
            fun read(line: String): TranspositionLine? {
                val match = READABLE_TRANSPOSITION.matchEntire(line) ?: return null
                val (prefix, sign, digits, rest) = match.destructured
                val semitones = (if (sign == "-") -1 else 1) * (digits.toIntOrNull() ?: return null)
                if (semitones !in -MAX_SEMITONES..MAX_SEMITONES) return null
                return TranspositionLine(prefix = prefix, hasPlus = sign == "+", semitones = semitones, rest = rest)
            }
        }
    }

    private const val TUNE_START = 'X'
    private const val KEY = 'K'
    private const val MAX_SEMITONES = 36
    private const val OCTAVE = 12
    private const val NEW_LINE_PREFIX = "%%transpose "
    private val TRANSPOSITION_DIRECTIVE = Regex("^\\s*(?:%%|I:)\\s*transpose\\b")

    /** `%%transpose 2`, `I:transpose +2#`, `%%transpose -3 % down`: the prefix, the sign, the number, then the rest. */
    private val READABLE_TRANSPOSITION = Regex("^(\\s*(?:%%|I:)\\s*transpose\\s+)([+-]?)([0-9]+)([#b]?\\s*(?:%.*)?)$")
}
