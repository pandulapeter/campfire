/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.tuner.api

import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstrumentTuningTest {

    @Test
    fun `every preset is read back from its id and an unknown id is chromatic`() {
        InstrumentTuning.entries.forEach { assertEquals(it, InstrumentTuning.fromId(it.id)) }
        assertNull(InstrumentTuning.fromId(InstrumentTuning.CHROMATIC_ID))
        assertNull(InstrumentTuning.fromId("theremin"))
    }

    @Test
    fun `the ids are unique`() {
        assertEquals(InstrumentTuning.entries.size, InstrumentTuning.entries.map { it.id }.toSet().size)
        assertTrue(InstrumentTuning.entries.none { it.id == InstrumentTuning.CHROMATIC_ID })
    }

    @Test
    fun `the presets are the tunings they are named after`() {
        assertEquals(listOf("E2", "A2", "D3", "G3", "B3", "E4"), InstrumentTuning.GUITAR.strings.map(::name))
        assertEquals(listOf("D2", "A2", "D3", "G3", "B3", "E4"), InstrumentTuning.GUITAR_DROP_D.strings.map(::name))
        assertEquals(listOf("D#2", "G#2", "C#3", "F#3", "A#3", "D#4"), InstrumentTuning.GUITAR_HALF_STEP_DOWN.strings.map(::name))
        assertEquals(listOf("D2", "G2", "C3", "F3", "A3", "D4"), InstrumentTuning.GUITAR_FULL_STEP_DOWN.strings.map(::name))
        assertEquals(listOf("C2", "G2", "C3", "F3", "A3", "D4"), InstrumentTuning.GUITAR_DROP_C.strings.map(::name))
        assertEquals(listOf("D2", "G2", "D3", "G3", "B3", "D4"), InstrumentTuning.GUITAR_OPEN_G.strings.map(::name))
        assertEquals(listOf("D2", "A2", "D3", "F#3", "A3", "D4"), InstrumentTuning.GUITAR_OPEN_D.strings.map(::name))
        assertEquals(listOf("D2", "A2", "D3", "G3", "A3", "D4"), InstrumentTuning.GUITAR_DADGAD.strings.map(::name))
        assertEquals(listOf("B1", "E2", "A2", "D3", "G3", "B3", "E4"), InstrumentTuning.GUITAR_SEVEN_STRING.strings.map(::name))
        assertEquals(listOf("A1", "E2", "A2", "D3", "G3", "B3", "E4"), InstrumentTuning.GUITAR_SEVEN_STRING_DROP_A.strings.map(::name))
        assertEquals(listOf("F#1", "B1", "E2", "A2", "D3", "G3", "B3", "E4"), InstrumentTuning.GUITAR_EIGHT_STRING.strings.map(::name))
        assertEquals(listOf("E1", "B1", "E2", "A2", "D3", "G3", "B3", "E4"), InstrumentTuning.GUITAR_EIGHT_STRING_DROP_E.strings.map(::name))
        assertEquals(listOf("E1", "A1", "D2", "G2"), InstrumentTuning.BASS.strings.map(::name))
        assertEquals(listOf("D1", "A1", "D2", "G2"), InstrumentTuning.BASS_DROP_D.strings.map(::name))
        assertEquals(listOf("D#1", "G#1", "C#2", "F#2"), InstrumentTuning.BASS_HALF_STEP_DOWN.strings.map(::name))
        assertEquals(listOf("D1", "G1", "C2", "F2"), InstrumentTuning.BASS_FULL_STEP_DOWN.strings.map(::name))
        assertEquals(listOf("B0", "E1", "A1", "D2", "G2"), InstrumentTuning.BASS_FIVE_STRING.strings.map(::name))
        assertEquals(listOf("E1", "A1", "D2", "G2", "C3"), InstrumentTuning.BASS_FIVE_STRING_HIGH_C.strings.map(::name))
        assertEquals(listOf("G4", "C4", "E4", "A4"), InstrumentTuning.UKULELE.strings.map(::name))
        assertEquals(listOf("G3", "C4", "E4", "A4"), InstrumentTuning.UKULELE_LOW_G.strings.map(::name))
        assertEquals(listOf("D3", "G3", "B3", "E4"), InstrumentTuning.UKULELE_BARITONE.strings.map(::name))
        assertEquals(listOf("G4", "D3", "G3", "B3", "D4"), InstrumentTuning.BANJO.strings.map(::name))
        assertEquals(listOf("G4", "C3", "G3", "C4", "D4"), InstrumentTuning.BANJO_DOUBLE_C.strings.map(::name))
        assertEquals(listOf("G3", "D4", "A4", "E5"), InstrumentTuning.VIOLIN.strings.map(::name))
        assertEquals(listOf("G3", "D4", "A4", "E5"), InstrumentTuning.MANDOLIN.strings.map(::name))
    }

    private fun name(note: Int) = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")[note % 12] + (note / 12 - 1)
}
