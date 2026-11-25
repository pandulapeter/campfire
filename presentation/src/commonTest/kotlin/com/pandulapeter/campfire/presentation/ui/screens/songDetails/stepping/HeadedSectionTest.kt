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
        assertEquals(5, sectionAt(650, rows, maxValue = 650))
    }

    @Test
    fun `a song not laid out yet heads nothing`() = assertNull(sectionAt(0, SongRows()))
}
