/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

import com.pandulapeter.campfire.data.model.domain.UserPreferences.Instrument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InstrumentTest {

    /** Every tuning `:tuner:api` knows, written out since this module does not see it, with the instrument it is of. */
    private val tunings = mapOf(
        Instrument.GUITAR to listOf(
            "guitar", "guitar_drop_d", "guitar_half_step_down", "guitar_full_step_down", "guitar_drop_c_sharp",
            "guitar_drop_c", "guitar_drop_b", "guitar_double_drop_d", "guitar_open_g", "guitar_open_d", "guitar_open_e",
            "guitar_open_a", "guitar_open_c", "guitar_dadgad",
        ),
        Instrument.GUITAR_SEVEN_STRING to listOf(
            "guitar_seven_string", "guitar_seven_string_drop_a", "guitar_seven_string_half_step_down",
            "guitar_seven_string_full_step_down",
        ),
        Instrument.GUITAR_EIGHT_STRING to listOf(
            "guitar_eight_string", "guitar_eight_string_drop_e", "guitar_eight_string_half_step_down",
            "guitar_eight_string_full_step_down",
        ),
        Instrument.BASS to listOf("bass", "bass_drop_d", "bass_half_step_down", "bass_full_step_down", "bass_drop_c"),
        Instrument.BASS_FIVE_STRING to listOf(
            "bass_five_string", "bass_five_string_high_c", "bass_five_string_drop_a", "bass_five_string_half_step_down",
            "bass_five_string_full_step_down",
        ),
        Instrument.UKULELE to listOf("ukulele", "ukulele_low_g", "ukulele_baritone", "ukulele_d"),
        Instrument.BANJO to listOf("banjo", "banjo_double_c", "banjo_c", "banjo_open_d", "banjo_g_modal"),
        Instrument.VIOLIN to listOf("violin", "violin_half_step_down", "violin_aeae", "violin_adae", "violin_gdgd"),
        Instrument.MANDOLIN to listOf("mandolin", "mandolin_half_step_down", "mandolin_aeae", "mandolin_gdgd"),
    )

    @Test
    fun `every tuning id resolves to its own instrument`() {
        tunings.forEach { (instrument, ids) ->
            ids.forEach { assertEquals(instrument, Instrument.ofTuningId(it), it) }
        }
    }

    @Test
    fun `an unknown tuning id resolves to nothing`() {
        assertNull(Instrument.ofTuningId("chromatic"))
        assertNull(Instrument.ofTuningId("guitarist"))
        assertNull(Instrument.ofTuningId(""))
    }

    @Test
    fun `every instrument is found by its id`() {
        Instrument.entries.forEach { assertEquals(it, Instrument.fromId(it.id)) }
        assertNull(Instrument.fromId("theremin"))
    }
}
