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
 * is, and this cuts every line of a system at the same columns instead. A cut is made after a bar line wherever one
 * fits, so that a row ends where the music does; where a single bar is wider than the row, the cut falls on a column
 * every string is silent on and no chord name spans, and only when there is no such column is it made wherever the
 * row is full. Every row after the first repeats the string names in front of the staff (`e|`, `B|`, …), because
 * without them the reader would have to count strings back to the first row.
 *
 * A run is whatever lines of a tab environment no blank line separates, and it may stack several systems, so it is
 * cut system by system: every system is cut at columns of its own, and its rows come before the next one's. A new
 * system starts at the lines above a staff that follows another, since lines between two staves are chord names
 * written over the staff they belong to (lyrics written under a staff therefore travel with the next system), or,
 * with nothing between them, where the string names start over, all of them in the same order: a name that recurs
 * inside one system, `E|` for both E strings or DADGAD's three `D|`, does not start one.
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
     * list of lines of one system in the order of the run, cut at the same column; a line that has nothing left to
     * say in a row (a row of chord names past its last chord) is left out of it, while a staff line is always kept,
     * so that the strings are the same in every row. The run comes back as a single row whenever it fits as it is,
     * and a run that is not tablature at all (see [isTablature]) is returned whole. So is a run that would wrap into
     * more row lines than half its characters, which only a crafted file does: shown unwrapped, it runs past the edge
     * rather than into memory no screen can draw.
     *
     * The rows are never wider than [maxColumns] unless that leaves fewer than [MIN_CAPACITY] columns for the music
     * itself, at which point the strings are shown that wide anyway, since a staff cut into pieces of two characters
     * is not a staff any more.
     */
    fun wrap(lines: List<String>, maxColumns: Int): List<List<String>> {
        val isStaffLine = lines.map(ChordProSyntax::isStaffLine)
        val length = lines.maxOfOrNull { it.length } ?: 0
        if (isStaffLine.none { it } || length <= maxColumns) return listOf(lines)
        // Proportional to the run rather than a fixed number, since a fixed cap per run or per system still lets a file
        // of many runs or many small systems multiply its size by the number of rows. A real system stays far below
        // it: with at least MIN_CAPACITY columns a row, its row lines are at most an eighth of its characters.
        var budget = lines.size + lines.sumOf { it.length } / 2
        val rows = mutableListOf<List<String>>()
        systems(lines, isStaffLine).forEach { system ->
            val systemRows = wrapSystem(
                lines = lines.subList(system.first, system.last + 1),
                isStaffLine = isStaffLine.subList(system.first, system.last + 1),
                maxColumns = maxColumns,
                budget = budget,
            ) ?: return listOf(lines)
            budget -= systemRows.sumOf { it.size }
            rows += systemRows
        }
        return rows
    }

    /** [wrap] for the lines of one system, or null as soon as its rows hold more than [budget] lines. */
    private fun wrapSystem(lines: List<String>, isStaffLine: List<Boolean>, maxColumns: Int, budget: Int): List<List<String>>? {
        val length = lines.maxOfOrNull { it.length } ?: 0
        if (isStaffLine.none { it } || length <= maxColumns) return if (lines.size > budget) null else listOf(lines)
        val prefixes = lines.mapIndexed { index, line -> if (isStaffLine[index]) staffPrefix(line) else "" }
        val prefixWidth = prefixes.maxOf { it.length }
        val barColumns = barColumns(lines, isStaffLine, length)
        val rows = mutableListOf<List<String>>()
        var lineCount = 0
        var start = 0
        while (start < length) {
            // The first row keeps the lines' own beginnings, every later one starts with the repeated string names.
            val contentStart = if (start == 0) prefixWidth else start
            val capacity = (maxColumns - prefixWidth).coerceAtLeast(MIN_CAPACITY)
            val end = if (length - contentStart <= capacity) length else cutColumn(lines, isStaffLine, barColumns, contentStart, contentStart + capacity)
            val rowLines = lines.mapIndexedNotNull { index, line ->
                // The same boundary on both ends: this row stops where the next one starts, so a character the shared
                // column falls inside travels whole into the row whose column has passed it. A staff line is ASCII, so
                // for it the boundary is the column itself, which is what makes this safe to ask of every line.
                val from = cutBoundary(line, start)
                val to = cutBoundary(line, end)
                val content = if (from < to) line.substring(from, to) else ""
                val row = if (start == 0) content else (if (isStaffLine[index]) prefixes[index].padStart(prefixWidth) else " ".repeat(prefixWidth)) + content
                row.trimEnd().takeIf { isStaffLine[index] || it.isNotBlank() }
            }
            lineCount += rowLines.size
            if (lineCount > budget) return null
            rows += rowLines
            start = end
        }
        return rows
    }

    /**
     * The systems of a run, as ranges of its lines. Lines before the first staff join the first system and lines after
     * the last staff the last one. The string names are only compared over [MAX_STRINGS] lines at most, which keeps
     * this linear in the run whatever it holds.
     */
    private fun systems(lines: List<String>, isStaffLine: List<Boolean>): List<IntRange> {
        val names = lines.mapIndexed { index, line -> if (isStaffLine[index]) staffPrefix(line).filterNot { it.isWhitespace() || it == BAR } else "" }
        val starts = mutableListOf(0)
        var systemStaffStart = -1
        var staffCount = 0
        var firstLineBetweenStaves = -1
        for (index in lines.indices) {
            if (!isStaffLine[index]) {
                if (staffCount > 0 && firstLineBetweenStaves < 0) firstLineBetweenStaves = index
                continue
            }
            val startsSystem = when {
                staffCount == 0 -> false
                firstLineBetweenStaves >= 0 -> true
                else -> staffCount <= MAX_STRINGS && names[index].isNotEmpty() && repeatsNames(names, isStaffLine, systemStaffStart, index, staffCount)
            }
            if (startsSystem) {
                starts += if (firstLineBetweenStaves >= 0) firstLineBetweenStaves else index
                systemStaffStart = index
                staffCount = 0
            }
            if (systemStaffStart < 0) systemStaffStart = index
            staffCount++
            firstLineBetweenStaves = -1
        }
        return starts.mapIndexed { position, start -> start until (starts.getOrNull(position + 1) ?: lines.size) }
    }

    /** Whether the [count] lines from [index] on are staff lines named exactly like the [count] lines from [from] on. */
    private fun repeatsNames(names: List<String>, isStaffLine: List<Boolean>, from: Int, index: Int, count: Int) =
        index + count <= names.size && (0 until count).all { offset ->
            isStaffLine[from + offset] && isStaffLine[index + offset] && names[from + offset] == names[index + offset]
        }

    /**
     * The string name and the bar line that open a staff line (`e|`, `E |`, ` G|` next to an `Eb|`, or nothing at
     * all), which is what a continuation row repeats in front of its piece of the staff.
     */
    private fun staffPrefix(line: String): String {
        var index = 0
        while (index < line.length && line[index].isWhitespace()) index++
        var letters = 0
        while (letters < MAX_PREFIX_LETTERS && line.getOrNull(index)?.isStringNameCharacter == true) {
            index++
            letters++
        }
        while (index < line.length && line[index].isWhitespace()) index++
        if (line.getOrNull(index) == BAR) index++
        return line.substring(0, index)
    }

    private val Char.isStringNameCharacter get() = this in 'A'..'Z' || this in 'a'..'z' || this == SHARP

    /**
     * The columns where every staff line that reaches them has a bar line. A staff line that has already ended does
     * not object, since one string written shorter than the others is a common enough sloppiness and the bars of
     * the rest still say where the music breaks.
     */
    private fun barColumns(lines: List<String>, isStaffLine: List<Boolean>, length: Int): BooleanArray {
        val reaching = IntArray(length)
        val bars = IntArray(length)
        lines.forEachIndexed { index, line ->
            if (isStaffLine[index]) {
                line.forEachIndexed { column, character ->
                    reaching[column]++
                    if (character == BAR) bars[column]++
                }
            }
        }
        return BooleanArray(length) { column -> reaching[column] > 0 && reaching[column] == bars[column] }
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

    /**
     * Where [line] may be cut for a row that the staff under it wants to end at [column].
     *
     * A tab is cut at columns the staff decides, and the staff is dashes, bars and digits. A line written above it
     * is somebody's own text, where a character may be written with more than one `Char` — an astral symbol as a
     * surrogate pair, a letter and the mark that accents it — and cutting through one draws a replacement glyph at
     * the end of one row and another at the start of the next. So the column is moved back off the middle of a
     * character. It is moved back for both sides of the cut, since the next row starts where this one ended, which
     * is what keeps the character in exactly one row.
     */
    private fun cutBoundary(line: String, column: Int): Int {
        var index = column.coerceAtMost(line.length)
        var moved = 0
        while (index > 0 && index < line.length && moved < MAX_CLUSTER && line.isInsideCharacter(index)) {
            index--
            moved++
        }
        return index
    }

    /** Whether the character at [index] belongs to the one in front of it rather than starting one of its own. */
    private fun String.isInsideCharacter(index: Int) =
        (this[index].isLowSurrogate() && this[index - 1].isHighSurrogate()) || this[index].isCombiningMark()

    /**
     * The combining marks a pasted title or chord name carries: the Latin block a decomposed accent is written
     * with, and the general categories every other script's marks fall in. `:data:model` has its own, narrower
     * answer for folding accents out of a file name; this module depends on nothing, so it carries its own.
     */
    private fun Char.isCombiningMark() = this in '\u0300'..'\u036F' ||
            category == CharCategory.NON_SPACING_MARK ||
            category == CharCategory.COMBINING_SPACING_MARK ||
            category == CharCategory.ENCLOSING_MARK

    /** Whether nothing on the line would be torn by a cut in front of [column]: a dash on a staff, a space elsewhere. */
    private fun isQuietColumn(line: String, isStaffLine: Boolean, column: Int) =
        column >= line.length || line[column] == (if (isStaffLine) DASH else ' ')

    private const val MIN_CAPACITY = 8
    private const val MAX_STRINGS = 12
    private const val MIN_BAR_WIDTH = 3 // A cut after the bar that opens a line would make a row of nothing but it.
    private const val BAR = '|'
    private const val DASH = '-'
    private const val MAX_PREFIX_LETTERS = 2
    private const val SHARP = '#'

    /**
     * A crafted line of nothing but combining marks would otherwise make the walk back as long as the line, once per
     * row; past this many the cut is made where the staff asked and one glyph is drawn wrong.
     */
    private const val MAX_CLUSTER = 16
}
