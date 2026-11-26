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
    fun withoutDiagrams(glyphs: List<Positioned>): List<Positioned> = withoutDiagramRows(withoutDiagramColumn(glyphs))

    private fun withoutDiagramRows(glyphs: List<Positioned>): List<Positioned> {
        val rows = rows(glyphs)
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

    /**
     * The page without a column of diagrams set beside the song, at either edge (a margin the ChordPro program fills with
     * the song's chords). Their labels share baselines with the song's lines, so no whole row is only a diagram's and the
     * rows above miss them; across the page they would be read into the song's lines as stray chords and numbers. A column
     * is cut off at a band no glyph crosses, two ems of the song's type wide, and goes only where every row of it is a
     * diagram's: chord names, each followed by marks before the next, every mark in small type, two names or more.
     */
    private fun withoutDiagramColumn(glyphs: List<Positioned>): List<Positioned> {
        val ink = glyphs.filter { it.span.text.isNotBlank() }.sortedBy { it.span.start }
        if (ink.isEmpty()) return glyphs
        val em = ink.map { it.span.size }.sorted()[ink.size / 2]
        val gaps = mutableListOf<Double>()
        var reach = ink.first().span.end
        for (glyph in ink.drop(1)) {
            if (glyph.span.start - reach > em * GUTTER) gaps += glyph.span.start
            reach = maxOf(reach, glyph.span.end)
        }
        val tried = if (gaps.size > 2 * EDGE_GAPS) gaps.take(EDGE_GAPS) + gaps.takeLast(EDGE_GAPS) else gaps
        for (gap in tried) {
            val (left, right) = glyphs.partition { it.span.start < gap }
            if (isDiagramColumn(right, besides = left)) return left
            if (isDiagramColumn(left, besides = right)) return right
        }
        return glyphs
    }

    private fun isDiagramColumn(column: List<Positioned>, besides: List<Positioned>): Boolean {
        val songSizes = inks(besides).filter { kind(it) == Kind.TEXT }.flatMap { row -> row.map { it.span.size } }.sorted()
        if (songSizes.isEmpty()) return false
        val small = songSizes[songSizes.size / 2] * SMALL_TYPE
        val rows = inks(column).filter { it.isNotEmpty() }
        val kinds = rows.map(::kind)
        if (kinds.count { it == Kind.NAMES } < 2 || kinds.first() != Kind.NAMES) return false
        return kinds.indices.all { index ->
            when (kinds[index]) {
                Kind.NAMES -> kinds.getOrNull(index + 1).let { it == Kind.MARK || it == Kind.MARKS }
                Kind.MARK, Kind.MARKS -> rows[index].all { it.span.size < small }
                Kind.TEXT -> false
            }
        }
    }

    /** The glyphs' rows, top to bottom, as indices into [glyphs]: those within a fifth of their type of one baseline. */
    private fun rows(glyphs: List<Positioned>): List<List<Int>> {
        val rows = mutableListOf<MutableList<Int>>()
        for (index in glyphs.indices.sortedBy { glyphs[it].y }) {
            val row = rows.lastOrNull()
            val first = row?.first()?.let(glyphs::get)
            if (first == null || abs(first.y - glyphs[index].y) > minOf(first.span.size, glyphs[index].span.size) * 0.22) rows += mutableListOf(index)
            else row += index
        }
        return rows
    }

    /** Each row's ink in reading order. */
    private fun inks(glyphs: List<Positioned>) =
        rows(glyphs).map { row -> row.map(glyphs::get).filter { it.span.text.isNotBlank() }.sortedBy { it.span.start } }

    /** A row's role in a diagram: its names, one mark, several marks (a diagram's string row), or neither. */
    private enum class Kind { NAMES, MARK, MARKS, TEXT }

    /** What a row of [ink], in reading order, could be to a diagram. */
    private fun kind(ink: List<Positioned>): Kind {
        // Built up rather than joined again for every glyph, which a row of one word as long as the page allows would make
        // take time growing with the square of its length.
        val builders = mutableListOf<StringBuilder>()
        var end = Double.NEGATIVE_INFINITY
        for (glyph in ink) {
            if (builders.isEmpty() || glyph.span.start - end > glyph.span.size * 0.25) builders += StringBuilder()
            builders.last().append(glyph.span.text.trim())
            end = glyph.span.end
        }
        val words = builders.map { it.toString() }
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

    /** The narrowest band, in ems of the page's type, that sets a column of diagrams apart from the song. */
    private const val GUTTER = 2.0

    /**
     * How many of the bands nearest each edge are tried as a diagram column's, once a page has more than twice as many.
     * Each try reads both sides' rows afresh, so trying every band of a page of tiny glyphs set far apart, one band per
     * glyph, would take time growing with the square of its glyphs. A column hangs off one edge, so its band is among the
     * nearest; a real page has a handful (one per diagram side by side and per column of the song), never this many.
     */
    private const val EDGE_GAPS = 8

    /** The largest share of the song's type a diagram's labels are set in. */
    private const val SMALL_TYPE = 0.85
}
