/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import com.pandulapeter.campfire.presentation.ui.chords.ChordCell
import com.pandulapeter.campfire.presentation.ui.chords.SelectedShape
import kotlin.test.Test
import kotlin.test.assertEquals

class ChordShapeCrossfadeTest {

    private val openG = ChordVoicing.Fretted(frets = listOf(3, 2, 0, 0, 0, 3))
    private val barreG = ChordVoicing.Fretted(frets = listOf(3, 5, 5, 4, 3, 3))
    private val openC = ChordVoicing.Fretted(frets = listOf(null, 3, 2, 0, 1, 0))
    private val openA = ChordVoicing.Fretted(frets = listOf(null, 0, 2, 2, 2, 0))

    private fun cell(name: String, shape: ChordVoicing?, tuning: ChordTuning = ChordTuning.GUITAR) = ChordCell(
        name = name,
        tuning = tuning,
        root = 0,
        selection = SelectedShape(shape, SelectedShape.Source.DEFAULT),
    )

    @Test
    fun `a chord given another shape fades and the others do not`() {
        assertEquals(
            listOf(1),
            changedShapeCells(
                previous = listOf(cell("C", openC), cell("G", openG)),
                cells = listOf(cell("C", openC), cell("G", barreG)),
            ),
        )
    }

    @Test
    fun `another tuning of the same instrument fades`() {
        assertEquals(
            listOf(0),
            changedShapeCells(
                previous = listOf(cell("G", openG)),
                cells = listOf(cell("G", openG, ChordTuning.GUITAR_DROP_D)),
            ),
        )
    }

    @Test
    fun `a shape arriving into the empty frame does not fade`() {
        assertEquals(emptyList(), changedShapeCells(previous = listOf(cell("G", null)), cells = listOf(cell("G", openG))))
    }

    @Test
    fun `a cell holding another chord does not fade`() {
        assertEquals(emptyList(), changedShapeCells(previous = listOf(cell("G", openG)), cells = listOf(cell("A", barreG))))
    }

    @Test
    fun `a transposed step of a numbering does not fade while another shape for the same step does`() {
        fun step(letterName: String, root: Int, shape: ChordVoicing) = ChordCell(
            name = "1",
            letterName = letterName,
            tuning = ChordTuning.GUITAR,
            root = root,
            selection = SelectedShape(shape, SelectedShape.Source.DEFAULT),
        )
        assertEquals(emptyList(), changedShapeCells(previous = listOf(step("G", 7, openG)), cells = listOf(step("A", 9, openA))))
        assertEquals(listOf(0), changedShapeCells(previous = listOf(step("G", 7, openG)), cells = listOf(step("G", 7, barreG))))
    }

    @Test
    fun `a cell that is new does not fade`() {
        assertEquals(emptyList(), changedShapeCells(previous = listOf(cell("C", openC)), cells = listOf(cell("C", openC), cell("G", openG))))
    }
}
