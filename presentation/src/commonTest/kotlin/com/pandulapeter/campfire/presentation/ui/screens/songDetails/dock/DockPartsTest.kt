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
import kotlin.test.assertTrue

internal class DockPartsTest {

    private val everyRow = DockRows(hasChordRow = true, hasScaleRow = true, hasTempo = true)

    private fun partsOf(rows: DockRows, isOneRow: Boolean = false, budget: Int = 1000) =
        dockPartsOf(rows, dockLayoutOf(rows, budget.dp, isOneRow = isOneRow))

    @Test
    fun `no dock draws no part`() = assertTrue(dockPartsOf(everyRow, layout = null).isEmpty())

    @Test
    fun `two rows put the tempo chip and the card beside the diagrams under the scale row`() = assertEquals(
        setOf(
            DockPart.CHIP_ROW,
            DockPart.CHIP_ROW_SCALES,
            DockPart.DIAGRAM_ROW,
            DockPart.DIAGRAM_ROW_CARD,
            DockPart.FULL_DIAGRAMS,
            DockPart.DIAGRAM_ROW_TEMPO,
        ),
        partsOf(everyRow),
    )

    @Test
    fun `turning the scale suggestions off takes the chip row and keeps the diagram row`() {
        val parts = partsOf(everyRow.copy(hasScaleRow = false))
        assertTrue(DockPart.CHIP_ROW !in parts)
        assertTrue(DockPart.DIAGRAM_ROW in parts)
    }

    @Test
    fun `turning the diagrams off moves the tempo chip into the chip row`() = assertEquals(
        setOf(DockPart.CHIP_ROW, DockPart.CHIP_ROW_SCALES, DockPart.CHIP_ROW_TEMPO),
        partsOf(everyRow.copy(hasChordRow = false)),
    )

    @Test
    fun `smaller diagrams are a part of their own so a new size fades rather than jumps`() =
        assertTrue(DockPart.SMALL_DIAGRAMS in partsOf(everyRow, budget = 0))

    @Test
    fun `one row holds the chord chips and the scale chips together`() =
        assertTrue(DockPart.CHIP_ROW_CHORDS_AND_SCALES in partsOf(everyRow, isOneRow = true))

    @Test
    fun `the metronome alone is the metronome panel with or without the card`() {
        assertEquals(setOf(DockPart.METRONOME_PANEL), partsOf(DockRows(hasChordRow = false, hasScaleRow = false, hasTempo = true)))
        assertEquals(
            setOf(DockPart.METRONOME_PANEL_WITH_CARD),
            partsOf(DockRows(hasChordRow = false, hasScaleRow = false, hasTempo = true, hasInstrumentChip = true)),
        )
    }
}
