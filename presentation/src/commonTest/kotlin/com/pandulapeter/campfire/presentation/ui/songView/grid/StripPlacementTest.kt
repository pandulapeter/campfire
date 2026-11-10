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

import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StripPlacementTest {

    /** Three sections of one unit each, one a column, two columns of 400 on screen. */
    private val grid = SectionGrid(rows = IntArray(3), columns = intArrayOf(0, 1, 2), columnCounts = intArrayOf(3), stripColumns = 2)
    private val units = songUnitsOf(List(3) { listOf(listOf(100)) })
    private val widths = SectionWidths(
        columnWidths = IntArray(3) { 400 },
        cellWidths = arrayOf(IntArray(3) { 400 }),
        slotWidths = arrayOf(IntArray(3) { 400 }),
        unitWidths = IntArray(3) { 400 },
    )

    private fun place(layoutDirection: LayoutDirection, isInsetAtLeft: Boolean = false, isInset: Boolean = true) = placeStrip(
        grid = grid,
        widths = widths,
        unitHeights = IntArray(3) { 100 },
        units = units,
        piecePadding = IntArray(3),
        width = 1_000,
        isInset = isInset,
        endInsetPx = 50,
        columnGapPx = 20,
        sectionGapPx = SECTION_GAP,
        rowGapPx = ROW_GAP,
        layoutDirection = layoutDirection,
        isInsetAtLeft = isInsetAtLeft,
    )

    @Test
    fun `a strip's columns are each centered in a slot of the view, and it ends at the first stop showing its last column`() {
        val placement = place(LayoutDirection.Ltr)
        // What the buttons and a gap before them leave of the viewport, 930, and a gap past it is a slot of 475 for each
        // of the two columns on screen, each centered in its own less the gap, so the third one starts past what is in view.
        assertEquals(listOf(27, 502, 977), placement.positions.map { it.x })
        assertTrue(502 + 400 <= 930 && 977 > 930)
        // The second stop already shows the third column, so it is the last, and the scroll ends there.
        assertEquals(listOf(0, 475), placement.columnStops)
        assertEquals(1_000 + 475, placement.width)
        assertTrue(977 + 400 - 475 <= 930)
        assertTrue(placement.positions.all { it.y == 0 })
    }

    @Test
    fun `a column wider than a slot takes whole slots, centered in them, so the next one still starts past the view`() {
        val columnWidths = intArrayOf(280, 500, 280, 280)
        val placement = placeStrip(
            grid = SectionGrid(rows = IntArray(4), columns = intArrayOf(0, 1, 2, 3), columnCounts = intArrayOf(4), stripColumns = 3),
            widths = SectionWidths(columnWidths = columnWidths, cellWidths = arrayOf(columnWidths), slotWidths = arrayOf(columnWidths), unitWidths = columnWidths),
            unitHeights = IntArray(4) { 100 },
            units = songUnitsOf(List(4) { listOf(listOf(100)) }),
            piecePadding = IntArray(4),
            width = 1_000,
            isInset = true,
            endInsetPx = 50,
            columnGapPx = 20,
            sectionGapPx = SECTION_GAP,
            rowGapPx = ROW_GAP,
            layoutDirection = LayoutDirection.Ltr,
            isInsetAtLeft = false,
        )
        // A view of 930 and a gap is three slots of 316: the wide column is centered in two of them, and the one after it
        // starts past the view rather than in it.
        assertEquals(listOf(8, 372, 956, 1_272), placement.positions.map { it.x })
        assertTrue(956 > 930)
        assertEquals(listOf(0, 316, 948), placement.columnStops)
    }

    @Test
    fun `a strip read from the right is the one read from the left mirrored, its stops counted from its start edge`() {
        val ltr = place(LayoutDirection.Ltr)
        // A song and a screen both read from the right: the buttons, at the screen's end, are on the left.
        val rtl = place(LayoutDirection.Rtl, isInsetAtLeft = true)
        assertEquals(ltr.columnStops, rtl.columnStops)
        assertEquals(ltr.positions.map { ltr.width - it.x - 400 }, rtl.positions.map { it.x })
        val rows = stripRowsOf(grid, units, rtl.columnStops, readsLeftwards = true)
        assertTrue(rows.isAcross && rows.readsLeftwards)
        assertEquals(listOf(0, 1), rows.stepSections)
    }

    @Test
    fun `a strip read from the right on a screen read from the left leaves its gap at the left, where it reads on to`() {
        // With the step buttons in a row under it, nothing is at the screen's end, so only the song's direction counts.
        val ltr = place(LayoutDirection.Ltr, isInset = false)
        val rtl = place(LayoutDirection.Rtl, isInset = false)
        assertEquals(ltr.columnStops, rtl.columnStops)
        assertEquals(ltr.positions.map { ltr.width - it.x - 400 }, rtl.positions.map { it.x })
        // The two columns in view, at the end of a reversed scroll, end further from the left edge than the right one.
        val shown = rtl.positions.take(2).map { it.x - (rtl.width - 1_000) }
        assertTrue(1_000 - (shown[0] + 400) < shown[1])
    }
}
