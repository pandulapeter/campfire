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

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SectionPlacementTest {

    private val oneRowOfTwo = SectionGrid(rows = intArrayOf(0, 0), columns = intArrayOf(0, 1), columnCounts = intArrayOf(2))

    private fun cellStarts(
        grid: SectionGrid,
        cellWidths: Array<IntArray>,
        width: Int = 1_000,
        layoutWidth: Int = width,
        isInset: Boolean = false,
        endInset: Int = 0,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) = cellStartsOf(grid, cellWidths, width, layoutWidth, isInset, endInset, SECTION_GAP, layoutDirection)

    private fun place(
        grid: SectionGrid,
        units: SongUnits,
        unitHeights: IntArray,
        cellWidths: Array<IntArray>,
        unitWidths: IntArray,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        piecePadding: IntArray = IntArray(units.sectionStarts.size - 1),
    ) = placeSections(
        grid = grid,
        widths = SectionWidths(columnWidths = unitWidths, cellWidths = cellWidths, unitWidths = unitWidths),
        unitHeights = unitHeights,
        dividerHeight = 0,
        units = units,
        piecePadding = piecePadding,
        width = 1_000,
        layoutWidth = 1_000,
        isInset = false,
        endInsetPx = 0,
        columnGapPx = SECTION_GAP,
        sectionGapPx = SECTION_GAP,
        rowGapPx = ROW_GAP,
        availableHeightPx = 2_000,
        rowViewportHeight = null,
        rowViewportTopPaddingPx = 0,
        rowViewportBottomPaddingPx = 0,
        layoutDirection = layoutDirection,
        lineHeightPx = LINE_HEIGHT,
        heldSpreads = null,
        heldPageShifts = null,
    )

    @Test
    fun `the columns of a row are spread evenly across the width`() {
        val starts = cellStarts(oneRowOfTwo, arrayOf(intArrayOf(100, 200))).single()
        val before = starts[0]
        val between = starts[1] - (starts[0] + 100)
        val after = 1_000 - (starts[1] + 200)
        assertTrue(abs(before - between) <= 1 && abs(after - between) <= 1, starts.toList().toString())
    }

    @Test
    fun `a card narrower than its cell sits at the cell's start, mirrored in a right to left layout`() {
        val units = songUnitsOf(listOf(listOf(listOf(100)), listOf(listOf(100))), cardSections = setOf(0))
        val cellWidths = arrayOf(intArrayOf(300, 300))
        val unitWidths = intArrayOf(200, 300)
        val starts = cellStarts(oneRowOfTwo, cellWidths).single()
        val ltr = place(oneRowOfTwo, units, intArrayOf(100, 100), cellWidths, unitWidths)
        assertEquals(starts[0], ltr.positions[0].x)
        val rtl = place(oneRowOfTwo, units, intArrayOf(100, 100), cellWidths, unitWidths, layoutDirection = LayoutDirection.Rtl)
        assertEquals(starts[0] + 300 - 200, rtl.positions[0].x)
        assertEquals(ltr.positions[1], rtl.positions[1])
    }

    @Test
    fun `an inset row keeps out of the step buttons' edge only where it would reach under them`() {
        val narrow = SectionGrid(rows = intArrayOf(0), columns = intArrayOf(0), columnCounts = intArrayOf(1))
        assertEquals(
            cellStarts(narrow, arrayOf(intArrayOf(400))).single().toList(),
            cellStarts(narrow, arrayOf(intArrayOf(400)), layoutWidth = 900, isInset = true, endInset = 100).single().toList(),
        )
        val wide = cellStarts(oneRowOfTwo, arrayOf(intArrayOf(400, 400)), layoutWidth = 900, isInset = true, endInset = 100).single()
        assertTrue(wide[1] + 400 <= 900, wide.toList().toString())
        val rtl = cellStarts(oneRowOfTwo, arrayOf(intArrayOf(400, 400)), layoutWidth = 900, isInset = true, endInset = 100, layoutDirection = LayoutDirection.Rtl).single()
        assertTrue(rtl[0] >= 100, rtl.toList().toString())
    }

    @Test
    fun `the pages of a single column share one start edge, and a pair of narrow sections splits it in the middle`() {
        val grid = SectionGrid(
            rows = intArrayOf(0, 1, 2, 2),
            columns = intArrayOf(0, 0, 0, 1),
            columnCounts = intArrayOf(1, 1, 2),
            sharesKeyline = true,
        )
        val starts = cellStarts(grid, arrayOf(intArrayOf(300), intArrayOf(300), intArrayOf(100, 100)))
        assertEquals(starts[0][0], starts[1][0])
        assertEquals(starts[0][0], starts[2][0])
        assertEquals(starts[0][0] + (300 + SECTION_GAP) / 2, starts[2][1])
    }

    @Test
    fun `a card cut across two columns is two cards, each padded at the cut`() {
        val units = songUnitsOf(listOf(listOf(listOf(100), listOf(100))), cardSections = setOf(0))
        val positions = arrayOf(IntOffset(0, 0), IntOffset(500, 0))
        val cards = cardBoundsOf(oneRowOfTwo, units, positions, unitHeights = intArrayOf(100, 100), unitWidths = intArrayOf(200, 200), piecePadding = intArrayOf(10))
        assertEquals(listOf(0 to IntRect(0, 0, 200, 110), 1 to IntRect(500, -10, 700, 100)), cards)
    }

    @Test
    fun `a song stepped by its sections stops a fade above each one`() {
        val grid = SectionGrid(rows = intArrayOf(0, 0), columns = intArrayOf(0, 0), columnCounts = intArrayOf(1))
        val units = songUnitsOf(listOf(listOf(listOf(100)), listOf(listOf(100))))
        val arrangement = grid.arrange(intArrayOf(100, 100), SECTION_GAP, ROW_GAP)
        val rows = songRowsOf(grid, units, arrangement, intArrayOf(100, 100), dividerHeight = 0, isScrolledByRow = false, fadePx = 30)
        assertEquals(listOf(arrangement.tops[0] - 30, arrangement.tops[1] - 30), rows.stepOffsets)
        assertEquals(listOf(0, 1), rows.stepSections)
    }

    @Test
    fun `a song read in rows rests at the top and under every divider`() {
        val grid = SectionGrid(rows = intArrayOf(0, 1), columns = intArrayOf(0, 0), columnCounts = intArrayOf(2, 2))
        val units = songUnitsOf(listOf(listOf(listOf(100)), listOf(listOf(100))))
        val arrangement = grid.arrange(intArrayOf(100, 100), SECTION_GAP, ROW_GAP)
        val rows = songRowsOf(grid, units, arrangement, intArrayOf(100, 100), dividerHeight = 4, isScrolledByRow = true, fadePx = 30)
        assertEquals(listOf(0) + arrangement.dividerTops.map { it + 2 }, rows.restingOffsets)
        assertEquals(rows.restingOffsets, rows.stepOffsets)
        assertTrue(rows.isSteppedByRow)
    }
}
