/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.corpus

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.convert.ChordSheet
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter
import com.pandulapeter.campfire.chordpro.language.WritingDirection
import com.pandulapeter.campfire.chordpro.language.textDirection
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import java.text.Bidi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every lyric of the corpus written out as a website prints a chord sheet, chords on a row over their syllables, and read
 * back by the converter. A right-to-left song is written as a right-to-left display shows it, every line flush right,
 * laid out by the JDK's own bidi algorithm rather than the module's, so that the two are checked against each other.
 */
class PlainTextRoundTripTest {

    @Test
    fun `a chord sheet written out as plain text reads back with every chord over its syllable`() = Corpus.chordPro.forEach { (name, text) ->
        val song = ChordProParser.parse(text)
        val isRightToLeft = song.textDirection == WritingDirection.RIGHT_TO_LEFT
        val lyrics = song.blocks.filterIsInstance<ChordProBlock.Section>().filter { it.lines.all { line -> line is ChordProLine.Lyrics } }
            .flatMap { it.lines }.filterIsInstance<ChordProLine.Lyrics>()
            .filter { line -> line.chords.isNotEmpty() && line.chords.none { it.isAnnotation || it.isMark } && line.text.firstOrNull()?.isLetter() == true }
            .filter { line -> fits(line) && line.chords.first().position < line.text.length }
        // A row of one chord is as likely a sung word until another row shows the sheet has chords (ChordSheetConverterTest).
        if (lyrics.none { it.chords.size > 1 }) return@forEach
        val sheet = buildString {
            append(song.metadata.title).append("\n\n")
            lyrics.forEach { line -> append(if (isRightToLeft) rightToLeftRows(line) else leftToRightRows(line)).append('\n') }
        }
        val read = ChordProParser.parse(ChordSheetConverter.convert(ChordSheet.ofPlainText(sheet)).single())
        val readLines = read.blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }.filterIsInstance<ChordProLine.Lyrics>()
        assertEquals(lyrics.map(::placed), readLines.map(::placed), "$name\n$sheet")
    }

    /** Whether every chord name ends before the next chord starts, which a chord row needs to say where each one stands. */
    private fun fits(line: ChordProLine.Lyrics) = line.chords.zipWithNext().all { (a, b) -> a.position + a.name.length < b.position }

    /**
     * A line's text and chords, a chord past its last letter counted at its end, where a chord row cannot tell them apart.
     * Non-breaking spaces are read as spaces. A chord one column into a word is counted at the word's start, where the converter sets a chord a
     * column off a word's start (`ChordSheetConverterTest`), since a hand-typed row is often one off.
     */
    private fun placed(line: ChordProLine.Lyrics) = line.text.replace('\u00A0', ' ').trimEnd().let { text ->
        text to line.chords.map { chord ->
            val position = chord.position.coerceAtMost(text.length)
            val isOneIn = position >= 1 && text[position - 1] != ' ' && (position == 1 || text[position - 2] == ' ')
            (if (isOneIn) position - 1 else position) to chord.name
        }
    }

    private fun leftToRightRows(line: ChordProLine.Lyrics): String {
        val row = StringBuilder()
        line.chords.forEach { chord ->
            while (row.length < chord.position) row.append(' ')
            row.append(chord.name)
        }
        return "$row\n${line.text}"
    }

    /**
     * The chord row and the lyric of [line] as a right-to-left display draws them flush right, with each chord over the
     * first letter of its word: ending at a right-to-left letter's right edge, starting at a left-to-right one's left.
     * Columns are counted from the right edge, so that lines of any length line up.
     */
    private fun rightToLeftRows(line: ChordProLine.Lyrics): String {
        val units = units(line.text)
        val bidi = Bidi(line.text, Bidi.DIRECTION_RIGHT_TO_LEFT)
        val levels = units.map { (start, _) -> bidi.getLevelAt(start).toByte() }.toByteArray()
        val drawn = Array<Any>(units.size) { it }
        Bidi.reorderVisually(levels, 0, drawn, 0, units.size)
        // The column of each unit counted from the right edge: 0 is the rightmost.
        val fromRight = IntArray(units.size)
        drawn.forEachIndexed { position, index -> fromRight[index as Int] = units.size - 1 - position }
        val cells = sortedMapOf<Int, Char>(compareByDescending { it })
        line.chords.forEach { chord ->
            val unit = units.indexOfFirst { (start, end) -> chord.position in start until end }
            val isRtlLetter = levels[unit] % 2 == 1
            // A right-to-left letter's chord ends at its right edge, a left-to-right one's starts at its left.
            val rightmost = if (isRtlLetter) fromRight[unit] else fromRight[unit] - chord.name.length + 1
            chord.name.forEachIndexed { offset, character -> cells[rightmost + chord.name.length - 1 - offset] = character }
        }
        // A row's leading spaces are drawn at its right in a right-to-left display, its chords as one left-to-right run.
        val lead = cells.keys.min()
        val run = (cells.keys.max() downTo lead).joinToString("") { (cells[it] ?: ' ').toString() }
        return " ".repeat(lead) + run + "\n" + line.text
    }

    /** The characters of [text] that take a column each, as start and end indices: a letter with its marks is one. */
    private fun units(text: String): List<Pair<Int, Int>> {
        val units = mutableListOf<Pair<Int, Int>>()
        text.forEachIndexed { index, character ->
            if (character.category == CharCategory.NON_SPACING_MARK && units.isNotEmpty()) {
                units[units.lastIndex] = units.last().first to index + 1
            } else {
                units += index to index + 1
            }
        }
        return units
    }
}
