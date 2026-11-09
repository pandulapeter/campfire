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

    private fun place(layoutDirection: LayoutDirection, isInsetAtLeft: Boolean = false) = placeStrip(
        grid = grid,
        widths = widths,
        unitHeights = IntArray(3) { 100 },
        units = units,
        piecePadding = IntArray(3),
        width = 1_000,
        isInset = true,
        endInsetPx = 50,
        columnGapPx = 20,
        sectionGapPx = SECTION_GAP,
        rowGapPx = ROW_GAP,
        layoutDirection = layoutDirection,
        isInsetAtLeft = isInsetAtLeft,
    )

    @Test
    fun `a strip's columns are each centered in a slot of the view, the last ones reached at the end of its scroll`() {
        val placement = place(LayoutDirection.Ltr)
        // What the buttons leave of the viewport, 950, is a slot of 475 for each of the two columns on screen, each
        // centered in its own, so the third one starts past the edge of what is in view.
        assertEquals(listOf(0, 475, 950), placement.columnStops)
        assertEquals(listOf(37, 512, 987), placement.positions.map { it.x })
        assertEquals(3 * 475 + 50, placement.width)
        val maxScroll = placement.width - 1_000
        assertTrue(987 + 400 - maxScroll <= 950)
        assertTrue(512 + 400 <= 950 && 987 >= 950)
        assertTrue(placement.positions.all { it.y == 0 })
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
        assertEquals(listOf(0, 1, 2), rows.stepSections)
    }
}
