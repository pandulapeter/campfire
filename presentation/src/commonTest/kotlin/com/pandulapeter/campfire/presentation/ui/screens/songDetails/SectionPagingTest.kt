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
    fun aSongThatFitsTheScreenIsOnePage() {
        val grid = page(listOf(listOf(100), listOf(100, 100)), maxRowHeight = 1000)
        assertEquals(1, grid.pageCount)
        assertTrue(grid.sharesKeyline)
    }

    @Test
    fun pagesAreFilledRatherThanEndedWithASection() {
        // Three sections of four 100 high lines on 550 high pages: every page is filled, the sections running on.
        val grid = page(List(3) { List(4) { 100 } }, maxRowHeight = 550)
        assertEquals(3, grid.pageCount)
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2), grid.pageOfEveryUnit())
    }

    @Test
    fun aSectionIsOnlyCutWhereItMayBe() {
        val sections = listOf(listOf(100, 100), listOf(100, 100, 100, 100))
        // The second section may only be cut after its second line, so the page ends after its first two lines.
        val grid = page(sections, maxRowHeight = 450, isCuttableBefore = { it == 4 })
        assertEquals(listOf(0, 0, 0, 0, 1, 1), grid.pageOfEveryUnit())
    }

    @Test
    fun anUncuttableStretchTallerThanTheScreenIsAPageOfItsOwn() {
        val grid = page(listOf(listOf(100), listOf(800), listOf(100)), maxRowHeight = 500)
        assertEquals(listOf(0, 1, 2), grid.pageOfEveryUnit())
    }

    @Test
    fun twoNarrowSectionsAreSetSideBySideOnThePage() {
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
    fun aNarrowSectionNextToAWideOneIsStacked() {
        val grid = page(listOf(listOf(100), listOf(80), listOf(100)), maxRowHeight = 1000, narrow = setOf(1))
        assertContentEquals(intArrayOf(1), grid.columnCounts)
    }

    @Test
    fun aPairThatDoesNotFitWhatIsLeftStartsTheNextPage() {
        val grid = page(listOf(listOf(400), listOf(200), listOf(200)), maxRowHeight = 500, narrow = setOf(1, 2))
        assertEquals(2, grid.pageCount)
        assertEquals(listOf(0, 1, 1), grid.pageOfEveryUnit())
    }

    @Test
    fun thePagesOfJoinedRowsAreReadApart() {
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
    fun noPageIsTallerThanTheScreenAndTheSongIsInItsOrder() {
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

    private fun unitSections(sectionUnits: List<List<Int>>) = sectionUnits.flatMapIndexed { section, units -> List(units.size) { section } }.toIntArray()

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
}

private const val SECTION_GAP = 20
private const val ROW_GAP = 40
