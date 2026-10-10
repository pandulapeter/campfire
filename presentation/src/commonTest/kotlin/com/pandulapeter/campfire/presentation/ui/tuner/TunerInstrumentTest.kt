/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TunerInstrumentTest {

    @Test
    fun `every preset is offered under exactly one instrument`() {
        val offered = TunerInstrument.entries.flatMap { it.tunings }
        assertEquals(InstrumentTuning.entries.toSet(), offered.toSet())
        assertEquals(offered.size, offered.toSet().size)
    }

    @Test
    fun `a tuning is found under its instrument and chromatic under none`() {
        assertEquals(TunerInstrument.EIGHT_STRING_GUITAR, instrumentOf(InstrumentTuning.GUITAR_EIGHT_STRING_DROP_E))
        assertEquals(TunerInstrument.FIVE_STRING_BASS, instrumentOf(InstrumentTuning.BASS_FIVE_STRING))
        assertEquals(TunerInstrument.GUITAR, instrumentOf(InstrumentTuning.GUITAR_DADGAD))
        assertNull(instrumentOf(null))
    }

    @Test
    fun `every instrument is strung in its standard tuning first`() {
        assertEquals(InstrumentTuning.GUITAR, TunerInstrument.GUITAR.tunings.first())
        assertEquals(InstrumentTuning.BASS, TunerInstrument.BASS.tunings.first())
        assertEquals(InstrumentTuning.BANJO, TunerInstrument.BANJO.tunings.first())
    }
}
