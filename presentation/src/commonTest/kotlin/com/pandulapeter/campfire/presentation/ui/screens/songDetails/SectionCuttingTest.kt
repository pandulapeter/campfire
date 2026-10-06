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

import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SectionItemKind.BLANK
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SectionItemKind.COMMENT
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SectionItemKind.CONTENT
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SectionCuttingTest {

    @Test
    fun aSectionIsCutWithALineOnEitherSide() {
        assertContentEquals(intArrayOf(0, 1), sectionChunkStarts(listOf(CONTENT, CONTENT)))
        assertContentEquals(intArrayOf(0), sectionChunkStarts(listOf(CONTENT)))
        assertContentEquals(intArrayOf(0, 1, 2, 3, 4, 5), sectionChunkStarts(List(6) { CONTENT }))
        assertContentEquals(intArrayOf(0, 1), sectionChunkStarts(listOf(CONTENT, CONTENT, BLANK)))
    }

    @Test
    fun noPieceStartsWithAnEmptyLineOrEndsWithAComment() {
        assertContentEquals(intArrayOf(0, 1, 3, 4), sectionChunkStarts(listOf(CONTENT, CONTENT, BLANK, CONTENT, CONTENT)))
        assertContentEquals(intArrayOf(0, 1, 2, 4), sectionChunkStarts(listOf(CONTENT, CONTENT, COMMENT, CONTENT, CONTENT)))
    }

    @Test
    fun thePiecesOfASectionOnACardHaveTheCardsPaddingWhereItIsCut() {
        // A section on a card cut into two columns, and a section after it under its second piece.
        val grid = SectionGrid(rows = IntArray(3), columns = intArrayOf(0, 1, 1), columnCounts = intArrayOf(2))
        val arrangement = grid.arrange(
            heights = intArrayOf(100, 100, 50),
            sectionGap = 20,
            rowGap = 40,
            unitSections = intArrayOf(0, 0, 1),
            piecePadding = intArrayOf(10, 0),
        )
        // The first piece ends 10 below its chunk, the second starts 10 below the top of its column.
        assertContentEquals(intArrayOf(0, 10, 130), arrangement.tops)
        assertEquals(180, arrangement.height)
    }

    @Test
    fun theChunksOfAWholeSectionAreStackedWithoutGaps() {
        val grid = SectionGrid(rows = IntArray(3), columns = IntArray(3), columnCounts = intArrayOf(1))
        val arrangement = grid.arrange(intArrayOf(100, 100, 50), sectionGap = 20, rowGap = 40, unitSections = intArrayOf(0, 0, 1), piecePadding = intArrayOf(10, 0))
        assertContentEquals(intArrayOf(0, 100, 220), arrangement.tops)
        assertEquals(270, arrangement.height)
    }

    @Test
    fun aSectionTallerThanTheScreenIsCutIntoTheColumnsOfItsRow() {
        val grid = flow(sectionUnits = listOf(listOf(100, 100, 100, 100)), columnCount = 2, maxRowHeight = 300)
        assertContentEquals(intArrayOf(2), grid.columnCounts)
        assertContentEquals(intArrayOf(0, 0, 1, 1), grid.columns)
    }

    @Test
    fun aSectionIsOnlyCutWhereItMayBe() {
        // Six lines, which may only be cut in front of the fifth: the two columns are four lines and two.
        val grid = flow(sectionUnits = listOf(List(6) { 100 }), columnCount = 2, maxRowHeight = 500, isCuttableBefore = { it == 4 })
        assertContentEquals(intArrayOf(0, 0, 0, 0, 1, 1), grid.columns)
    }

    @Test
    fun sectionsThatFitTheColumnsWholeAreNotCut() {
        val sections = listOf(listOf(100), listOf(100), listOf(100), listOf(100))
        assertFalse(cutsAnySection(flow(sectionUnits = sections, columnCount = 2, maxRowHeight = 300), unitSections(sections)))
    }

    @Test
    fun aSectionIsCutToEvenTheColumnsOut() {
        // Kept whole, the second section would leave the first column 250 tall and the second 400.
        val sections = listOf(listOf(250), listOf(100, 100, 100, 100))
        val grid = flow(sectionUnits = sections, columnCount = 2, maxRowHeight = 500)
        assertTrue(cutsAnySection(grid, unitSections(sections)))
        assertContentEquals(intArrayOf(2), grid.columnCounts)
    }

    @Test
    fun aSectionRunsOnFromTheLastColumnOfARowIntoTheNextRow() {
        val sections = List(3) { List(4) { 100 } }
        val grid = flow(sectionUnits = sections, columnCount = 2, maxRowHeight = 300)
        val units = unitSections(sections)
        assertTrue((1 until units.size).any { units[it] == units[it - 1] && grid.rows[it] != grid.rows[it - 1] })
    }

    @Test
    fun pagesAreFilledRatherThanEndedWithASection() {
        // Six sections of five lines, of which a column holds one and a half: whole, a row holds two of them, three pages
        // in all, while running them on from column to column holds the song on two.
        val sections = List(6) { List(5) { 60 } }
        val grid = flow(sectionUnits = sections, columnCount = 2, maxRowHeight = 500)
        assertEquals(2, grid.columnCounts.size)
        assertEquals(2, grid.pageCount(sections.flatten().toIntArray(), SECTION_GAP, 500, unitSections(sections), IntArray(sections.size)))
    }

    @Test
    fun theLastRowTakesTheFewestColumnsThatHoldIt() {
        // Two full rows of two columns, and a short section left over, which is not split across two columns.
        val sections = List(4) { List(3) { 100 } } + listOf(listOf(100))
        val grid = flow(sectionUnits = sections, columnCount = 2, maxRowHeight = 300)
        assertEquals(1, grid.columnCounts.last())
    }

    @Test
    fun anUncuttableStretchTallerThanTheScreenHasARowOfItsOwn() {
        val sections = listOf(listOf(100), listOf(800), listOf(100))
        val grid = flow(sectionUnits = sections, columnCount = 2, maxRowHeight = 500)
        val row = grid.rows[1]
        assertEquals(listOf(1), sections.indices.filter { grid.rows[it] == row })
        assertEquals(1, grid.columnCounts[row])
    }

    @Test
    fun rowsAreNeverTallerThanTheScreenAndHoldTheSongInItsOrder() {
        val random = Random(7)
        repeat(300) {
            val sections = List(random.nextInt(1, 12)) { List(random.nextInt(1, 6)) { random.nextInt(40, 200) } }
            val maxRowHeight = random.nextInt(250, 700)
            val columnCount = random.nextInt(2, 4)
            val padding = IntArray(sections.size) { if (random.nextBoolean()) 12 else 0 }
            val grid = flow(sections, columnCount, maxRowHeight, padding)
            assertRowsFitInOrder(grid, sections, maxRowHeight, padding)
        }
    }

    @Test
    fun aSongbookSizedFileIsFlowedByTheSameRules() {
        val random = Random(11)
        val sections = List(1000) { List(3) { random.nextInt(40, 200) } }
        val padding = IntArray(sections.size)
        val grid = flow(sections, columnCount = 4, maxRowHeight = 500, piecePadding = padding)
        assertRowsFitInOrder(grid, sections, maxRowHeight = 500, padding = padding)
    }

    /**
     * No row of several columns is taller than [maxRowHeight], every row fills its columns, and the song is read in its
     * order, row after row and column after column.
     */
    private fun assertRowsFitInOrder(grid: SectionGrid, sections: List<List<Int>>, maxRowHeight: Int, padding: IntArray) {
        val units = unitSections(sections)
        val heights = sections.flatten().toIntArray()
        val unitsByRow = units.indices.groupBy { grid.rows[it] }
        grid.columnCounts.forEachIndexed { row, columnCount ->
            val rowUnits = unitsByRow[row].orEmpty()
            assertEquals((0 until columnCount).toList(), rowUnits.map { grid.columns[it] }.distinct())
            assertEquals(rowUnits.map { grid.columns[it] }.sorted(), rowUnits.map { grid.columns[it] })
            if (columnCount > 1) {
                val rowGrid = SectionGrid(IntArray(rowUnits.size), IntArray(rowUnits.size) { grid.columns[rowUnits[it]] }, intArrayOf(columnCount))
                val height = rowGrid.arrange(
                    heights = IntArray(rowUnits.size) { heights[rowUnits[it]] },
                    sectionGap = SECTION_GAP,
                    rowGap = ROW_GAP,
                    unitSections = IntArray(rowUnits.size) { units[rowUnits[it]] },
                    piecePadding = padding,
                ).height
                assertTrue(height <= maxRowHeight, "Row $row of $columnCount columns is $height tall, more than $maxRowHeight")
            }
        }
        assertEquals(grid.rows.sorted(), grid.rows.toList())
    }

    /** Whether [grid], a grid of chunks, puts any section into more than one cell. */
    private fun cutsAnySection(grid: SectionGrid, unitSections: IntArray) = (1 until unitSections.size).any { unit ->
        unitSections[unit] == unitSections[unit - 1] && (grid.rows[unit] != grid.rows[unit - 1] || grid.columns[unit] != grid.columns[unit - 1])
    }

    private fun unitSections(sectionUnits: List<List<Int>>) = sectionUnits.flatMapIndexed { section, units -> List(units.size) { section } }.toIntArray()

    private fun flow(
        sectionUnits: List<List<Int>>,
        columnCount: Int,
        maxRowHeight: Int,
        piecePadding: IntArray = IntArray(sectionUnits.size),
        isCuttableBefore: (Int) -> Boolean = { true },
    ): SectionGrid {
        val heights = sectionUnits.flatten()
        val sectionStarts = IntArray(sectionUnits.size + 1).also { starts ->
            sectionUnits.forEachIndexed { section, units -> starts[section + 1] = starts[section] + units.size }
        }
        return flowLikeAMagazine(
            sectionStarts = sectionStarts,
            columnCount = columnCount,
            heightAt = { unit, _ -> heights[unit] },
            isCuttableBefore = isCuttableBefore,
            piecePadding = piecePadding,
            sectionGap = SECTION_GAP,
            maxRowHeight = maxRowHeight,
        )
    }
}

private const val SECTION_GAP = 20
private const val ROW_GAP = 40
