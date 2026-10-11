/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

/**
 * What a layout of a song is judged by besides its pages, in points: one point is what a column pays for ending one
 * chorded line ([lineHeight], the height of a line of chords over a line of lyrics) short of the screen. Of two ways of
 * setting a song on as many pages, the one that scores lower is the one a designer would have set by hand: no section
 * header stranded over a single line, no lone line continuing a verse at the top of a column, cuts between stanzas
 * rather than inside them, columns ending close together. The page count always decides first; the score only ever
 * chooses between layouts that agree on it.
 *
 * The units are those of [SongUnits]: the units of section `s` are `sectionStarts[s]` to `sectionStarts[s + 1]`,
 * [lineCount] is how many lines of lyrics a unit is and [isStanzaBreakBefore] whether a cut in front of it falls where
 * the song's writer already marked a boundary. Only lines of lyrics count towards a stub: a row of chord diagrams, a
 * system of tablature, a comment or the song's own first section is no stranded line, and a section of them pays for
 * its cuts and nothing else.
 */
internal class LayoutScoring(
    val sectionStarts: IntArray,
    lineHeight: Int,
    lineCount: (unit: Int) -> Int,
    private val isStanzaBreakBefore: (unit: Int) -> Boolean,
) {

    val unitCount = sectionStarts.last()

    /** The section of every unit. Looked up for every unit of every column tried, which a scan of the starts would make quadratic. */
    val unitSections = IntArray(unitCount).also { table ->
        for (section in 0 until sectionStarts.size - 1) table.fill(section, sectionStarts[section], sectionStarts[section + 1])
    }

    private val lineUnit = lineHeight.coerceAtLeast(1).toDouble()
    private val lineSums = IntArray(unitCount + 1).also { sums -> for (unit in 0 until unitCount) sums[unit + 1] = sums[unit] + lineCount(unit) }

    /** Whether [unit] starts a section, the end of the song counting as one. */
    fun isSectionStart(unit: Int) = unit >= unitCount || sectionStarts[unitSections[unit]] == unit

    /** How many lines of lyrics the units from [from] until [until] hold. */
    fun linesOf(from: Int, until: Int) = lineSums[until] - lineSums[from]

    /** What a piece of a cut section holding [lines] lines of lyrics costs: a single line most, two lines less, more nothing. */
    fun pieceCost(lines: Int) = when (lines) {
        1 -> STUB_PENALTY
        2 -> PAIR_PENALTY
        else -> 0.0
    }

    /** What cutting a section in front of [unit] costs, on top of what its pieces do. */
    fun cutCost(unit: Int): Double {
        val section = unitSections[unit]
        val sectionLines = linesOf(sectionStarts[section], sectionStarts[section + 1])
        val cut = if (isStanzaBreakBefore(unit)) STANZA_CUT_PENALTY else CUT_PENALTY
        return cut + if (sectionLines in 1..SHORT_SECTION_LINES) SHORT_SECTION_PENALTY else 0.0
    }

    /**
     * What a column holding the units from [from] until [end] costs for the pieces it holds of sections cut elsewhere,
     * and for the cut it ends with where it ends inside a section. A section it holds whole costs nothing.
     */
    fun columnCost(from: Int, end: Int): Double {
        val startsInside = !isSectionStart(from)
        val endsInside = !isSectionStart(end)
        if (!startsInside && !endsInside) return 0.0
        val firstSection = unitSections[from]
        val lastSection = unitSections[end - 1]
        var cost = 0.0
        if (startsInside) cost += pieceCost(linesOf(from, minOf(end, sectionStarts[firstSection + 1])))
        if (endsInside) {
            // A piece cut at both of its ends is one piece, already paid for above.
            if (!startsInside || firstSection != lastSection) cost += pieceCost(linesOf(sectionStarts[lastSection], end))
            cost += cutCost(end)
        }
        return cost
    }

    /**
     * What a column of a page that is not the song's last costs for ending [empty] pixels short of the screen: by the
     * square of the lines it leaves, so one short line is nearly free and three are expensive, which spreads the slack of
     * a page over its columns rather than leaving it in one.
     */
    fun emptyCost(empty: Int): Double {
        val lines = empty.coerceAtLeast(0) / lineUnit
        return EMPTY_PENALTY * lines * lines
    }

    /** What the columns of one page cost for ending [spread] pixels apart: a line or two of rag is fine, more is not. */
    fun spreadCost(spread: Int): Double {
        val lines = spread.coerceAtLeast(0) / lineUnit
        return SPREAD_PENALTY * lines * lines
    }

    /** What [lines] lines of lyrics wrapping into one more line height each cost, which only tells column widths apart. */
    fun wrapCost(lines: Int) = WRAP_PENALTY * lines
}

/**
 * The score of this grid (see [LayoutScoring]), given the [heights] of the units it places as [arrange] takes them, and
 * [wrappedLines], how many more line heights than it is written in every unit takes at the width this grid gives it:
 * every cut and every piece of a cut section, the empty space at the end of every column of a page that is not the
 * last - a page of rows joined under each other being one column, and a row taller than the screen leaving none - and
 * every wrapped line.
 */
