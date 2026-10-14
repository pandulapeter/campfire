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

import com.pandulapeter.campfire.presentation.ui.songView.grid.ColumnSpread
import com.pandulapeter.campfire.presentation.ui.songView.grid.columnKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColumnCenteringHoldTest {

    @Test
    fun `the first keys are not a change`() {
        assertFalse(FoldKeyWatch().hasChanged(listOf(setOf("verse#1"), true)))
    }

    @Test
    fun `different keys are a change`() {
        val watch = FoldKeyWatch()
        watch.hasChanged(listOf(setOf("verse#1"), true))
        assertTrue(watch.hasChanged(listOf(setOf("verse#1", "chorus#1"), true)))
    }

    @Test
    fun `equal keys are not a change, even in new instances`() {
        val watch = FoldKeyWatch()
        watch.hasChanged(listOf(setOf("verse#1"), null))
        assertFalse(watch.hasChanged(listOf(mutableSetOf("verse#1"), null)))
    }

    @Test
    fun `a change back to the first keys is a change`() {
        val watch = FoldKeyWatch()
        watch.hasChanged(listOf(emptySet<String>(), false))
        watch.hasChanged(listOf(setOf("verse#1"), false))
        assertTrue(watch.hasChanged(listOf(emptySet<String>(), false)))
    }

    @Test
    fun `nothing is held while not holding`() {
        val hold = recordedHold()
        assertNull(hold.heldSpreads(intArrayOf(2, 1), BooleanArray(2)))
    }

    @Test
    fun `nothing is held for rows of other column counts`() {
        val hold = recordedHold().apply { isHolding = true }
        assertNull(hold.heldSpreads(intArrayOf(1, 2), BooleanArray(2)))
    }

    @Test
    fun `the recorded spreads are held for the same rows`() {
        val hold = recordedHold().apply { isHolding = true }
        assertEquals(setOf(columnKey(0, 0), columnKey(0, 1)), hold.heldSpreads(intArrayOf(2, 1), BooleanArray(2))?.keys)
    }

    @Test
    fun `nothing is held for rows paired into other pages`() {
        val hold = recordedHold().apply { isHolding = true }
        assertNull(hold.heldSpreads(intArrayOf(2, 1), booleanArrayOf(false, true)))
        assertNull(hold.heldPageShifts(intArrayOf(2, 1), booleanArrayOf(false, true)))
    }

    @Test
    fun `the recorded page shifts are held for the same rows`() {
        val hold = recordedHold().apply { isHolding = true }
        assertEquals(mapOf(0 to 30), hold.heldPageShifts(intArrayOf(2, 1), BooleanArray(2)))
    }

    private fun recordedHold() = ColumnCenteringHold().apply {
        spreads = mapOf(columnKey(0, 0) to ColumnSpread(top = 10, extraGap = 2), columnKey(0, 1) to ColumnSpread(top = 20, extraGap = 0))
        pageShifts = mapOf(0 to 30)
        columnCounts = intArrayOf(2, 1)
        joinsPrevious = BooleanArray(2)
    }
}
