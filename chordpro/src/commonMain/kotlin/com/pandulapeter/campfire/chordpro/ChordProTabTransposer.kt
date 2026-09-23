/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

/**
 * Moves the contents of one tab environment by a number of semitones, on the raw lines, for [ChordProTransposer].
 *
 * A tab is transposed on the fingerboard and not on the staff: every fret number moves by the same amount, the tuning
 * stays what it was. Since a fret number is not always as wide as the one it replaces, the surrounding dashes are
 * absorbed or padded so that the columns of the whole environment keep lining up.
 */
internal object ChordProTabTransposer {

    /**
     * Transposes the lines of one tab environment.
     *
     * Lines that look like tablature get their fret numbers moved; a line that holds nothing but chord names (the ones
     * usually written above the staff) gets those transposed instead, and everything else is returned byte for byte.
     * The environment is a unit: a transposition that would take a fret off the fingerboard moves the whole of it by
     * whole octaves instead, which keeps every interval in it exact, and if not even that fits (a tab spanning more
     * than [MAX_FRET] frets), the environment is left alone rather than half transposed.
     */
    fun transpose(lines: List<String>, semitones: Int, rename: (String) -> String): List<String> {
        val isStaffLine = lines.map(ChordProSyntax::isStaffLine)
        val frets = lines.filterIndexed { index, _ -> isStaffLine[index] }.flatMap(::fretNumbers)
        val shift = semitones + (octaveOffset(frets, semitones) ?: return lines)
        return lines.mapIndexed { index, line ->
            if (isStaffLine[index]) {
                transposeStaffLine(line, shift)
            } else {
                rewriteChordLine(line, rename)
            }
        }
    }

    /**
     * Rewrites the chord names of the lines of one tab environment with [rename], leaving the tablature untouched:
     * nothing moves on the fingerboard, only the names above it are spelled differently. It is how a notation the
     * viewer prefers reaches a tab, where transposing would mean the frets instead.
     */
    fun rewriteChordNames(lines: List<String>, rename: (String) -> String) =
        lines.map { line -> if (ChordProSyntax.isStaffLine(line)) line else rewriteChordLine(line, rename) }

    /** The chord names [rewriteChordNames] would rewrite in [lines], in order. */
    fun chordNames(lines: List<String>): List<String> = buildList {
        rewriteChordNames(lines) { name -> name.also(::add) }
    }

    /**
     * The positions of the fret numbers of a staff line: every run of digits that is not a repeat count, `x4` or, after
     * the last bar line, `4x`. Inside the staff a `3x` is a fret and a dead note on the next column, and moves.
     */
    private fun fretRanges(line: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        // What stands after the last bar line is a note to the player (`x3`, `3x`, `(3x)`), not a string's frets.
        val lastBar = line.lastIndexOf('|')
        var index = 0
        while (index < line.length) {
            if (line[index].isAsciiDigit) {
                var endIndex = index
                while (endIndex + 1 < line.length && line[endIndex + 1].isAsciiDigit) {
                    endIndex++
                }
                val isCountAfter = line.getOrNull(index - 1)?.lowercaseChar() == REPEAT_COUNT_MARKER
                val isCountBefore = index > lastBar && lastBar >= 0 && line.getOrNull(endIndex + 1)?.lowercaseChar() == REPEAT_COUNT_MARKER
                if (!isCountAfter && !isCountBefore) ranges += index..endIndex
                index = endIndex + 1
            } else {
                index++
            }
        }
        return ranges
    }

    /**
     * A fret number is ASCII. [Char.isDigit] takes every script's digits, and `toIntOrNull` reads those on the JVM but
     * not on Kotlin/Native or in the browser, so the same tab would be transposed on one platform and left on another.
     */
    private val Char.isAsciiDigit get() = this in '0'..'9'

    private fun fretNumbers(line: String) = fretRanges(line).mapNotNull { line.substring(it).toIntOrNull() }

    /** How many semitones of whole octaves to add so that every fret stays on the fingerboard, or null if none does. */
    private fun octaveOffset(frets: List<Int>, semitones: Int): Int? {
        if (frets.isEmpty()) return 0
        val lowestOctave = -(frets.min() + semitones).floorDiv(SEMITONES_IN_OCTAVE)
        val highestOctave = (MAX_FRET - frets.max() - semitones).floorDiv(SEMITONES_IN_OCTAVE)
        if (lowestOctave > highestOctave) return null
        return SEMITONES_IN_OCTAVE * 0.coerceIn(lowestOctave, highestOctave) // The one closest to not moving at all.
    }

