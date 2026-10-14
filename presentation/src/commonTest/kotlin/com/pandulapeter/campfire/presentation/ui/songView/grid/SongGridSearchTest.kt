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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongGridSearchTest {

    /** [count] sections of [lines] lines each, every line [lineWidth] wide, each section a single unit. */
    private fun song(count: Int, lines: Int, lineWidth: Int) = List(count) { listOf(List(lines) { lineWidth }) }

    private fun search(
        sections: List<List<List<Int>>>,
        spec: SongGridSpec,
        timingStarts: IntArray = intArrayOf(0),
        tabUnits: Set<Int> = emptySet(),
        metrics: FakeSectionMetrics = FakeSectionMetrics(unitLinesOf(sections), tabUnits),
    ) = SongGridSearch(songUnitsOf(sections, timingStarts), metrics, spec)

    private fun SectionGrid.cellOf(unit: Int) = rows[unit] to columns[unit]

    @Test
    fun `a song that fits one row takes the fewest columns that hold it`() {
        // Lines as wide as a column of two, so that two columns fill the width as they fill the height.
        val sections = song(count = 6, lines = 4, lineWidth = 550)
        val decided = search(sections, spec(availableHeight = 700)).decide(settledWidth = 1_200, canFit = true)
        assertEquals(listOf(2), decided.grid.columnCounts.toList())
        assertTrue(6 * 4 * LINE_HEIGHT / 2 + 5 * SECTION_GAP > 700)
    }

    @Test
    fun `a short wide song on a wide screen takes more columns than the fewest and wraps no line more`() {
        val sections = song(count = 4, lines = 4, lineWidth = 200)
        val metrics = FakeSectionMetrics(unitLinesOf(sections))
        val decided = search(sections, spec(availableHeight = 1_000), metrics = metrics).decide(settledWidth = 2_000, canFit = true)
        assertEquals(1, decided.grid.columnCounts.size)
        assertTrue(decided.grid.columnCounts.single() > 1)
        assertTrue(metrics.heightCalls.none { (_, width) -> width < 200 })
    }

    @Test
    fun `a window with room for one column that is not cut searches no other count`() {
        val sections = song(count = 8, lines = 6, lineWidth = 450)
        val metrics = FakeSectionMetrics(unitLinesOf(sections))
        val decided = search(sections, spec(availableHeight = 600, fitScales = emptyList(), canCutSections = false), metrics = metrics)
            .decide(settledWidth = 500, canFit = true)
        assertTrue(decided.grid.columnCounts.all { it == 1 })
        assertTrue(metrics.heightCalls.all { (_, width) -> width == 500 })
    }

    @Test
    fun `the editor's preview never cuts a section`() {
        val sections = List(6) { List(3) { List(4) { 300 } } }
        val decided = search(sections, spec(availableHeight = 700, canCutSections = false)).decide(settledWidth = 1_200, canFit = true)
        sections.indices.forEach { section ->
            val units = (section * 3 until section * 3 + 3).map { decided.grid.cellOf(it) }
            assertEquals(1, units.distinct().size)
        }
    }

    @Test
    fun `a song too long for one row is cut like a magazine, in no more pages than with its sections whole`() {
        val sections = List(7) { List(3) { List(3) { 300 } } }
        val cut = search(sections, spec(availableHeight = 800)).decide(settledWidth = 1_200, canFit = true).grid
        val whole = search(sections, spec(availableHeight = 800, canCutSections = false)).decide(settledWidth = 1_200, canFit = true).grid
        assertTrue(cut.pageCount <= whole.pageCount)
        assertTrue(sections.indices.any { section -> (section * 3 until section * 3 + 3).map { cut.cellOf(it) }.distinct().size > 1 })
    }

    @Test
    fun `every stretch of a song that changes its tempo starts a page`() {
        val sections = song(count = 6, lines = 3, lineWidth = 300)
        val grid = search(sections, spec(availableHeight = 2_000), timingStarts = intArrayOf(0, 3)).decide(settledWidth = 1_200, canFit = true).grid
        assertTrue(grid.startsPage(grid.rows[3]))
        assertTrue(grid.rows[3] != grid.rows[2])
    }

    @Test
    fun `a songbook with tempo changes is one column`() {
        val sections = song(count = 210, lines = 2, lineWidth = 300)
        val grid = search(sections, spec(availableHeight = 800), timingStarts = intArrayOf(0, 100)).decide(settledWidth = 1_600, canFit = true).grid
        assertTrue(grid.columnCounts.all { it == 1 })
    }

    @Test
    fun `a staff wider than a column gets a row as wide as it`() {
        val sections = listOf(listOf(List(12) { 900 })) + song(count = 6, lines = 4, lineWidth = 300)
        val grid = search(sections, spec(availableHeight = 800), tabUnits = setOf(0)).decide(settledWidth = 1_400, canFit = true).grid
        assertTrue(grid.wideRows.any { it })
    }

    @Test
    fun `narrow columns are taken only where they save a page`() {
        val narrowLines = search(song(count = 9, lines = 4, lineWidth = 250), spec(availableHeight = 700)).decide(settledWidth = 1_000, canFit = true).grid
        assertEquals(3, narrowLines.columnCounts.max())
        val wideLines = search(song(count = 9, lines = 4, lineWidth = 350), spec(availableHeight = 700)).decide(settledWidth = 1_000, canFit = true).grid
        assertTrue(wideLines.columnCounts.all { it <= 2 })
    }

    @Test
    fun `a song a few lines over is fitted only once fitting is allowed`() {
        val sections = song(count = 10, lines = 2, lineWidth = 300)
        val search = search(sections, spec(availableHeight = 1_170))
        val deferred = search.decide(settledWidth = 500, canFit = false)
        assertEquals(1f, deferred.fitScale)
        assertTrue(deferred.isFitDeferred)
        val fitted = search.decide(settledWidth = 500, canFit = true)
        assertEquals(0.975f, fitted.fitScale)
        assertTrue(fitted.grid.pageCount < deferred.grid.pageCount)
    }

    @Test
    fun `the step buttons' edge is kept only where the song scrolls`() {
        val short = song(count = 2, lines = 2, lineWidth = 300)
        val long = song(count = 30, lines = 4, lineWidth = 300)
        assertFalse(search(short, spec(availableHeight = 800, endInset = 100)).decide(settledWidth = 1_200, canFit = true).isInset)
        assertTrue(search(long, spec(availableHeight = 800, endInset = 100)).decide(settledWidth = 1_200, canFit = true).isInset)
        assertTrue(search(short, spec(availableHeight = 800, endInset = 100, keepsEndInset = true)).decide(settledWidth = 1_200, canFit = true).isInset)
    }
}
