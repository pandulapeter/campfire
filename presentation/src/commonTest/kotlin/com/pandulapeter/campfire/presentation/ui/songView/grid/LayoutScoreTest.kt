/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.grid
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayoutScoreTest {

    @Test
    fun `a section set whole costs nothing`() {
        val scoring = scoring(listOf(listOf(100, 100), listOf(100)))
        val grid = SectionGrid(rows = IntArray(3), columns = intArrayOf(0, 0, 1), columnCounts = intArrayOf(2))
        assertEquals(0.0, grid.score(intArrayOf(100, 100, 100), scoring, IntArray(2), SECTION_GAP, maxRowHeight = 1000) { 0 })
    }

    @Test
    fun `a cut leaving a single line costs more than one between two stanzas`() {
        val stranded = scoring(listOf(List(6) { 100 })).columnCost(0, 5) + scoring(listOf(List(6) { 100 })).columnCost(5, 6)
        val betweenStanzas = scoring(listOf(List(6) { 100 })) { it == 3 }.let { it.columnCost(0, 3) + it.columnCost(3, 6) }
        assertTrue(betweenStanzas < stranded)
    }

    @Test
    fun `a short section costs more to cut than a long one`() {
        val short = scoring(listOf(List(4) { 100 })).let { it.columnCost(0, 2) + it.columnCost(2, 4) }
        val long = scoring(listOf(List(8) { 100 })).let { it.columnCost(0, 4) + it.columnCost(4, 8) }
        assertTrue(long < short)
    }

    @Test
    fun `a row of chord diagrams is never a stranded line`() {
        val scoring = LayoutScoring(sectionStarts = intArrayOf(0, 3), lineHeight = LINE_HEIGHT, lineCount = { 0 }, isStanzaBreakBefore = { false })
        assertEquals(scoring.cutCost(2), scoring.columnCost(0, 2) + scoring.columnCost(2, 3))
    }

    @Test
    fun `only the pages before the last pay for the space they leave`() {
        val sections = listOf(listOf(100), listOf(100), listOf(100))
        val scoring = scoring(sections)
        // Three pages of a single column, each a line on a screen of three.
        val grid = SectionGrid(rows = intArrayOf(0, 1, 2), columns = IntArray(3), columnCounts = intArrayOf(1, 1, 1))
        val score = grid.score(intArrayOf(100, 100, 100), scoring, IntArray(3), SECTION_GAP, maxRowHeight = 300) { 0 }
        assertEquals(2 * scoring.emptyCost(200), score)
    }

    @Test
    fun `every wrapped line counts`() {
        val scoring = scoring(listOf(listOf(100, 100)))
        val grid = SectionGrid(rows = IntArray(2), columns = IntArray(2), columnCounts = intArrayOf(1))
        assertEquals(2 * scoring.wrapCost(1), grid.score(intArrayOf(100, 100), scoring, IntArray(1), SECTION_GAP, maxRowHeight = 1000) { 1 })
    }

    @Test
    fun `the sections whole keep a full tie`() {
        val whole = ScoredGrid(emptyGrid(), pageCount = 2, score = 3.0)
        val cut = ScoredGrid(emptyGrid(), pageCount = 2, score = 3.0)
        assertFalse(cut.isBetterThan(whole))
        assertTrue(ScoredGrid(emptyGrid(), pageCount = 2, score = 2.5).isBetterThan(whole))
        assertTrue(ScoredGrid(emptyGrid(), pageCount = 1, score = 30.0).isBetterThan(whole))
    }

    @Test
    fun `the magazine takes as few pages as filling every column does`() {
        val random = Random(5)
        repeat(300) {
            val sections = List(random.nextInt(1, 12)) { List(random.nextInt(1, 7)) { random.nextInt(40, 200) } }
            val maxRowHeight = random.nextInt(250, 700)
            val columnCount = random.nextInt(2, 5)
            val heights = sections.flatten().toIntArray()
            val sectionStarts = sectionStartsOf(sections)
            val grid = flowLikeAMagazine(
                sectionStarts = sectionStarts,
                columnCount = columnCount,
                heightAt = { unit, _ -> heights[unit] },
                isCuttableBefore = { true },
                piecePadding = IntArray(sections.size),
                sectionGap = SECTION_GAP,
                maxRowHeight = maxRowHeight,
                lineHeight = LINE_HEIGHT,
                lineCount = { 1 },
                isStanzaBreakBefore = { false },
            )
            val greedyColumns = greedyColumnCount(heights, unitSections(sections), maxRowHeight)
            val pageCount = grid.pageCount(heights, SECTION_GAP, maxRowHeight, unitSections(sections), IntArray(sections.size))
            assertEquals((greedyColumns + columnCount - 1) / columnCount, pageCount)
        }
    }

    @Test
    fun `no page but the last holds a single line of a section where a column can be left short instead`() {
        // Three verses of four lines in two columns of six: whole, they take two pages, and cut, no fewer.
        val sections = List(3) { List(4) { 100 } }
        val heights = sections.flatten().toIntArray()
        val grid = flowLikeAMagazine(
            sectionStarts = sectionStartsOf(sections),
            columnCount = 2,
            heightAt = { unit, _ -> heights[unit] },
            isCuttableBefore = { true },
            piecePadding = IntArray(sections.size),
            sectionGap = SECTION_GAP,
            maxRowHeight = 600,
            lineHeight = LINE_HEIGHT,
            lineCount = { 1 },
            isStanzaBreakBefore = { false },
        )
        val units = unitSections(sections)
        assertTrue((1 until units.size).none { units[it] == units[it - 1] && (grid.rows[it] != grid.rows[it - 1] || grid.columns[it] != grid.columns[it - 1]) })
    }

    /** How many columns of [maxRowHeight] the units take when every column is filled as full as it goes. */
    private fun greedyColumnCount(heights: IntArray, unitSections: IntArray, maxRowHeight: Int): Int {
        var columns = 1
        var used = 0L
        heights.indices.forEach { unit ->
            val gap = if (unit > 0 && unitSections[unit] != unitSections[unit - 1] && used > 0) SECTION_GAP else 0
            if (used + gap + heights[unit] > maxRowHeight) {
                columns++
                used = heights[unit].toLong()
            } else {
                used += gap + heights[unit]
            }
        }
        return columns
    }

    private fun sectionStartsOf(sections: List<List<Int>>) = IntArray(sections.size + 1).also { starts ->
        sections.forEachIndexed { section, units -> starts[section + 1] = starts[section] + units.size }
    }

    private fun scoring(sections: List<List<Int>>, isStanzaBreakBefore: (Int) -> Boolean = { false }) = LayoutScoring(
        sectionStarts = sectionStartsOf(sections),
        lineHeight = LINE_HEIGHT,
        lineCount = { 1 },
        isStanzaBreakBefore = isStanzaBreakBefore,
    )
}
