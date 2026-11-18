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
import com.pandulapeter.campfire.chordpro.language.isStrongRtl
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
        val lines = ChordSheetSpelledChords.lines(text.replace("\r\n", "\n").replace('\r', '\n').split('\n'))
        // Chord names are music, written from the left in any sheet, and would otherwise outvote a short Hebrew lyric.
        val words = lines.filterNot(ChordSheetConverter::isChordLine).joinToString("\n")
        if (writingDirectionOf(words) == WritingDirection.LEFT_TO_RIGHT) return lines.map(::leftToRight)
        val units = lines.map(::units)
        val width = units.maxOf { it.size }
        // A row of starred chords (`Em*`) is one too, which the converter reads as it reads any other.
        val isChordRow = lines.map { line -> ChordSheetChordWords.chordRow(line)?.let { it.annotation == null && it.notes.isEmpty() } == true }
        fun lyricOf(index: Int) = units.getOrNull(index + 1)?.takeIf { isChordRow[index] && !isChordRow[index + 1] && it.any { unit -> unit != " " } }
        // A text that pads a chord row or its lyric with spaces to the other's width sets its rows in boxes of one width.
        val isPadded = units.indices.any { index ->
            lyricOf(index)?.let { lyric -> lyric.size == units[index].size && (lyric.last() == " " || units[index].last() == " ") } == true
        }
        return units.mapIndexed { index, line ->
            when {
                isChordRow[index] -> run(line, width, isPadded = isPadded && lyricOf(index)?.size == line.size)
                lines[index].none { isStrongRtl(it.code) } -> run(line, width, isPadded = false)
                else -> rightToLeft(line, width)
            }
        }
    }

    /**
     * A chord row, or a line with no right-to-left letter in it (a tab's string, a Latin line), in a right-to-left display:
     * one left-to-right run from its first character to its last, whatever symbols it holds (`Em*`, whose star a
     * right-to-left line would draw apart at its far left, as it would a staff's closing bar line), with the spaces before
     * it drawn at its right and those after it at its left, as such a display draws a line's leading and trailing spaces.
     * In a text that pads its rows to one width, a chord row exactly as long as the lyric under it ([isPadded]) was not
     * typed in such a display: it is a left-to-right box as wide as the lyric (a page that sets its chord rows left to
     * right and pads both rows of a pair to one width), its spaces where they were typed.
     */
    private fun run(units: List<String>, width: Int, isPadded: Boolean): ChordSheet.Line {
        val first = units.indexOfFirst { it != " " }
        if (first < 0) return ChordSheet.Line(emptyList())
        val last = units.indexOfLast { it != " " }
        val start = width - units.size + if (isPadded) first else units.size - 1 - last
        return ChordSheet.Line(
            leftToRight(units.subList(first, last + 1).joinToString("")).spans.map { it.copy(start = it.start + start, end = it.end + start) },
        )
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
