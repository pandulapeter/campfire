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
    fun transpose(lines: List<String>, semitones: Int, preferFlats: Boolean): List<String> {
        val isStaffLine = lines.map(::isStaffLine)
        val frets = lines.filterIndexed { index, _ -> isStaffLine[index] }.flatMap(::fretNumbers)
        val shift = semitones + (octaveOffset(frets, semitones) ?: return lines)
        return lines.mapIndexed { index, line ->
            if (isStaffLine[index]) transposeStaffLine(line, shift) else transposeChordLine(line, semitones, preferFlats)
        }
    }

    /**
     * A tablature line: enough dashes to be a staff, and made mostly of the characters a staff is made of. The letters
     * of a technique (`h`, `p`, `x`, …) and the string name in front of the line are the minority that is allowed.
     */
    private fun isStaffLine(line: String): Boolean {
        var dashCount = 0
        var staffCharacterCount = 0
        var otherCharacterCount = 0
        line.forEach { character ->
            when {
                character == DASH -> {
                    dashCount++
                    staffCharacterCount++
                }

                character == '|' || character.isDigit() -> staffCharacterCount++
                character.isWhitespace() -> Unit
                else -> otherCharacterCount++
            }
        }
        return dashCount >= MINIMUM_DASH_COUNT && staffCharacterCount >= otherCharacterCount
    }

    /** The positions of the fret numbers of a staff line: every run of digits that is not the count of an `x4`. */
    private fun fretRanges(line: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var index = 0
        while (index < line.length) {
            if (line[index].isDigit()) {
                var endIndex = index
                while (endIndex + 1 < line.length && line[endIndex + 1].isDigit()) {
                    endIndex++
                }
                if (line.getOrNull(index - 1)?.lowercaseChar() != REPEAT_COUNT_MARKER) {
                    ranges += index..endIndex
                }
                index = endIndex + 1
            } else {
                index++
            }
        }
        return ranges
    }

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
            filler = DASH
        )
    }

    /** Transposes a line of the environment that is not tablature, but only if every word on it is a chord. */
    private fun transposeChordLine(line: String, semitones: Int, preferFlats: Boolean): String {
        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty() || trimmedLine.startsWith(SOURCE_COMMENT) || ChordProSyntax.matchDirective(trimmedLine) != null) return line
        if (ChordProSyntax.chordRegex.containsMatchIn(line)) return ChordProTransposer.transposeLyricsLine(line, semitones, preferFlats)
        val replacements = mutableListOf<Pair<IntRange, String>>()
        wordRegex.findAll(line).forEach { match ->
            val word = match.value
            val isParenthesized = word.length > 2 && word.startsWith('(') && word.endsWith(')')
            val name = if (isParenthesized) word.substring(1, word.length - 1) else word
            when {
                chordNameRegex.matches(name) -> {
                    val transposedName = ChordProTransposer.transposeChord(name, semitones, preferFlats)
                    replacements += match.range to if (isParenthesized) "($transposedName)" else transposedName
                }

                isMarker(word) -> Unit
                else -> return line // A word that is not a chord: this is prose, not a row of chord names.
            }
        }
        return if (replacements.isEmpty()) line else replaceKeepingColumns(line, replacements, filler = ' ')
    }

    private fun isMarker(word: String) = ChordProSyntax.isBar(word) ||
            word == BEAT ||
            word == REPEAT ||
            word == DOUBLE_REPEAT ||
            repeatCountRegex.matches(word) ||
            dashesRegex.matches(word)

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
    private const val MINIMUM_DASH_COUNT = 3
    private const val DASH = '-'
    private const val REPEAT_COUNT_MARKER = 'x' // The `x` of an `x4` after a bar, whose number is not a fret.
    private const val SOURCE_COMMENT = "#"
    private const val BEAT = "."
    private const val REPEAT = "%"
    private const val DOUBLE_REPEAT = "%%"
    private val wordRegex = Regex("\\S+")
    private val dashesRegex = Regex("-+")
    private val repeatCountRegex = Regex("\\(?[xX]\\d+\\)?")
    private val chordNameRegex = Regex(
        "[A-H][#b♯♭]?(?:maj|min|dim|aug|sus|add|m|M|\\+|°|ø)?[0-9]*(?:(?:maj|min|dim|aug|sus|add|[#b♯♭])[0-9]*)*(?:/[A-H][#b♯♭]?)?"
    )
}
