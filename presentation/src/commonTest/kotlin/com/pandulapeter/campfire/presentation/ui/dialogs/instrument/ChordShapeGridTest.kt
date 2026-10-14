/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.instrument

import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.presentation.ui.chords.SelectedShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ChordShapeGridTest {

    private val g = ChordProChords.parse("G")!!
    private val standardG = ChordVoicings.default(g, ChordTuning.GUITAR)!!
    private val dropD = ChordVoicings.all(g, ChordTuning.GUITAR_DROP_D)

    @Test
    fun `a default given for another tuning is not offered`() {
        assertEquals(dropD, shapeGridOptions(SelectedShape(standardG, SelectedShape.Source.DEFAULT), dropD))
    }

    @Test
    fun `the default of the sheet's own tuning offers every shape`() {
        assertEquals(dropD, shapeGridOptions(SelectedShape(dropD.first(), SelectedShape.Source.DEFAULT), dropD))
    }

    @Test
    fun `a stored shape the app does not list comes first`() {
        assertEquals(listOf(standardG) + dropD, shapeGridOptions(SelectedShape(standardG, SelectedShape.Source.PLAYER), dropD))
    }

    @Test
    fun `a stored shape the app lists offers every shape`() {
        assertEquals(dropD, shapeGridOptions(SelectedShape(dropD[1], SelectedShape.Source.PLAYER), dropD))
    }

    @Test
    fun `a defined shape is the only shape offered`() {
        val defined = SelectedShape(standardG, SelectedShape.Source.DEFINED)
        assertEquals(listOf(standardG), shapeGridOptions(defined, dropD))
        assertEquals(listOf(standardG), shapeGridOptions(defined, emptyList()))
    }

    @Test
    fun `with no shapes in the tuning only a stored shape is offered`() {
        assertEquals(listOf(standardG), shapeGridOptions(SelectedShape(standardG, SelectedShape.Source.PLAYER), emptyList()))
        assertTrue(shapeGridOptions(SelectedShape(standardG, SelectedShape.Source.DEFAULT), emptyList()).isEmpty())
    }
}
