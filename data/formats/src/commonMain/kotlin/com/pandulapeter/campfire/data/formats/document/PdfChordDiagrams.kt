/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.document

import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter
import com.pandulapeter.campfire.data.formats.document.PdfLineLayout.Positioned
import kotlin.math.abs

/**
 * The chord diagrams a page draws over its song. A diagram's grid is drawn as lines, which are never read, so all that is
 * left of it is its labels: a chord name in type smaller than the song's over the grid, and under it the frets, the
 * fingers, and the muted or open strings marked in rows of numbers, `x`, `o`, and symbols in smaller type still. Read as
 * text they are a chord row followed by rows of stray numbers, and across the page they would pass for columns, so a run
 * of such rows holding both names and a row of several marks is left out before the lines are set. A run of names alone
 * stays: a chord row in small type over its lyric is the song's own, and the lyric under it ends the run. A lone mark (a
 * barre's position) joins a run without making one, so a page number under a chord-only outro does not take it along;
 * a row of bare digits under a chord row (`1 2 3 4`) still reads as a diagram, which nothing in the glyphs tells apart.
 */
internal object PdfChordDiagrams {
    fun withoutDiagrams(glyphs: List<Positioned>): List<Positioned> {
        val rows = mutableListOf<MutableList<Int>>()
        for (index in glyphs.indices.sortedBy { glyphs[it].y }) {
            val row = rows.lastOrNull()
            val first = row?.first()?.let(glyphs::get)
            if (first == null || abs(first.y - glyphs[index].y) > minOf(first.span.size, glyphs[index].span.size) * 0.22) rows += mutableListOf(index)
            else row += index
        }
        val inks = rows.map { row -> row.map(glyphs::get).filter { it.span.text.isNotBlank() }.sortedBy { it.span.start } }
        val kinds = inks.map(::kind)
        // The song's text is the measure of small type, since a page of many diagrams and few lyrics has as many labels.
        val sizes = inks.indices.filter { kinds[it] == Kind.TEXT }.flatMap { row -> inks[row].map { it.span.size } }.sorted()
        if (sizes.isEmpty()) return glyphs
        val small = sizes[sizes.size / 2] * SMALL_TYPE
        val isLabel = inks.indices.map { kinds[it] != Kind.TEXT && inks[it].isNotEmpty() && inks[it].all { glyph -> glyph.span.size < small } }
        val isDropped = BooleanArray(glyphs.size)
        var start = 0
        while (start < rows.size) {
            var end = start
            while (isLabel[start] && end + 1 < rows.size && isLabel[end + 1]) end++
            val run = (start..end).filter { isLabel[it] }.map { kinds[it] }
            if (Kind.NAMES in run && Kind.MARKS in run) (start..end).forEach { rows[it].forEach { index -> isDropped[index] = true } }
            start = end + 1
        }
        return if (isDropped.none { it }) glyphs else glyphs.filterIndexed { index, _ -> !isDropped[index] }
    }

    /** A row's role in a diagram: its names, one mark, several marks (a diagram's string row), or neither. */
    private enum class Kind { NAMES, MARK, MARKS, TEXT }

    /** What a row of [ink], in reading order, could be to a diagram. */
    private fun kind(ink: List<Positioned>): Kind {
        val words = mutableListOf<String>()
        var end = Double.NEGATIVE_INFINITY
        for (glyph in ink) {
            if (words.isEmpty() || glyph.span.start - end > glyph.span.size * 0.25) words += glyph.span.text.trim()
            else words[words.lastIndex] += glyph.span.text.trim()
            end = glyph.span.end
        }
        return when {
            words.isNotEmpty() && words.all(::isMark) -> if (words.size == 1) Kind.MARK else Kind.MARKS
            ChordSheetConverter.isChordLine(words.joinToString(" ")) -> Kind.NAMES
            else -> Kind.TEXT
        }
    }

    /**
     * A fret or finger number, a muted or open string's `x` or `o`, or a symbol drawn for one (`○`, `•`, or the `?` a glyph
     * with no character comes out as), never one of [NOT_MARKS].
     */
    private fun isMark(word: String) = word.length <= 2 && word.all { it.isDigit() } ||
        word in setOf("x", "X", "o", "O") || word.length == 1 && !word[0].isLetterOrDigit() && word[0] !in NOT_MARKS

    /** What count rows (`1 & 2 &`), page footers (`- 3 -`, `3 / 5`), and bar lines are made of, never a diagram's string. */
    private const val NOT_MARKS = "&+-/|–—"

    /** The largest share of the song's type a diagram's labels are set in. */
    private const val SMALL_TYPE = 0.85
}
