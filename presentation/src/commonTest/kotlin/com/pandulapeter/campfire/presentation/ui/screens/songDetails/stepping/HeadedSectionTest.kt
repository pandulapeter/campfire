/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping

import com.pandulapeter.campfire.presentation.ui.songView.SongRows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class HeadedSectionTest {

    @Test
    fun `a column of lines is headed by the section of the first line at the top of the screen`() {
        val rows = SongRows(lineTops = listOf(0, 40, 80, 120), lineBottoms = listOf(40, 80, 120, 160), lineSections = listOf(0, 0, 1, 2))
        assertEquals(0, sectionAt(0, rows))
        assertEquals(1, sectionAt(60, rows))
        assertEquals(1, sectionAt(80, rows))
        assertEquals(2, sectionAt(500, rows))
    }

    @Test
    fun `rows or columns are headed by the section their stop starts with`() {
        val rows = SongRows(stepOffsets = listOf(0, 300, 600), stepSections = listOf(0, 3, 5))
        assertEquals(0, sectionAt(100, rows))
        assertEquals(3, sectionAt(300, rows))
        assertEquals(5, sectionAt(650, rows))
    }

    @Test
    fun `a song not laid out yet heads nothing`() = assertNull(sectionAt(0, SongRows()))

    @Test
    fun `a column of lines shows the sections of every line starting on the screen`() {
        val rows = SongRows(lineTops = listOf(0, 40, 80, 120), lineBottoms = listOf(40, 80, 120, 160), lineSections = listOf(0, 0, 1, 2))
        assertEquals(0..1, sectionsOnScreen(0, 100, rows))
        assertEquals(1..2, sectionsOnScreen(80, 100, rows))
    }

    @Test
    fun `a page shows the sections of its stops up to the next stop's`() {
        val rows = SongRows(stepOffsets = listOf(0, 300, 600), stepSections = listOf(0, 3, 5))
        assertEquals(0..2, sectionsOnScreen(0, 300, rows))
        assertEquals(5..Int.MAX_VALUE, sectionsOnScreen(600, 300, rows, maxValue = 600))
    }

    @Test
    fun `a section split over two pages is on both`() {
        val rows = SongRows(stepOffsets = listOf(0, 300, 600), stepSections = listOf(0, 0, 2))
        assertEquals(0..0, sectionsOnScreen(0, 300, rows))
        assertEquals(0..1, sectionsOnScreen(300, 300, rows))
    }

    @Test
    fun `a single column that fits the screen shows every section`() {
        // The scroll's padding puts the first line below the top of the screen, and there is nothing to scroll.
        val rows = SongRows(
            stepOffsets = listOf(0, 160, 320),
            stepSections = listOf(0, 1, 2),
            lineTops = listOf(120, 200, 280, 360),
            lineBottoms = listOf(200, 280, 360, 440),
            lineSections = listOf(0, 1, 1, 2),
        )
        assertEquals(0..Int.MAX_VALUE, sectionsOnScreen(0, 600, rows, maxValue = 0))
    }

    @Test
    fun `columns of a single row that fits the screen show every section`() {
        // Every column's first section is stepped to at the top of the song.
        val rows = SongRows(stepOffsets = listOf(0, 200, 0, 150), stepSections = listOf(0, 1, 2, 3))
        assertEquals(0..Int.MAX_VALUE, sectionsOnScreen(0, 600, rows, maxValue = 0))
    }

    @Test
    fun `a strip that fits the screen shows every section`() {
        val rows = SongRows(stepOffsets = listOf(0, 300, 600), stepSections = listOf(0, 2, 4), isSteppedByRow = true, isAcross = true)
        assertEquals(0..Int.MAX_VALUE, sectionsOnScreen(0, 900, rows, maxValue = 0))
    }

    @Test
    fun `a strip scrolled to its end shows every column on the screen`() {
        val rows = SongRows(stepOffsets = listOf(0, 300, 600, 900), stepSections = listOf(0, 2, 4, 6), isSteppedByRow = true, isAcross = true)
        assertEquals(4..Int.MAX_VALUE, sectionsOnScreen(600, 600, rows, maxValue = 600))
    }

    @Test
    fun `rows over a tall last row are read by their stops until its lines begin`() {
        val rows = SongRows(
            stepOffsets = listOf(0, 470, 942),
            stepSections = listOf(0, 2, 4),
            isSteppedByRow = true,
            lineTops = listOf(942, 1002, 1026),
            lineBottoms = listOf(1002, 1026, 1050),
            lineSections = listOf(4, 4, 5),
        )
        assertEquals(0, sectionAt(0, rows))
        assertEquals(2, sectionAt(470, rows))
        assertEquals(0..1, sectionsOnScreen(0, 470, rows))
        assertEquals(4, sectionAt(1002, rows))
        assertEquals(4..5, sectionsOnScreen(942, 470, rows))
    }
}