    private fun transposeStaffLine(line: String, shift: Int): String {
        if (shift == 0) return line
        return replaceKeepingColumns(
            line = line,
            replacements = fretRanges(line).mapNotNull { range ->
                line.substring(range).toIntOrNull()?.let { fret -> range to (fret + shift).toString() }
            },
            filler = DASH,
        )
    }

    /**
     * Rewrites a line of the environment that is not tablature, but only if every word on it is a chord or a marker.
     *
     * A bare tuning line (`E A D G B E`) is six valid chord names, and is rewritten like a row of six chords would be:
     * nothing on the line itself tells the two apart, and a guess would be wrong as often as right. The spelling that
     * is safe is `Tuning: E A D G B E`, which the word `Tuning:` keeps from being read as chords.
     */
    private fun rewriteChordLine(line: String, rename: (String) -> String): String {
        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty() || trimmedLine.startsWith(SOURCE_COMMENT) || ChordProSyntax.matchDirective(trimmedLine) != null) return line
        if (ChordProSyntax.hasBrackets(line)) return ChordProTransposer.rewriteLyricsLineChords(line, rename)
        val replacements = chordWords(line)?.map { word ->
            word.range to rename(word.value)
        } ?: return line
        return if (replacements.isEmpty()) line else replaceKeepingColumns(line, replacements, filler = ' ')
    }

    /** The chord names of a line that holds only chords and markers, or null when the line is prose. */
    private fun chordWords(line: String): List<ChordProSyntax.Word>? {
        val chordWords = mutableListOf<ChordProSyntax.Word>()
        ChordProSyntax.words(line).forEach { word ->
            when {
                ChordProChordNames.isChordName(word.value) -> chordWords += word
                isMarker(word.value) -> Unit
                else -> return null
            }
        }
        return chordWords
    }

    private fun isMarker(word: String) = ChordProSyntax.isBar(word) ||
            word == BEAT ||
            word == REPEAT ||
            word == DOUBLE_REPEAT ||
            repeatCountRegex.matches(word) ||
            dashesRegex.matches(word) ||
            noChordRegex.matches(word)

    /**
     * Applies [replacements] (in order, non-overlapping) to [line], keeping everything after each of them in the
     * column it was in: a wider replacement eats a [filler] character next to it (after it, or if there is none to
     * spare there, before it), a narrower one puts one back. A filler is only ever eaten while another one is left to
     * separate the replacement from its neighbour, so a tab whose notes are one dash apart grows rather than becoming
     * unreadable.
     */
    private fun replaceKeepingColumns(line: String, replacements: List<Pair<IntRange, String>>, filler: Char): String {
        val result = StringBuilder()
        var consumedUntil = 0
        replacements.forEach { (range, replacement) ->
            result.append(line, consumedUntil, range.first)
            result.append(replacement)
            consumedUntil = range.last + 1
            var difference = replacement.length - (range.last - range.first + 1)
            while (difference > 0 && line.getOrNull(consumedUntil) == filler && line.getOrNull(consumedUntil + 1).let { it == null || it == filler }) {
                consumedUntil++
                difference--
            }
            while (difference > 0 && consumedUntil < line.length) { // With nothing after it, growing the line is free.
                val fillerIndex = result.length - replacement.length - 1
                if (fillerIndex <= 0 || result[fillerIndex] != filler || result[fillerIndex - 1] != filler) break
                result.deleteAt(fillerIndex)
                difference--
            }
            if (difference < 0) {
                repeat(-difference) { result.append(filler) }
            }
        }
        result.append(line, consumedUntil, line.length)
        return result.toString()
    }

    private const val MAX_FRET = 24
    private const val SEMITONES_IN_OCTAVE = 12
    private const val DASH = '-'
    private const val REPEAT_COUNT_MARKER = 'x' // The `x` of an `x4` after a bar or a `4x` after the last one, whose number is not a fret.
    private const val SOURCE_COMMENT = "#"
    private const val BEAT = "."
    private const val REPEAT = "%"
    private const val DOUBLE_REPEAT = "%%"
    private val dashesRegex = Regex("-+")
    private val repeatCountRegex = Regex("\\(?[xX]\\d+\\)?")

    /**
     * `N.C.`, and the ways it is abbreviated further: the one word a row of chord names carries that names the absence
     * of a chord, so it stands in the row without making it prose.
     */
    private val noChordRegex = Regex("N\\.?C\\.?", RegexOption.IGNORE_CASE)
}
