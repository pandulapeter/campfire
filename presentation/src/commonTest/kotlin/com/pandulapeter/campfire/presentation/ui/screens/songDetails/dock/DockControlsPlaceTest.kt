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
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SETLIST_CONTROLS_MIN_WIDTH
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class DockControlsPlaceTest {

    private val cardScalesTempo = DockRows(hasChordRow = false, hasScaleRow = true, hasTempo = true, hasInstrumentChip = true)
    private val cardAndTempo = cardScalesTempo.copy(hasScaleRow = false)
    private val cardOnly = cardAndTempo.copy(hasTempo = false)
    private val oneRow = checkNotNull(dockLayoutOf(cardScalesTempo, 1000.dp, isOneRow = false))

    private fun layoutOf(rows: DockRows, scalesFit: Boolean = true, fitsPagerBar: Boolean = false, isOneRow: Boolean = false) =
        checkNotNull(dockLayoutOf(rows, 1000.dp, isOneRow = isOneRow, scalesFitBesideControls = scalesFit, controlsFitPagerBar = fitsPagerBar))

    @Test
    fun `scale chips too wide for the room between the controls get a row of their own over them`() {
        val apart = layoutOf(cardScalesTempo, scalesFit = false)
        assertEquals(DockControlsPlace.OWN_ROW, apart.controls)
        assertEquals(oneRow.height + DOCK_ROW_GAP + oneRow.rowHeight, apart.height)
        assertEquals(
            setOf(DockPart.CHIP_ROW, DockPart.CHIP_ROW_SCALES, DockPart.CONTROLS_ROW, DockPart.CONTROLS_ROW_CARD, DockPart.CONTROLS_ROW_TEMPO),
            dockPartsOf(cardScalesTempo, apart),
        )
    }

    @Test
    fun `a short window and a row with no control keep the scale chips in the one row however wide they are`() {
        assertEquals(oneRow, layoutOf(cardScalesTempo, scalesFit = false, isOneRow = true))
        val scalesOnly = cardScalesTempo.copy(hasTempo = false, hasInstrumentChip = false)
        assertEquals(DockControlsPlace.ROW, layoutOf(scalesOnly, scalesFit = false).controls)
    }

    @Test
    fun `the card alone moves onto the setlist's pager bar and leaves no row under the song`() {
        val layout = layoutOf(cardOnly, fitsPagerBar = true)
        assertEquals(DockControlsPlace.PAGER_BAR, layout.controls)
        assertEquals(0.dp, layout.height)
        assertEquals(setOf(DockPart.PAGER_BAR_CARD), dockPartsOf(cardOnly, layout))
    }

    @Test
    fun `the card and the tempo chip move onto the pager bar in place of the metronome panel where they fit there`() {
        assertEquals(setOf(DockPart.PAGER_BAR_CARD, DockPart.PAGER_BAR_TEMPO), dockPartsOf(cardAndTempo, layoutOf(cardAndTempo, fitsPagerBar = true)))
        assertEquals(setOf(DockPart.METRONOME_PANEL_WITH_CARD), dockPartsOf(cardAndTempo, layoutOf(cardAndTempo, fitsPagerBar = false)))
    }

    @Test
    fun `the tempo chip alone keeps the metronome panel even beside a pager bar`() {
        val tempoOnly = cardAndTempo.copy(hasInstrumentChip = false)
        assertEquals(setOf(DockPart.METRONOME_PANEL), dockPartsOf(tempoOnly, layoutOf(tempoOnly, fitsPagerBar = true)))
    }

    @Test
    fun `the pager bar takes the controls from under scale chips too wide for them and leaves those their row`() {
        val layout = layoutOf(cardScalesTempo, scalesFit = false, fitsPagerBar = true)
        assertEquals(oneRow.height, layout.height)
        assertEquals(
            setOf(DockPart.CHIP_ROW, DockPart.CHIP_ROW_SCALES, DockPart.PAGER_BAR_CARD, DockPart.PAGER_BAR_TEMPO),
            dockPartsOf(cardScalesTempo, layout),
        )
    }

    @Test
    fun `scale chips that fit keep the controls beside them in the one row whatever the pager bar has room for`() =
        assertEquals(oneRow, layoutOf(cardScalesTempo, fitsPagerBar = true))

    @Test
    fun `scale chips fit while their width is unknown or no wider than the room the controls leave`() {
        val widths = DockControlWidths(card = 150.dp, tempoChip = 130.dp)
        val room = 600.dp - 150.dp - 130.dp - DOCK_ITEM_GAP * 2
        assertTrue(scalesFitBesideControls(widths, rowWidth = 600.dp, scalesWidth = null))
        assertTrue(scalesFitBesideControls(widths, rowWidth = 600.dp, scalesWidth = room))
        assertFalse(scalesFitBesideControls(widths, rowWidth = 600.dp, scalesWidth = room + 1.dp))
        assertTrue(scalesFitBesideControls(DockControlWidths(card = 0.dp, tempoChip = 0.dp), rowWidth = 600.dp, scalesWidth = 600.dp))
    }

    @Test
    fun `the pager bar always holds the card alone and the tempo chip too only beside room for its own controls`() {
        assertTrue(controlsFitPagerBar(DockControlWidths(card = 400.dp, tempoChip = 0.dp), rowWidth = 344.dp))
        val widths = DockControlWidths(card = 150.dp, tempoChip = 130.dp)
        val needed = 150.dp + 130.dp + DOCK_ITEM_GAP * 2 + SETLIST_CONTROLS_MIN_WIDTH
        assertTrue(controlsFitPagerBar(widths, rowWidth = needed))
        assertFalse(controlsFitPagerBar(widths, rowWidth = needed - 1.dp))
    }
}
