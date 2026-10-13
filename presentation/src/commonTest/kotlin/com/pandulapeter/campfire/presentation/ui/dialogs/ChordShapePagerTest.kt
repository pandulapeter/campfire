/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.chords.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.presentation.ui.chords.SelectedShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ChordShapePagerTest {

    private val g = ChordProChords.parse("G")!!
    private val standardG = ChordVoicings.default(g, ChordTuning.GUITAR)!!
    private val dropD = ChordVoicings.all(g, ChordTuning.GUITAR_DROP_D)

    @Test
    fun `a default given for another tuning is not paged`() {
        assertEquals(dropD, shapePagerOptions(SelectedShape(standardG, SelectedShape.Source.DEFAULT), dropD))
    }

    @Test
    fun `the default of the sheet's own tuning pages every shape`() {
        assertEquals(dropD, shapePagerOptions(SelectedShape(dropD.first(), SelectedShape.Source.DEFAULT), dropD))
    }

    @Test
    fun `a stored shape the app does not list comes first`() {
        assertEquals(listOf(standardG) + dropD, shapePagerOptions(SelectedShape(standardG, SelectedShape.Source.PLAYER), dropD))
    }

    @Test
    fun `a stored shape the app lists pages every shape`() {
        assertEquals(dropD, shapePagerOptions(SelectedShape(dropD[1], SelectedShape.Source.PLAYER), dropD))
    }

    @Test
    fun `a defined shape is the only page`() {
        val defined = SelectedShape(standardG, SelectedShape.Source.DEFINED)
        assertEquals(listOf(standardG), shapePagerOptions(defined, dropD))
        assertEquals(listOf(standardG), shapePagerOptions(defined, emptyList()))
    }

    @Test
    fun `with no shapes in the tuning only a stored shape is paged`() {
        assertEquals(listOf(standardG), shapePagerOptions(SelectedShape(standardG, SelectedShape.Source.PLAYER), emptyList()))
        assertTrue(shapePagerOptions(SelectedShape(standardG, SelectedShape.Source.DEFAULT), emptyList()).isEmpty())
    }
}
