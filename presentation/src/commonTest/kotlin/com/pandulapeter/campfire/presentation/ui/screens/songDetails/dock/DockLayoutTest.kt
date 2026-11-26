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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class DockLayoutTest {

    private val everyRow = DockRows(hasChordRow = true, hasScaleRow = true, hasTempo = true)

    @Test
    fun `an upright phone gets smaller diagrams in two rows however it is read`() {
        listOf(false to false, false to true, true to false, true to true).forEach { (turnsByColumn, hasPagerBar) ->
            val budget = dockBudgetOf(windowHeight = 640.dp, turnsByColumn = turnsByColumn, hasPagerBar = hasPagerBar)
            val layout = checkNotNull(dockLayoutOf(everyRow, budget, isOneRow = false))
            assertEquals(DockTier.SMALL_DIAGRAMS, layout.tier)
            assertTrue(layout.height <= 122.dp, "$layout is taller than the plan's two rows")
        }
    }

    @Test
    fun `a short window and an import preview get one row of chord chips however much budget they have`() =
        assertEquals(DockTier.CHORD_CHIPS, dockLayoutOf(everyRow, 1000.dp, isOneRow = true)?.tier)

    @Test
    fun `a taller window gets full diagrams once they fit its budget`() {
        val tiers = listOf(640, 1024, 1280).map { height ->
            dockLayoutOf(everyRow, dockBudgetOf(windowHeight = height.dp, turnsByColumn = false, hasPagerBar = false), isOneRow = false)?.tier
        }
        assertEquals(listOf(DockTier.SMALL_DIAGRAMS, DockTier.SMALL_DIAGRAMS, DockTier.FULL_DIAGRAMS), tiers)
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
    fun `full diagrams never grow past the budget`() {
        (0..1600 step 40).forEach { budget ->
            val layout = checkNotNull(dockLayoutOf(everyRow, budget.dp, isOneRow = false))
            assertTrue(layout.height <= budget.dp || layout.tier != DockTier.FULL_DIAGRAMS, "$layout over a budget of $budget dp")
        }
    }

    @Test
    fun `without the chords' row the dock is one row however much room there is`() {
        val rows = everyRow.copy(hasChordRow = false)
        assertEquals(dockLayoutOf(rows, 0.dp, isOneRow = false), dockLayoutOf(rows, 1000.dp, isOneRow = false))
        assertEquals(DockTier.CHORD_CHIPS, dockLayoutOf(rows, 1000.dp, isOneRow = false)?.tier)
    }

    @Test
    fun `without scale suggestions the diagrams' row is the only one`() {
        val withScales = checkNotNull(dockLayoutOf(everyRow, 640.dp, isOneRow = false))
        val withoutScales = checkNotNull(dockLayoutOf(everyRow.copy(hasScaleRow = false), 640.dp, isOneRow = false))
        assertEquals(withScales.height - withScales.rowHeight - DOCK_ROW_GAP, withoutScales.height)
    }

    @Test
    fun `the click alone is the metronome panel in one row`() {
        val metronomeOnly = NO_ROW.copy(hasTempo = true)
        assertTrue(metronomeOnly.isMetronomeOnly)
        assertEquals(DockTier.CHORD_CHIPS, dockLayoutOf(metronomeOnly, 1000.dp, isOneRow = false)?.tier)
        listOf(metronomeOnly.copy(hasChordRow = true), metronomeOnly.copy(hasScaleRow = true)).forEach { rows ->
            assertFalse(rows.isMetronomeOnly, "$rows")
        }
    }

    @Test
    fun `a song with nothing for a row lays the dock out as that row's switch turned off would`() {
        assertEquals(everyRow.copy(hasScaleRow = false), everyRow.forSong(hasChords = true, hasScales = false))
        assertEquals(everyRow.copy(hasChordRow = false, hasInstrumentChip = true), everyRow.forSong(hasChords = false, hasScales = true))
        assertTrue(everyRow.forSong(hasChords = false, hasScales = false).isMetronomeOnly)
        assertNull(dockLayoutOf(everyRow.copy(hasChordRow = false, hasTempo = false).forSong(hasChords = false, hasScales = false), 1000.dp, isOneRow = false))
        assertEquals(everyRow.copy(hasChordRow = false), everyRow.copy(hasChordRow = false).forSong(hasChords = true, hasScales = true))
    }

    @Test
    fun `no row is no dock`() = assertNull(dockLayoutOf(NO_ROW, 1000.dp, isOneRow = false))

    @Test
    fun `an instrument without shapes keeps its card in one row under the song even with nothing else to show`() {
        val cardOnly = NO_ROW.copy(hasInstrumentChip = true)
        assertTrue(cardOnly.hasInstrumentCard)
        assertEquals(DockTier.CHORD_CHIPS, dockLayoutOf(cardOnly, 1000.dp, isOneRow = false)?.tier)
        assertTrue(cardOnly.copy(hasTempo = true).isMetronomeOnly)
        assertFalse(isDockSidePanel(rows = cardOnly.copy(hasScaleRow = true, hasTempo = true), windowWidth = 1280.dp, sideInsets = 0.dp, panelWidth = PANEL_WIDTH, isContinuous = false))
    }

    @Test
    fun `a song without chords keeps the instrument card in one row`() {
        val withoutShapes = NO_ROW.copy(hasInstrumentChip = true).forSong(hasChords = false, hasScales = false)
        assertTrue(withoutShapes.hasInstrumentCard)
        assertEquals(DockTier.CHORD_CHIPS, dockLayoutOf(withoutShapes, 1000.dp, isOneRow = false)?.tier)
        val withShapes = NO_ROW.copy(hasChordRow = true).forSong(hasChords = false, hasScales = false)
        assertFalse(withShapes.hasChordRow)
        assertTrue(withShapes.hasInstrumentCard)
        assertEquals(DockTier.CHORD_CHIPS, dockLayoutOf(withShapes, 1000.dp, isOneRow = false)?.tier)
    }

    @Test
    fun `a wide window gets the side panel whatever its height`() {
        assertTrue(isPagedSidePanel(windowWidth = 1024.dp))
        assertTrue(isPagedSidePanel(windowWidth = 840.dp))
    }

    @Test
    fun `a narrower window keeps the rows under paged songs even on its side`() {
        assertFalse(isPagedSidePanel(windowWidth = 800.dp))
        assertFalse(isPagedSidePanel(windowWidth = 640.dp))
        assertFalse(isPagedSidePanel(windowWidth = 360.dp))
    }

    @Test
    fun `a song in one column gets the side panel wherever a column still fits beside it`() {
        assertTrue(isContinuousSidePanel(windowWidth = 800.dp))
        assertTrue(isContinuousSidePanel(windowWidth = 720.dp))
        assertFalse(isContinuousSidePanel(windowWidth = 640.dp))
        assertFalse(isContinuousSidePanel(windowWidth = 360.dp))
    }

    @Test
    fun `a song in one column counts the window's side insets against the room beside the panel`() {
        assertTrue(isDockSidePanel(rows = everyRow, windowWidth = 720.dp, sideInsets = 0.dp, panelWidth = PANEL_WIDTH, isContinuous = true))
        assertFalse(isDockSidePanel(rows = everyRow, windowWidth = 720.dp, sideInsets = 96.dp, panelWidth = PANEL_WIDTH, isContinuous = true))
    }

    @Test
    fun `a dock without diagrams keeps its row under the song in any window`() {
        val withoutDiagrams = DockRows(hasChordRow = false, hasScaleRow = true, hasTempo = true)
        val metronomeOnly = NO_ROW.copy(hasTempo = true)
        listOf(withoutDiagrams, metronomeOnly).forEach { rows ->
            assertFalse(isDockSidePanel(rows = rows, windowWidth = 1280.dp, sideInsets = 0.dp, panelWidth = PANEL_WIDTH, isContinuous = false))
            assertFalse(isDockSidePanel(rows = rows, windowWidth = 1280.dp, sideInsets = 0.dp, panelWidth = PANEL_WIDTH, isContinuous = true))
        }
    }

    @Test
    fun `the side panel is as wide as the tall tempo chip but never narrower than two diagrams with room to spare`() {
        assertEquals(dockPanelWidthOf(tempoChipWidth = 0.dp), dockPanelWidthOf(tempoChipWidth = 100.dp))
        assertTrue(dockPanelContentWidthOf(tempoChipWidth = 0.dp) > DOCK_DIAGRAM_CELL_WIDTH * 2 + DOCK_ITEM_GAP)
        assertEquals(200.dp, dockPanelContentWidthOf(tempoChipWidth = 200.dp))
        assertEquals(10.dp, dockPanelWidthOf(tempoChipWidth = 210.dp) - dockPanelWidthOf(tempoChipWidth = 200.dp))
    }

    @Test
    fun `the side panel draws full diagrams and is no dock without a row`() {
        assertEquals(DockTier.FULL_DIAGRAMS, dockPanelLayoutOf(everyRow)?.tier)
        assertNull(dockPanelLayoutOf(NO_ROW))
    }

    @Test
    fun `the dock's last row is as far from a bare window edge as from its top but closer over a navigation bar`() {
        val atEdge = checkNotNull(dockLayoutOf(everyRow, 1000.dp, isOneRow = true, bottomPadding = dockBottomPaddingOf(bottomInset = 0.dp)))
        val overInset = checkNotNull(dockLayoutOf(everyRow, 1000.dp, isOneRow = true, bottomPadding = dockBottomPaddingOf(bottomInset = 24.dp)))
        assertEquals(DOCK_VERTICAL_PADDING, atEdge.bottomPadding)
        assertTrue(overInset.bottomPadding < atEdge.bottomPadding)
        assertEquals(atEdge.height - overInset.height, atEdge.bottomPadding - overInset.bottomPadding)
    }
}

private val PANEL_WIDTH = dockPanelWidthOf(tempoChipWidth = 130.dp)

private val WITH_DIAGRAMS = DockRows(hasChordRow = true, hasScaleRow = true, hasTempo = true)

private val NO_ROW = DockRows(hasChordRow = false, hasScaleRow = false, hasTempo = false)

private fun isPagedSidePanel(windowWidth: Dp) =
    isDockSidePanel(rows = WITH_DIAGRAMS, windowWidth = windowWidth, sideInsets = 0.dp, panelWidth = PANEL_WIDTH, isContinuous = false)

private fun isContinuousSidePanel(windowWidth: Dp) =
    isDockSidePanel(rows = WITH_DIAGRAMS, windowWidth = windowWidth, sideInsets = 0.dp, panelWidth = PANEL_WIDTH, isContinuous = true)
