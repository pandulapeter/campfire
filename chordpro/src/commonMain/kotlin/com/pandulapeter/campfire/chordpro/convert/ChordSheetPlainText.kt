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

import com.pandulapeter.campfire.chordpro.language.BidiOrder
import com.pandulapeter.campfire.chordpro.language.WritingDirection
import com.pandulapeter.campfire.chordpro.language.writingDirectionOf

/**
 * A plain text laid out in character columns, as it shows in a monospace display.
 *
 * A text whose letters (its chord rows' aside) mostly read from the right is laid out as a right-to-left display shows it, every line aligned
 * to the right edge, since that is the display its chord rows were typed to line up in: a Hebrew site shows the sheet
 * from the right, and an author typing one sees it so. A chord row of two chords or more is one left-to-right run there,
 * drawn from its left end, so its chords stand over the syllables they were typed over only where it is read by its
 * columns on the screen; read by its characters' indices it would set them in the opposite order. Each line keeps its
 * text in reading order, written over its characters where they show ([ChordSheet.Span.pieces]) wherever that is not
 * the order they show in, and the converter sets its chords by where they stand, as it reads a PDF.
 */
internal object ChordSheetPlainText {
    fun lines(text: String): List<ChordSheet.Line> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        // Chord names are music, written from the left in any sheet, and would otherwise outvote a short Hebrew lyric.
        val words = lines.filterNot(ChordSheetConverter::isChordLine).joinToString("\n")
        if (writingDirectionOf(words) == WritingDirection.LEFT_TO_RIGHT) return lines.map(::leftToRight)
        val units = lines.map(::units)
        val width = units.maxOf { it.size }
        return units.map { rightToLeft(it, width) }
    }

    private fun leftToRight(line: String): ChordSheet.Line {
        var column = 0
        val spans = mutableListOf<ChordSheet.Span>()
        var run = StringBuilder()
        var start = 0
        fun flush() {
            if (run.isNotEmpty()) spans += ChordSheet.Span(run.toString(), start.toDouble(), column.toDouble(), isMonospace = true)
            run = StringBuilder()
        }
        for (character in line) {
            if (character.isWhitespace() || character == ' ') {
                flush()
                column = if (character == '\t') (column / 8 + 1) * 8 else column + 1
            } else {
                if (run.isEmpty()) start = column
                run.append(character)
                column++
            }
        }
        flush()
        return ChordSheet.Line(spans)
    }

    /**
     * The columns of [line]: a character each, a letter's marks in its column (a display draws them over it), a tab as
     * the spaces up to the next stop, and any other whitespace as one space.
     */
    private fun units(line: String): List<String> {
        val units = mutableListOf<String>()
        var index = 0
        while (index < line.length) {
            val character = line[index]
            val next = line.getOrNull(index + 1)
            when {
                character == '\t' -> repeat(8 - units.size % 8) { units += " " }
                character.isWhitespace() || character == ' ' -> units += " "
                character.isHighSurrogate() && next != null && next.isLowSurrogate() -> {
                    units += "$character$next"
                    index++
                }
                units.isNotEmpty() && units.last() != " " && character.isMark() -> units[units.lastIndex] += character
                else -> units += character.toString()
            }
            index++
        }
        return units
    }

    private fun rightToLeft(units: List<String>, width: Int): ChordSheet.Line {
        val drawn = BidiOrder.drawnOrder(BidiOrder.levels(units, WritingDirection.RIGHT_TO_LEFT))
        val column = IntArray(units.size)
        drawn.forEachIndexed { position, index -> column[index] = width - units.size + position }
        val words = units.indices.filter { units[it] != " " }
        if (words.isEmpty()) return ChordSheet.Line(emptyList())
        // A line that shows in the order it is read (a chord row, a Latin line) is set in words, as a left-to-right one.
        if (words.zipWithNext().all { (a, b) -> column[b] > column[a] }) {
            val spans = mutableListOf<ChordSheet.Span>()
            var start = words.first()
            for (index in words.indices) {
                val unit = words[index]
                if (index == words.lastIndex || words[index + 1] != unit + 1 || column[unit + 1] != column[unit] + 1) {
                    spans += ChordSheet.Span(
                        text = (start..unit).joinToString("") { units[it] },
                        start = column[start].toDouble(),
                        end = column[unit] + 1.0,
                        isMonospace = true,
                    )
                    start = words.getOrNull(index + 1) ?: break
                }
            }
            return ChordSheet.Line(spans)
        }
        val pieces = (words.first()..words.last()).map { ChordSheet.Span(units[it], column[it].toDouble(), column[it] + 1.0, isMonospace = true) }
        return ChordSheet.Line(
            listOf(
                ChordSheet.Span(
                    text = pieces.joinToString("") { it.text },
                    start = pieces.minOf { it.start },
                    end = pieces.maxOf { it.end },
                    isMonospace = true,
                    pieces = pieces,
                ),
            ),
        )
    }

    private fun Char.isMark() = category == CharCategory.NON_SPACING_MARK || category == CharCategory.ENCLOSING_MARK
}
