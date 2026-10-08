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

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SectionPagingTest {

    @Test
    fun `a song that fits the screen is one page`() {
        val grid = page(listOf(listOf(100), listOf(100, 100)), maxRowHeight = 1000)
        assertEquals(1, grid.pageCount)
        assertTrue(grid.sharesKeyline)
    }

    @Test
    fun `pages are filled rather than ended with a section`() {
        // Three sections of four 100 high lines on 550 high pages: every page is filled, the sections running on.
        val grid = page(List(3) { List(4) { 100 } }, maxRowHeight = 550)
        assertEquals(3, grid.pageCount)
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2), grid.pageOfEveryUnit())
    }

    @Test
    fun `a section is only cut where it may be`() {
        val sections = listOf(listOf(100, 100), listOf(100, 100, 100, 100))
        // The second section may only be cut after its second line, so the page ends after its first two lines.
        val grid = page(sections, maxRowHeight = 450, isCuttableBefore = { it == 4 })
        assertEquals(listOf(0, 0, 0, 0, 1, 1), grid.pageOfEveryUnit())
    }

    @Test
    fun `nothing is cut in front of a unit with no height`() {
        // The run of a tab drawn in slots, the last two of them empty at this width: the page may not end before them.
        val grid = page(listOf(listOf(300), listOf(100, 100, 0, 0)), maxRowHeight = 450)
        assertEquals(listOf(0, 0, 1, 1, 1), grid.pageOfEveryUnit())
    }

    @Test
    fun `an uncuttable stretch taller than the screen ends its page`() {
        // After a short section it starts on the same page; after one that fills half the screen, on a page of its own.
        assertEquals(listOf(0, 0, 1), page(listOf(listOf(100), listOf(800), listOf(100)), maxRowHeight = 500).pageOfEveryUnit())
        assertEquals(listOf(0, 1, 2), page(listOf(listOf(300), listOf(800), listOf(100)), maxRowHeight = 500).pageOfEveryUnit())
    }

    @Test
    fun `two narrow sections are set side by side on the page`() {
        val grid = page(listOf(listOf(100), listOf(80), listOf(60), listOf(100)), maxRowHeight = 1000, narrow = setOf(1, 2))
        assertEquals(1, grid.pageCount)
        assertContentEquals(intArrayOf(1, 2, 1), grid.columnCounts)
        assertContentEquals(booleanArrayOf(false, true, true), grid.joinsPrevious)
        assertContentEquals(intArrayOf(0, 1, 1, 2), grid.rows)
        assertContentEquals(intArrayOf(0, 0, 1, 0), grid.columns)
        // The pair is as tall as the taller of the two, a section gap under the first and above the last.
        val arrangement = grid.arrange(intArrayOf(100, 80, 60, 100), sectionGap = SECTION_GAP, rowGap = ROW_GAP)
        assertContentEquals(intArrayOf(0, 120, 120, 220), arrangement.tops)
        assertEquals(320, arrangement.height)
    }

    @Test
    fun `a narrow section next to a wide one is stacked`() {
        val grid = page(listOf(listOf(100), listOf(80), listOf(100)), maxRowHeight = 1000, narrow = setOf(1))
        assertContentEquals(intArrayOf(1), grid.columnCounts)
    }

    @Test
    fun `a pair that does not fit what is left is stacked where its first section can start there`() {
        // The pair would be 200 tall, which the 180 left of the first page does not hold, but the first line of its first
        // section does.
        val grid = page(listOf(listOf(300), listOf(100, 100), listOf(100)), maxRowHeight = 500, narrow = setOf(1, 2))
        assertContentEquals(intArrayOf(1, 1), grid.columnCounts)
        assertEquals(listOf(0, 0, 1, 1), grid.pageOfEveryUnit())
    }

    @Test
    fun `what cannot be cut runs a mostly empty page on past the screen`() {
        // A short first section, then a 450 high staff that cannot be cut: moving it to the next page would leave the first
        // one four fifths empty, so it starts there and the page runs on.
        val grid = page(listOf(listOf(100), listOf(450), listOf(100)), maxRowHeight = 500)
        assertEquals(listOf(0, 0, 1), grid.pageOfEveryUnit())
    }

    @Test
    fun `a pair that does not fit what is left starts the next page`() {
        val grid = page(listOf(listOf(400), listOf(200), listOf(200)), maxRowHeight = 500, narrow = setOf(1, 2))
        assertEquals(2, grid.pageCount)
        assertEquals(listOf(0, 1, 1), grid.pageOfEveryUnit())
    }

    @Test
    fun `the pages of joined rows are read apart`() {
        val grid = SectionGrid(
            rows = intArrayOf(0, 1, 1, 2),
            columns = intArrayOf(0, 0, 1, 0),
            columnCounts = intArrayOf(1, 2, 1),
            joinsPrevious = booleanArrayOf(false, true, false),
        )
        val arrangement = grid.arrange(intArrayOf(100, 50, 70, 100), sectionGap = SECTION_GAP, rowGap = ROW_GAP, minRowPitch = 400)
        // The pair joins the first page under its single column; the last row is a page of its own.
        assertContentEquals(intArrayOf(0, 120, 120, 400), arrangement.tops)
        assertEquals(listOf(380), arrangement.dividerTops)
        assertEquals(listOf(190, 500), arrangement.pageBottoms)
        assertEquals(2, grid.pageCount)
    }

    @Test
    fun `no page is taller than the screen and the song is in its order`() {
        val random = Random(7)
        repeat(200) {
            val sections = List(random.nextInt(1, 12)) { List(random.nextInt(1, 8)) { random.nextInt(20, 120) } }
            val narrow = sections.indices.filter { random.nextBoolean() }.toSet()
            val maxRowHeight = random.nextInt(300, 900)
            val grid = page(sections, maxRowHeight = maxRowHeight, narrow = narrow)
            val heights = sections.flatten().toIntArray()
            val pageBottoms = grid.arrange(heights, sectionGap = SECTION_GAP, rowGap = 0, unitSections = unitSections(sections)).pageBottoms
            pageBottoms.forEachIndexed { page, bottom -> assertTrue(bottom - (pageBottoms.getOrNull(page - 1) ?: 0) <= maxRowHeight) }
            val rowOrder = grid.rows.toList()
            assertEquals(rowOrder.sorted(), rowOrder)
        }
    }

    private fun SectionGrid.pageOfEveryUnit(): List<Int> {
        var page = -1
        val pages = IntArray(columnCounts.size) { row -> if (startsPage(row)) ++page else page }
        return rows.map { pages[it] }
    }

    private fun page(
        sectionUnits: List<List<Int>>,
        maxRowHeight: Int,
        narrow: Set<Int> = emptySet(),
        isCuttableBefore: (Int) -> Boolean = { true },
    ): SectionGrid {
        val heights = sectionUnits.flatten()
        val sectionStarts = IntArray(sectionUnits.size + 1).also { starts ->
            sectionUnits.forEachIndexed { section, units -> starts[section + 1] = starts[section] + units.size }
        }
        return flowIntoPages(
            sectionStarts = sectionStarts,
            heightAt = { unit, _ -> heights[unit] },
            isNarrow = { it in narrow },
            isCuttableBefore = isCuttableBefore,
            piecePadding = IntArray(sectionUnits.size),
            sectionGap = SECTION_GAP,
            maxRowHeight = maxRowHeight,
        )
    }

    @Test
    fun `a chords section is cut between its rows rather than run past the page`() {
        val grid = flowIntoPages(
            sectionStarts = intArrayOf(0, 1, 4),
            heightAt = { unit, _ -> intArrayOf(90, 130, 100, 100)[unit] },
            isNarrow = { false },
            isCuttableBefore = { it >= 2 },
            piecePadding = IntArray(2),
            sectionGap = 12,
            maxRowHeight = 163,
        )
        assertContentEquals(intArrayOf(0, 1, 2, 3), grid.rows)
        assertContentEquals(intArrayOf(1, 1, 1, 1), grid.columnCounts)
        assertTrue(grid.joinsPrevious.none { it })
    }
}
