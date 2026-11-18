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

/**
 * The lines of a fragment read from the right, written as [ChordSheetPlainText] reads a right-to-left plain-text chord
 * sheet: every line flush right, and each chord row a left-to-right box as wide as the lyric under it, both padded to
 * one width, whose chords stand over their syllables on the screen. A chord hangs from the right edge of the
 * right-to-left letter it stands before (the left edge of a Latin one), the chords after the lyric's end stand left of
 * it, and one that would overlap the chord before it moves left past that one, as a chord row typed by hand would.
 */
internal object ChordSheetHtmlRightToLeft {

    fun rendered(lines: List<ChordSheetHtmlReader.Line>) = lines.map(::layout).flatMap { layout ->
        when {
            layout.chords.isEmpty() -> listOf(layout.lyric)
            layout.lyric.isBlank() -> listOf(layout.chords.joinToString(" ") { it.second })
            else -> {
                // A column more than either needs pads the lyric, which tells the reader the rows were padded to one width.
                val width = maxOf(layout.columns, -layout.chords.minOf { it.first }) + 1
                listOf(row(layout.chords, width), layout.lyric + " ".repeat(width - layout.columns))
            }
        }
    }

    /**
     * A line's lyric, the columns it takes, and its chords, each at the column it starts in counted from the right edge of
     * the display (so negative), in the order they are read; a line with no lyric keeps its chords' indices.
     */
    private class Layout(val lyric: String, val columns: Int, val chords: List<Pair<Int, String>>)

    private fun layout(line: ChordSheetHtmlReader.Line): Layout {
        val lyric = line.text.toString().replace('\u00a0', ' ').trimEnd()
        if (line.chords.isEmpty() || lyric.isBlank()) return Layout(lyric, lyric.length, line.chords.sortedBy { it.first })
        val (units, unitOf) = units(lyric)
        val drawn = BidiOrder.drawnOrder(BidiOrder.levels(units, WritingDirection.RIGHT_TO_LEFT))
        val column = IntArray(units.size)
        drawn.forEachIndexed { position, unit -> column[unit] = position - units.size }
        val placed = mutableListOf<Pair<Int, String>>()
        val (inLyric, pastEnd) = line.chords.sortedBy { it.first }.partition { it.first < lyric.length }
        inLyric.forEach { (index, chord) ->
            val unit = unitOf[index]
            var start = if (units[unit][0].let { it.isLetterOrDigit() && !isStrongRtl(it.code) }) column[unit] else column[unit] + 1 - chord.length
            while (true) {
                val overlapped = placed.filter { (other, name) -> start < other + name.length + 1 && other < start + chord.length + 1 }
                if (overlapped.isEmpty()) break
                start = overlapped.minOf { it.first } - 1 - chord.length
            }
            placed += start to chord
        }
        // The chords after the lyric go on leftwards as the line does, far enough from its end to be read as sung after
        // it rather than on its last syllable.
        var end = minOf(-units.size, placed.minOfOrNull { it.first } ?: 0) - PAST_END_GAP + 1
        pastEnd.forEach { (_, chord) ->
            placed += end - chord.length to chord
            end -= chord.length + 1
        }
        return Layout(lyric, units.size, placed)
    }

    /**
     * The chord row whose chords stand at [chords]' columns, [width] wide, as wide as the lyric under it is padded to: a
     * chord row as long as its lyric is read as a left-to-right box over it, its spaces where they are written.
     */
    private fun row(chords: List<Pair<Int, String>>, width: Int): String {
        val row = CharArray(width) { ' ' }
        chords.forEach { (column, chord) -> chord.forEachIndexed { index, character -> row[width + column + index] = character } }
        return row.concatToString()
    }

    /** Columns between a lyric's end and the chords sung after it, past the em a chord may stand off its letter. */
    private const val PAST_END_GAP = 3

    /**
     * The columns of [line] as [ChordSheetPlainText] counts them (a letter with its marks is one, as is a surrogate
     * pair), and the column each of its characters is in.
     */
    private fun units(line: String): Pair<List<String>, IntArray> {
        val units = mutableListOf<String>()
        val unitOf = IntArray(line.length)
        line.forEachIndexed { index, character ->
            val isJoined = units.isNotEmpty() && (
                (character.isLowSurrogate() && line.getOrNull(index - 1)?.isHighSurrogate() == true) ||
                    (units.last() != " " && character.category.let { it == CharCategory.NON_SPACING_MARK || it == CharCategory.ENCLOSING_MARK })
                )
            if (isJoined) units[units.lastIndex] += character else units += if (character.isWhitespace()) " " else character.toString()
            unitOf[index] = units.lastIndex
        }
        return units to unitOf
    }
}
