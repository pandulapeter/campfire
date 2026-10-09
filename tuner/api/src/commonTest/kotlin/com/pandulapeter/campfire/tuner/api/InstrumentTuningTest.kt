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
    fun `the presets are the standard tunings`() {
        assertEquals(listOf("E2", "A2", "D3", "G3", "B3", "E4"), InstrumentTuning.GUITAR.strings.map(::name))
        assertEquals(listOf("D2", "A2", "D3", "G3", "B3", "E4"), InstrumentTuning.GUITAR_DROP_D.strings.map(::name))
        assertEquals(listOf("E1", "A1", "D2", "G2"), InstrumentTuning.BASS.strings.map(::name))
        assertEquals(listOf("B0", "E1", "A1", "D2", "G2"), InstrumentTuning.BASS_FIVE_STRING.strings.map(::name))
        assertEquals(listOf("G4", "C4", "E4", "A4"), InstrumentTuning.UKULELE.strings.map(::name))
        assertEquals(listOf("G3", "C4", "E4", "A4"), InstrumentTuning.UKULELE_LOW_G.strings.map(::name))
        assertEquals(listOf("G3", "D4", "A4", "E5"), InstrumentTuning.VIOLIN.strings.map(::name))
        assertEquals(listOf("G4", "D3", "G3", "B3", "D4"), InstrumentTuning.BANJO.strings.map(::name))
    }

    private fun name(note: Int) = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")[note % 12] + (note / 12 - 1)
}
