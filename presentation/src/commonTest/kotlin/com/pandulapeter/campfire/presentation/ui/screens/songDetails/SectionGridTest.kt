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

class SectionGridTest {

    @Test
    fun songThatFitsTheScreenInColumnsStillFitsIt() {
        // The sections of a song of short verses, two long bridges and a few choruses, which three columns read top to
        // bottom hold on one screen while no row of them is ever as tall as its columns would have to be.
        val heights = listOf(80, 110, 110, 200, 170, 60, 110, 110, 200, 170, 170, 110)
        val columns = heights.balanceIntoColumns(3, SECTION_GAP)
        assertTrue(columns.height(heights) <= 900)
        assertTrue(flow(heights, maxColumnCount = 3, maxRowHeight = 900).height(heights) <= columns.height(heights))
    }

    @Test
    fun songThatFitsTheScreenOnlyInColumnsIsOneRow() {
        val heights = List(9) { 280 }
        val grid = flow(heights, maxColumnCount = 3, maxRowHeight = 900)
        assertContentEquals(intArrayOf(3), grid.columnCounts)
        assertContentEquals(intArrayOf(0, 0, 0, 1, 1, 1, 2, 2, 2), grid.columns)
    }

    @Test
    fun sectionTallerThanTheScreenHasARowOfItsOwn() {
        val heights = listOf(100, 1200, 100, 100)
        val grid = flow(heights, maxColumnCount = 3, maxRowHeight = 900)
        val row = grid.rows[1]
        assertEquals(listOf(1), heights.indices.filter { grid.rows[it] == row })
        assertEquals(1, grid.columnCounts[row])
    }

    @Test
    fun firstRowLeavesRoomForTheHeader() {
        // Three columns of three hold the song on a screen of 900, but not under a header that takes 300 of it.
        val heights = List(9) { 280 }
        val grid = flow(heights, maxColumnCount = 3, maxRowHeight = 900, maxFirstRowHeight = 600)
        val firstRow = heights.indices.filter { grid.rows[it] == 0 }
        assertTrue(firstRow.size > 1)
        val firstRowHeight = SectionGrid(IntArray(firstRow.size), IntArray(firstRow.size) { grid.columns[firstRow[it]] }, intArrayOf(grid.columnCounts[0]))
            .height(firstRow.map { heights[it] })
        assertTrue(firstRowHeight <= 600, "The first row is $firstRowHeight tall, more than 600")
    }

    @Test
    fun balancedColumnsStayEven() {
        val grid = List(7) { 100 }.balanceIntoColumns(3, SECTION_GAP)
        assertEquals(listOf(2, 2, 3), grid.columns.toList().groupingBy { it }.eachCount().values.sorted())
    }

    @Test
    fun rowsAreNeverTallerThanTheScreenNorTallerThanTheColumnsTopToBottom() {
        val random = Random(42)
        repeat(500) {
            val heights = List(random.nextInt(1, 25)) { random.nextInt(40, 500) }
            val maxRowHeight = random.nextInt(300, 1200)
            val maxFirstRowHeight = maxRowHeight - random.nextInt(0, 300)
            val maxColumnCount = random.nextInt(1, 5).coerceAtMost(heights.size)
            val grid = flow(heights, maxColumnCount = maxColumnCount, maxRowHeight = maxRowHeight, maxFirstRowHeight = maxFirstRowHeight)
            grid.columnCounts.forEachIndexed { row, columnCount ->
                val sections = heights.indices.filter { grid.rows[it] == row }
                // No row has a hole where a column should be.
                assertEquals((0 until columnCount).toList(), sections.map { grid.columns[it] }.distinct())
                if (sections.size > 1) {
                    val rowHeight = SectionGrid(IntArray(sections.size), IntArray(sections.size) { grid.columns[sections[it]] }, intArrayOf(columnCount))
                        .height(sections.map { heights[it] })
                    val limit = if (row == 0) maxFirstRowHeight else maxRowHeight
                    assertTrue(rowHeight <= limit, "Row $row of $columnCount columns is $rowHeight tall, more than $limit")
                }
            }
            val columns = heights.balanceIntoColumns(maxColumnCount, SECTION_GAP)
            if (columns.height(heights) <= maxFirstRowHeight) assertTrue(grid.height(heights) <= columns.height(heights))
        }
    }

    private fun flow(heights: List<Int>, maxColumnCount: Int, maxRowHeight: Int, maxFirstRowHeight: Int = maxRowHeight) = flowIntoRows(
        sectionCount = heights.size,
        maxColumnCount = maxColumnCount,
        heightAt = { index, _ -> heights[index] },
        wideHeightAt = { null },
        sectionGap = SECTION_GAP,
        rowGap = ROW_GAP,
        maxRowHeight = maxRowHeight,
        maxFirstRowHeight = maxFirstRowHeight,
    )

    private fun SectionGrid.height(heights: List<Int>) = arrange(heights.toIntArray(), SECTION_GAP, ROW_GAP).height

    private companion object {
        const val SECTION_GAP = 20
        const val ROW_GAP = 40
    }
}
