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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StripFlowTest {

    /** Three sections of four one-line units, each 10 tall, every unit after a section's first one a place to cut. */
    private val sectionStarts = intArrayOf(0, 4, 8, 12)

    private fun strip(
        maxColumnHeight: Int,
        heights: IntArray = IntArray(12) { 10 },
        timingStarts: IntArray = intArrayOf(0),
    ) = flowIntoStrip(
        sectionStarts = sectionStarts,
        timingStarts = timingStarts,
        heights = heights,
        isCuttableBefore = { it !in sectionStarts },
        piecePadding = IntArray(3),
        sectionGap = 5,
        maxColumnHeight = maxColumnHeight,
        lineHeight = 10,
        lineCount = { 1 },
        isStanzaBreakBefore = { false },
    )

    @Test
    fun `a section that fits a column whole is not cut where that takes no extra column`() {
        val strip = assertNotNull(strip(maxColumnHeight = 45))
        assertEquals(3, strip.columnCount)
        assertEquals(listOf(0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2), strip.columns.toList())
    }

    @Test
    fun `every column is filled to the screen, a section running on into the next one`() {
        val strip = assertNotNull(strip(maxColumnHeight = 65))
        assertEquals(2, strip.columnCount)
        // The second section is cut, its first half under the first section and the rest over the third.
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1), strip.columns.toList())
    }

    @Test
    fun `a change of tempo or time starts a column of its own`() {
        val strip = assertNotNull(strip(maxColumnHeight = 1_000, timingStarts = intArrayOf(0, 1)))
        assertEquals(2, strip.columnCount)
        assertEquals(listOf(0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1), strip.columns.toList())
    }

    @Test
    fun `something too tall for a column that cannot be cut leaves no strip`() {
        val heights = IntArray(12) { 10 }.also { it[4] = 100 }
        assertNull(strip(maxColumnHeight = 65, heights = heights))
    }
}
