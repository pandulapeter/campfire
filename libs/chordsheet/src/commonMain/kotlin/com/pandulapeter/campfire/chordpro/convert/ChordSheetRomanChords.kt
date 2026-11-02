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

import com.pandulapeter.campfire.chordpro.chords.ChordProChordNames
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.ascii
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.chordTokens
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.furniture
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.rowChord
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.tokens

/**
 * A song whose chords are written as Roman numerals counted from its key (`i III iv V7`), the way an app prints a song
 * in its Roman notation, with each numeral replaced by the chord it names in that key before anything else reads the
 * song, so that every later rule sees an ordinary row of chords and the file holds letters, as every file does. Every
 * common way of writing a numeral is read ([ChordProNotation.romanNumeralChord]): qualities by case or in words,
 * accidentals before the step, signs after it, a bass or the step a dominant is applied to after a `/`, and a quality
 * word set apart from its numeral (`III aug`).
 *
 * Whether a song is written so is decided for the whole song by whole rows: a row is one of numerals where every word
 * on it is a numeral (or a bar line or another mark a chord row holds), and the song is one only with two such rows of
 * two numerals or more and no row of letter chords beside them. A row whose numerals are all `I` is a lyric ("I", "I I
 * I"), and so is a lone `i`, except in a song whose rows play `i` among other numerals; a heading, a margin label (`V`),
 * and verses numbered `I.`, `II.` are never a row of numerals.
 *
 * The case of the numerals is a quality only where the song writes both: a song written all in small letters or all in
 * capitals says its qualities in words (`vim`, `VIm`). One all in small letters that writes no minor anywhere (`i iv v`)
 * could mean either, and is left as it was read.
 *
 * The key is the one a key line names (`Key: Dm`), the first one for the rows before it and each later one from where it
 * stands. A song that names none is left as it was read, never counted from a key its chords suggest: a wrong guess would
 * write every chord of the song wrong. In a minor key, `III`, `VI` and `VII` are the minor scale's, as classical
 * analysis writes them, unless a numeral of the song is marked with a flat or a sharp (`bVII`), which says it counts on
 * the major scale from the key's note, as Campfire's own Roman notation does.
 *
 * Nashville numbers (`1 4 5 6-`) are not read: a row of bare numbers is as often a count of beats, a list, or the
 * numbers of a hymn's verses.
 */
internal object ChordSheetRomanChords {
    private const val MIN_ROWS = 2
    private val gap = Regex("\\s{2,}")
    private val qualityWords = setOf("aug", "dim")
    private val pronouns = setOf("I", "i")

    fun named(lines: List<Rendered>): List<Rendered> {
        val rows = lines.map { line -> line.takeUnless { it.isMarginLabel || ChordSheetHeadings.parse(it.text) != null }?.let { numeralRow(it.text) } }
        val strong = rows.filterNotNull().filter { row -> row.count(::isNumeral) >= 2 && row.any { isNumeral(it) && it.text !in pronouns } }
        if (strong.size < MIN_ROWS) return lines
        if (lines.any { line -> (chordTokens(line)?.count { rowChord(it.text) } ?: 0) >= 2 }) return lines
        val numerals = strong.flatten().filter(::isNumeral).map { it.text }
        val hasSmall = numerals.any { letters(it).first().isLowerCase() }
        val hasCapital = numerals.any { letters(it).first().isUpperCase() }
        if (hasSmall && !hasCapital && numerals.none { isWrittenMinor(it) }) return lines
        val playsSmallI = "i" in numerals
        val isOnKeyScale = numerals.none { it.first() in "b#\u266d\u266f" }
        val keys = keys(lines)
        return lines.mapIndexed { index, line ->
            val row = rows[index]?.takeIf { row -> row.any { isNumeral(it) && it.text !in pronouns } || playsSmallI && row.none { it.text == "I" } }
            val key = keys[index]
            if (row == null || key == null) {
                line
            } else {
                renamed(line, row) { ChordProNotation.romanNumeralChord(it, key, isOnKeyScale, isCaseQuality = hasSmall && hasCapital) } ?: line
            }
        }
    }