internal fun SectionGrid.score(
    heights: IntArray,
    scoring: LayoutScoring,
    piecePadding: IntArray,
    sectionGap: Int,
    maxRowHeight: Int,
    wrappedLines: (unit: Int) -> Int,
): Double {
    val unitCount = heights.size
    if (unitCount == 0) return 0.0
    val unitSections = scoring.unitSections
    fun isInSameCell(unit: Int, other: Int) = rows[unit] == rows[other] && columns[unit] == columns[other]
    var score = 0.0
    var pieceStart = 0
    for (unit in 1..unitCount) {
        val isSameSection = unit < unitCount && unitSections[unit] == unitSections[unit - 1]
        if (isSameSection && isInSameCell(unit, unit - 1)) continue
        if (isSameSection) score += scoring.cutCost(unit)
        // A piece is a part of a section placed in one cell; only a section placed in several has any.
        val section = unitSections[pieceStart]
        val isCut = pieceStart > scoring.sectionStarts[section] || isSameSection
        if (isCut) score += scoring.pieceCost(scoring.linesOf(pieceStart, unit))
        pieceStart = unit
    }
    for (unit in 0 until unitCount) score += scoring.wrapCost(wrappedLines(unit))

    val arrangement = arrange(heights, sectionGap, rowGap = 0, unitSections = unitSections, piecePadding = piecePadding)
    val pageCount = arrangement.pageBottoms.size
    if (pageCount < 2) return score
    // With no gap between them, every page starts where the one before it ends.
    var page = -1
    val pageOfRow = IntArray(columnCounts.size) { row -> if (startsPage(row)) ++page else page }
    val rowsOfPage = IntArray(pageCount)
    pageOfRow.forEach { rowsOfPage[it]++ }
    val columnBottoms = Array(pageCount) { IntArray(columnCounts[pageOfRow.indexOf(it)]) }
    for (unit in 0 until unitCount) {
        val isCutAfter = unit < unitCount - 1 && unitSections[unit + 1] == unitSections[unit] && !isInSameCell(unit + 1, unit)
        val bottom = arrangement.tops[unit] + heights[unit] + if (isCutAfter) piecePadding[unitSections[unit]] else 0
        val unitPage = pageOfRow[rows[unit]]
        val column = if (rowsOfPage[unitPage] > 1) 0 else columns[unit]
        columnBottoms[unitPage][column] = maxOf(columnBottoms[unitPage][column], bottom)
    }
    for (pageIndex in 0 until pageCount - 1) {
        val pageTop = if (pageIndex == 0) 0 else arrangement.pageBottoms[pageIndex - 1]
        // A page of joined rows is read as one column, down to its bottom.
        val bottoms = if (rowsOfPage[pageIndex] > 1) intArrayOf(arrangement.pageBottoms[pageIndex]) else columnBottoms[pageIndex]
        bottoms.forEach { bottom -> score += scoring.emptyCost(maxRowHeight - (bottom - pageTop)) }
    }
    return score
}

/** The most lines of lyrics a section may have for any cut of it to cost [SHORT_SECTION_PENALTY] on top. */
private const val SHORT_SECTION_LINES = 4

/** A header over one line, or one line continuing a verse at the top of a column, is what a designer never lets through. */
private const val STUB_PENALTY = 12.0

/** A piece of two lines is better than one of a single line, and still a stub. */
private const val PAIR_PENALTY = 4.0

/** Any cut: a section in one piece reads best. */
private const val CUT_PENALTY = 2.0

/** A cut after an empty line or in front of a comment, in place of [CUT_PENALTY]: the writer marked the boundary. */
private const val STANZA_CUT_PENALTY = 0.5

/** Cutting a section of [SHORT_SECTION_LINES] lines or fewer at all: a four-line verse in two halves is never worth a line of space. */
private const val SHORT_SECTION_PENALTY = 6.0

/** Per square line a column of a page that is not the last ends short of the screen. */
private const val EMPTY_PENALTY = 1.0

/** Per square line the columns of one page end apart, where they are evened out. */
private const val SPREAD_PENALTY = 0.5

/** Per line height a line of lyrics wraps into more than it is written in. */
private const val WRAP_PENALTY = 0.75

/** A grid a stretch of the song may be set in, with the pages it takes and its [score]. */
internal class ScoredGrid(
    val grid: SectionGrid,
    val pageCount: Int,
    val score: Double,
)

/**
 * Whether this grid is better than [other], or than nothing at all: fewer pages, then a lower score. Of two that agree
 * on both, the one found first stays, so the order the candidates are tried in decides a full tie.
 */
internal fun ScoredGrid.isBetterThan(other: ScoredGrid?) = other == null || pageCount < other.pageCount || pageCount == other.pageCount && score < other.score
