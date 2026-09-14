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
 * Cuts one run of tablature into rows that fit a width, the way a tab book breaks a long staff into systems.
 *
 * A tab is a grid of columns: the notes of one moment sit above each other, and the chord names above the staff and
 * anything written under it are aligned to the same columns. So it can never be wrapped line by line, the way prose
 * is, and this cuts every line of the run at the same columns instead. A cut is made after a bar line wherever one
 * fits, so that a row ends where the music does; where a single bar is wider than the row, the cut falls on a column
 * every string is silent on and no chord name spans, and only when there is no such column is it made wherever the
 * row is full. Every row after the first repeats the string names in front of the staff (`e|`, `B|`, …), because
 * without them the reader would have to count strings back to the first row.
 */
object ChordProTabWrapper {

    /**
     * Whether the lines are tablature at all: a run inside `{start_of_tab}` that holds no staff line is preformatted
     * text (chord names over lyrics, most often), whose columns line up only while no line is cut, and it is left to
     * the caller to show that some other way.
     */
    fun isTablature(lines: List<String>) = lines.any(ChordProSyntax::isStaffLine)

    /**
     * Cuts [lines], the lines of one run of tablature, into rows of at most [maxColumns] characters. Every row is a
     * list of lines in the order of the run, cut at the same column; a line that has nothing left to say in a row
     * (a row of chord names past its last chord) is left out of it, while a staff line is always kept, so that the
     * strings are the same in every row. The run comes back as a single row whenever it fits as it is, and a run
     * that is not tablature at all (see [isTablature]) is returned whole.
     *
     * The rows are never wider than [maxColumns] unless that leaves fewer than [MIN_CAPACITY] columns for the music
     * itself, at which point the strings are shown that wide anyway, since a staff cut into pieces of two characters
     * is not a staff any more.
     */
    fun wrap(lines: List<String>, maxColumns: Int): List<List<String>> {
        val isStaffLine = lines.map(ChordProSyntax::isStaffLine)
        val length = lines.maxOfOrNull { it.length } ?: 0
        if (isStaffLine.none { it } || length <= maxColumns) return listOf(lines)
        val prefixes = lines.mapIndexed { index, line -> if (isStaffLine[index]) staffPrefix(line) else "" }
        val prefixWidth = prefixes.maxOf { it.length }
        val barColumns = barColumns(lines, isStaffLine, length)
        val rows = mutableListOf<List<String>>()
        var start = 0
        while (start < length) {
            // The first row keeps the lines' own beginnings, every later one starts with the repeated string names.
            val contentStart = if (start == 0) prefixWidth else start
            val capacity = (maxColumns - prefixWidth).coerceAtLeast(MIN_CAPACITY)
            val end = if (length - contentStart <= capacity) length else cutColumn(lines, isStaffLine, barColumns, contentStart, contentStart + capacity)
            rows += lines.mapIndexedNotNull { index, line ->
                val content = if (start < line.length) line.substring(start, minOf(end, line.length)) else ""
                val row = if (start == 0) content else (if (isStaffLine[index]) prefixes[index].padStart(prefixWidth) else " ".repeat(prefixWidth)) + content
                row.trimEnd().takeIf { isStaffLine[index] || it.isNotBlank() }
            }
            start = end
        }
        return rows
    }

    /**
     * The string name and the bar line that open a staff line (`e|`, `E |`, ` G|` next to an `Eb|`, or nothing at
     * all), which is what a continuation row repeats in front of its piece of the staff.
     */
    private fun staffPrefix(line: String) = staffPrefixRegex.find(line)?.value.orEmpty()

    /**
     * The columns where every staff line that reaches them has a bar line. A staff line that has already ended does
     * not object, since one string written shorter than the others is a common enough sloppiness and the bars of
     * the rest still say where the music breaks.
     */
    private fun barColumns(lines: List<String>, isStaffLine: List<Boolean>, length: Int): BooleanArray {
        val staffLines = lines.filterIndexed { index, _ -> isStaffLine[index] }
        return BooleanArray(length) { column ->
            val reachingLines = staffLines.filter { column < it.length }
            reachingLines.isNotEmpty() && reachingLines.all { it[column] == BAR }
        }
    }

    /**
     * Where the row whose content starts at [start] ends, given that it cannot reach past [maxEnd]: after the last
     * bar line that fits, failing that on the last quiet column in the second half of the row, and failing that at
     * the edge. The quiet cut is not allowed into the first half, since a row that short would only be made so by a
     * chord name sitting exactly where the row wants to end, and cutting the name is the smaller loss.
     */
    private fun cutColumn(lines: List<String>, isStaffLine: List<Boolean>, barColumns: BooleanArray, start: Int, maxEnd: Int): Int {
        for (end in maxEnd downTo start + MIN_BAR_WIDTH) {
            if (barColumns[end - 1] && !barColumns[end]) return end
        }
        for (end in maxEnd downTo start + (maxEnd - start) / 2) {
            if (lines.indices.all { index -> isQuietColumn(lines[index], isStaffLine[index], end) }) return end
        }
        return maxEnd
    }

    /** Whether nothing on the line would be torn by a cut in front of [column]: a dash on a staff, a space elsewhere. */
    private fun isQuietColumn(line: String, isStaffLine: Boolean, column: Int) =
        column >= line.length || line[column] == (if (isStaffLine) DASH else ' ')

    private const val MIN_CAPACITY = 8
    private const val MIN_BAR_WIDTH = 3 // A cut after the bar that opens a line would make a row of nothing but it.
    private const val BAR = '|'
    private const val DASH = '-'
    private val staffPrefixRegex = Regex("^\\s*[A-Za-z#]{0,2}\\s*\\|?")
}
