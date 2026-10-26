/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.tabs

/**
 * Rewrites the fret numbers of one staff system so that every moment of it stays one column on every line, for
 * [ChordProTabTransposer] and [ChordProTabRetuner].
 *
 * A system is a grid read top to bottom: the notes played together sit in the same columns of every string, and the
 * chord names over the staff stand in the columns of the notes they name. A fret number that changes width (`9` to
 * `10`) therefore cannot be absorbed by the dashes next to it on its own line, which shifts every note after it against
 * the other strings wherever its neighbours are a single dash apart. A column is the union of the fret numbers that
 * overlap across the strings, and it is exactly as much wider or narrower as its widest fret number grew or shrank:
 * dashes are added to or taken from the end of it on every staff line, and spaces to or from the lines between, at the
 * first gap between words from there on, so that no chord name is split.
 *
 * Nothing is ever borrowed from the dashes between two columns, because that is what makes a move reversible: what
 * moving a column up added is all that moving it back down can take away again, so a tab transposed and transposed
 * back is the tab it was, and a column the author padded keeps its padding.
 */
internal object ChordProTabColumns {

    /**
     * The [lines] of one system with their fret numbers replaced. [replacements] holds, for every line, the fret numbers
     * to rewrite in it, as ranges of the line in order with the text replacing each, or null for a line that is not a
     * staff line, which only moves with the columns.
     */
    fun replace(lines: List<String>, replacements: List<List<Pair<IntRange, String>>?>): List<String> {
        val columns = columnsOf(replacements)
        if (columns.isEmpty()) return lines
        val widths = columns.map { column -> widthOf(column, lines, replacements) }
        return lines.mapIndexed { index, line ->
            replacements[index]?.let { staffReplacements -> rewriteStaffLine(line, staffReplacements, columns, widths) }
                ?: moveLine(line, columns, widths)
        }
    }

    /** The columns of the system: the ranges of fret numbers that overlap on any of its strings, merged, in order. */
    private fun columnsOf(replacements: List<List<Pair<IntRange, String>>?>): List<IntRange> {
        val columns = mutableListOf<IntRange>()
        replacements.flatMap { it.orEmpty() }.map { it.first }.sortedBy { it.first }.forEach { range ->
            val last = columns.lastOrNull()
            if (last != null && range.first <= last.last) {
                columns[columns.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                columns += range
            }
        }
        return columns
    }

    /**
     * How wide [column] has to be once its fret numbers are replaced: as wide as the widest of what the staff lines
     * hold there without the dashes at its end. It comes out as wide as the column was wherever no fret number changes
     * width, since the fret number that ends the column ends it with no dash after it.
     */
    private fun widthOf(column: IntRange, lines: List<String>, replacements: List<List<Pair<IntRange, String>>?>) =
        lines.indices.maxOf { index ->
            val line = lines[index]
            val staffReplacements = replacements[index]
            if (staffReplacements == null || line.length <= column.first) {
                0
            } else {
                contentOf(line, column, staffReplacements).trimEnd(DASH).length
            }
        }

    /** What [line] holds in [column], or in as much of it as the line reaches, with its fret numbers replaced. */
    private fun contentOf(line: String, column: IntRange, replacements: List<Pair<IntRange, String>>) = buildString {
        val end = minOf(column.last + 1, line.length)
        var consumedUntil = column.first
        replacements.forEach { (range, replacement) ->
            if (range.first >= column.first && range.last < end) {
                append(line, consumedUntil, range.first)
                append(replacement)
                consumedUntil = range.last + 1
            }
        }
        append(line, consumedUntil, end)
    }

    /**
     * A staff line with every column brought to its new width by dashes added to or taken from its end. A line that
     * ends inside a column, or with it, is left as it ends: nothing after it has to line up, and padding it would make it
     * longer than the move back could tell, or put a dash after the bar of a string shorter than the one whose fret
     * widened the column.
     */
    private fun rewriteStaffLine(line: String, replacements: List<Pair<IntRange, String>>, columns: List<IntRange>, widths: List<Int>) = buildString {
        var consumedUntil = 0
        columns.forEachIndexed { position, column ->
            if (column.first >= line.length) return@forEachIndexed
            append(line, consumedUntil, column.first)
            val content = contentOf(line, column, replacements)
            if (column.last < line.lastIndex) {
                val width = widths[position]
                // Never shorter than its own content without the trailing dashes, which the width counts.
                append(if (content.length >= width) content.take(width) else content.padEnd(width, DASH))
            } else {
                append(content)
            }
            consumedUntil = minOf(column.last + 1, line.length)
        }
        append(line, consumedUntil, line.length)
    }

    /**
     * A line between the staff lines (chord names, most often) with as many spaces added or taken as every column grew
     * or shrank, where the column ends in both the old and the new system, or at the first gap between words after that.
     * Spaces are only taken where some are left between two words, unless the line starts or ends there.
     */
    private fun moveLine(line: String, columns: List<IntRange>, widths: List<Int>): String {
        val result = StringBuilder()
        var consumedUntil = 0
        columns.forEachIndexed { position, column ->
            val difference = widths[position] - (column.last + 1 - column.first)
            if (difference == 0) return@forEachIndexed
            val gap = gapFrom(line, maxOf(consumedUntil, column.first + minOf(widths[position], column.last + 1 - column.first)))
            if (gap >= line.length) return@forEachIndexed
            result.append(line, consumedUntil, gap)
            consumedUntil = gap
            if (difference > 0) {
                repeat(difference) { result.append(SPACE) }
            } else {
                val spaces = line.drop(gap).takeWhile { it == SPACE }.length
                val isOpen = gap == 0 || gap + spaces >= line.length || result.lastOrNull() == SPACE
                val removed = minOf(-difference, if (isOpen) spaces else (spaces - 1).coerceAtLeast(0))
                consumedUntil += removed
            }
        }
        result.append(line, consumedUntil, line.length)
        return result.toString()
    }

    /** The first index from [index] on that is not inside a word: a space, the start of a word, or the end of the line. */
    private fun gapFrom(line: String, index: Int): Int {
        var gap = index
        while (gap in 1 until line.length && line[gap - 1] != SPACE && line[gap] != SPACE) gap++
        return gap
    }

    private const val DASH = '-'
    private const val SPACE = ' '
}
