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
    fun aSectionIsOnlyCutWithTwoLinesOnEitherSide() {
        assertContentEquals(intArrayOf(0, 2), sectionChunkStarts(listOf(CONTENT, CONTENT, CONTENT, CONTENT)))
        assertContentEquals(intArrayOf(0), sectionChunkStarts(listOf(CONTENT, CONTENT, CONTENT)))
        assertContentEquals(intArrayOf(0, 2, 3, 4), sectionChunkStarts(List(6) { CONTENT }))
    }

    @Test
    fun noPieceStartsWithAnEmptyLineOrEndsWithAComment() {
        assertContentEquals(intArrayOf(0, 3), sectionChunkStarts(listOf(CONTENT, CONTENT, BLANK, CONTENT, CONTENT)))
        assertContentEquals(intArrayOf(0, 2), sectionChunkStarts(listOf(CONTENT, CONTENT, COMMENT, CONTENT, CONTENT)))
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
        val grid = cut(sectionUnits = listOf(listOf(100, 100, 100, 100)), maxColumnCount = 2, maxRowHeight = 300)
        assertContentEquals(intArrayOf(2), grid.columnCounts)
        assertContentEquals(intArrayOf(0, 0, 1, 1), grid.columns)
    }

    @Test
    fun aSectionThatFitsTheScreenIsOnlyCutWhereEverySectionMayBe() {
        val sections = listOf(listOf(100, 100, 100, 100))
        assertFalse(cutsAnySection(cut(sectionUnits = sections, maxColumnCount = 2, maxRowHeight = 500), unitSections(sections)))
        assertTrue(cutsAnySection(cut(sectionUnits = sections, maxColumnCount = 2, maxRowHeight = 500, cutsEverySection = true), unitSections(sections)))
    }

    @Test
    fun aSectionIsOnlyCutWhereItMayBe() {
        // Six lines, which may only be cut in front of the fifth: the two columns are four lines and two.
        val grid = cut(sectionUnits = listOf(List(6) { 100 }), maxColumnCount = 2, maxRowHeight = 500, isCuttableBefore = { it == 4 })
        assertContentEquals(intArrayOf(0, 0, 0, 0, 1, 1), grid.columns)
    }

    @Test
    fun sectionsThatFitTheColumnsWholeAreNotCut() {
        val sections = listOf(listOf(100), listOf(100), listOf(100), listOf(100))
        listOf(false, true).forEach { cutsEverySection ->
            val grid = cut(sectionUnits = sections, maxColumnCount = 2, maxRowHeight = 300, cutsEverySection = cutsEverySection)
            assertFalse(cutsAnySection(grid, unitSections(sections)))
        }
    }

    @Test
    fun aCutThatSavesLittleIsNotMade() {
        // Cutting the first section would even the two columns out by a few pixels.
        val sections = listOf(listOf(100, 100), listOf(150))
        val grid = cut(sectionUnits = sections, maxColumnCount = 2, maxRowHeight = 1000, cutsEverySection = true)
        assertFalse(cutsAnySection(grid, unitSections(sections)))
        assertContentEquals(intArrayOf(2), grid.columnCounts)
    }

    @Test
    fun cutRowsAreNeverTallerThanTheScreenAndNoPieceLeavesItsRow() {
        val random = Random(7)
        repeat(300) {
            val sections = List(random.nextInt(1, 12)) { List(random.nextInt(1, 6)) { random.nextInt(40, 200) } }
            val maxRowHeight = random.nextInt(250, 700)
            val maxColumnCount = random.nextInt(2, 4)
            val units = unitSections(sections)
            val padding = IntArray(sections.size) { if (random.nextBoolean()) 12 else 0 }
            val grid = cut(sections, maxColumnCount, maxRowHeight, padding, cutsEverySection = random.nextBoolean())
            val heights = sections.flatten().toIntArray()
            grid.columnCounts.forEachIndexed { row, columnCount ->
                val rowUnits = units.indices.filter { grid.rows[it] == row }
                assertEquals((0 until columnCount).toList(), rowUnits.map { grid.columns[it] }.distinct())
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
            // Every section is in one row.
            sections.indices.forEach { section -> assertEquals(1, units.indices.filter { units[it] == section }.map { grid.rows[it] }.distinct().size) }
        }
    }

    private fun unitSections(sectionUnits: List<List<Int>>) = sectionUnits.flatMapIndexed { section, units -> List(units.size) { section } }.toIntArray()

    private fun cut(
        sectionUnits: List<List<Int>>,
        maxColumnCount: Int,
        maxRowHeight: Int,
        piecePadding: IntArray = IntArray(sectionUnits.size),
        cutsEverySection: Boolean = false,
        isCuttableBefore: (Int) -> Boolean = { true },
    ): SectionGrid {
        val heights = sectionUnits.flatten()
        val sectionStarts = IntArray(sectionUnits.size + 1).also { starts ->
            sectionUnits.forEachIndexed { section, units -> starts[section + 1] = starts[section] + units.size }
        }
        return flowIntoRowsCuttingSections(
            sectionStarts = sectionStarts,
            maxColumnCount = maxColumnCount,
            heightAt = { unit, _ -> heights[unit] },
            isCuttableBefore = isCuttableBefore,
            wideHeightAt = { null },
            piecePadding = piecePadding,
            sectionGap = SECTION_GAP,
            rowGap = ROW_GAP,
            maxRowHeight = maxRowHeight,
            minCutSaving = 50,
            cutsEverySection = cutsEverySection,
        )
    }
}

private const val SECTION_GAP = 20
private const val ROW_GAP = 40