    /** Whether [text] is a row of numerals and the marks a chord row holds, whatever the song around it. */
    fun isNumeralLine(text: String) = numeralRow(text) != null

    /**
     * The words of a row of numerals, a quality word one space after its numeral joined to it, or null where a word on
     * it is something else.
     */
    private fun numeralRow(text: String): List<Token>? {
        val words = tokens(text).fold(mutableListOf<Token>()) { joined, word ->
            val previous = joined.lastOrNull()
            if (previous != null && word.text in qualityWords && word.index == previous.index + previous.text.length + 1 && isNumeral(previous)) {
                joined[joined.lastIndex] = Token(text.substring(previous.index, word.index + word.text.length), previous.index)
            } else {
                joined += word
            }
            joined
        }
        return words.takeIf { words.any(::isNumeral) && words.all { isNumeral(it) || furniture(it.text) } }
    }

    private fun isNumeral(word: Token) = isNumeralText(word.text)
    private fun isNumeralText(text: String) = ChordProNotation.romanNumeralChord(written(text), "C", isOnKeyScale = false, isCaseQuality = true) != null

    /** A numeral as [ChordProNotation.romanNumeralChord] reads it: its quality word joined to it, its signs in ASCII. */
    private fun written(text: String) = ascii(text).replace(" ", "")

    /** The step's own letters, `vii` of `bvii7`. */
    private fun letters(text: String) = written(text).trimStart('(').dropWhile { it in "b#" }.takeWhile { it in "IViv" }

    /** Whether a numeral writes a minor in a word after its step (`vim`, `II-7`), not by its case. */
    private fun isWrittenMinor(text: String) = written(text).trimStart('(').dropWhile { it in "b#" }.dropWhile { it in "IViv" }
        .let { it.startsWith('-') || it.startsWith('m') && !it.startsWith("ma") }

    /**
     * The key in force at each line: the first key line's before it, and each key line's from where it stands. A key's
     * value ends at a gap, where a header row prints its next field (`Key: Dm   ·   BPM: 120`).
     */
    private fun keys(lines: List<Rendered>): List<String?> {
        val named = lines.map { line ->
            val field = ChordSheetConverter.metadata(line.text)?.takeIf { it.first == "key" }
                ?: ChordSheetConverter.labelledRow(line.text, isHeader = true)?.firstOrNull { it.first == "key" }
            field?.second?.trim()?.split(gap)?.first()?.let(::ascii)
                ?.let { ChordProChordNames.latinExpanded(it) ?: it }
                ?.takeIf { ChordProChordNames.isChordName(it.substringBefore(' ')) }
        }
        var key = named.firstOrNull { it != null }
        return named.map { line -> line?.also { key = it } ?: key }
    }

    /**
     * [line] with every numeral of [row] replaced by its [name], or null where one names no chord. Each name stands where
     * its numeral did, its letters spread over the numeral's width, so that a measured row still meets the syllables it
     * was printed over; in text laid out in characters each keeps its numeral's column wherever the names before it
     * leave room for it.
     */
    private fun renamed(line: Rendered, row: List<Token>, name: (String) -> String?): Rendered? {
        val text = StringBuilder()
        val positions = mutableListOf<Double>()
        fun add(char: Char, position: Double) {
            text.append(char)
            positions += maxOf(position, positions.lastOrNull() ?: position)
        }
        for (word in row) {
            val chord = if (isNumeral(word)) name(written(word.text)) ?: return null else word.text
            val start = line.positions[word.index]
            val end = word.index + word.text.length
            val last = line.positions[end - 1]
            val after = line.positions.getOrNull(end) ?: (last + (last - start) / maxOf(1, word.text.length - 1) + 0.01)
            val column = if (text.isEmpty()) word.index else maxOf(word.index, text.length + 1)
            while (text.length < column) add(' ', line.positions.getOrNull(text.length) ?: start)
            chord.forEachIndexed { index, char -> add(char, start + (after - start) * index / chord.length) }
        }
        return line.copy(text = text.toString(), positions = positions.toDoubleArray(), bidi = null)
    }
}
