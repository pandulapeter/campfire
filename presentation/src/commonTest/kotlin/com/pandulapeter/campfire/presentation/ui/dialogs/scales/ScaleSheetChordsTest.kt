/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.scales

import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.scales.ScaleChords
import com.pandulapeter.campfire.chordpro.scales.Scales
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ScaleSheetChordsTest {

    @Test
    fun `the chords of a scale are named in the reader's notation and drawn with the tables' shapes`() {
        val major = Scales.fromId("major")!!
        val chords = scaleSheetChords(ScaleChords.triads(major, "G"), ChordNotation.GERMAN, ChordTuning.GUITAR)
        assertEquals(listOf("G", "Am", "Hm", "C", "D", "Em", "F#dim"), chords.map { it.chord.name })
        assertEquals(listOf("G", "Am", "Bm", "C", "D", "Em", "F#dim"), chords.map { it.chord.spelling })
        chords.filter { !it.chord.isShapePending }.forEach { assertNotNull(it.chord.defaultShape, it.chord.name) }
        assertEquals("vii°", chords.last().numeral)
    }
}
