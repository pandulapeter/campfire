/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class DockLayoutTest {

    private val everyRow = DockRows(hasPlayingRow = true, hasChordRow = true, hasScaleRow = true)

    @Test
    fun `the smallest phone gets one row of chord chips however it is read`() {
        listOf(false to false, false to true, true to false, true to true).forEach { (turnsByColumn, hasPagerBar) ->
            val budget = dockBudgetOf(windowHeight = 640.dp, turnsByColumn = turnsByColumn, hasPagerBar = hasPagerBar)
            assertEquals(DockTier.CHORD_CHIPS, dockLayoutOf(everyRow, budget)?.tier)
        }
    }

    @Test
    fun `a taller window gets smaller diagrams and then full ones`() {
        val tiers = listOf(640, 1024, 1280).map { height ->
            dockLayoutOf(everyRow, dockBudgetOf(windowHeight = height.dp, turnsByColumn = false, hasPagerBar = false))?.tier
        }
        assertEquals(listOf(DockTier.CHORD_CHIPS, DockTier.SMALL_DIAGRAMS, DockTier.FULL_DIAGRAMS), tiers)
    }

    @Test
    fun `turned by column the dock takes less room than turned by page and less again over a pager bar`() {
        listOf(false, true).forEach { hasPagerBar ->
            assertTrue(dockBudgetOf(640.dp, turnsByColumn = true, hasPagerBar) < dockBudgetOf(640.dp, turnsByColumn = false, hasPagerBar))
        }
        listOf(false, true).forEach { turnsByColumn ->
            assertTrue(dockBudgetOf(640.dp, turnsByColumn, hasPagerBar = true) < dockBudgetOf(640.dp, turnsByColumn, hasPagerBar = false))
        }
    }

    @Test
    fun `a dock never grows past its budget unless it is one row`() {
        (0..1600 step 40).forEach { budget ->
            val layout = checkNotNull(dockLayoutOf(everyRow, budget.dp))
            assertTrue(layout.height <= budget.dp || layout.tier == DockTier.CHORD_CHIPS, "$layout over a budget of $budget dp")
        }
    }

    @Test
    fun `without the chords' row the dock is one row however much room there is`() {
        val rows = everyRow.copy(hasChordRow = false)
        assertEquals(dockLayoutOf(rows, 0.dp), dockLayoutOf(rows, 1000.dp))
    }

    @Test
    fun `no row is no dock`() = assertNull(dockLayoutOf(DockRows(hasPlayingRow = false, hasChordRow = false, hasScaleRow = false), 1000.dp))
}
