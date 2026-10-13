/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chords

import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.playing.testPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChordTuningsTest {

    @Test
    fun `every tuning the diagrams know is the tuner's tuning of the same id and strings`() {
        ChordTuning.entries.filter { it.instrument.isFretted }.forEach { tuning ->
            val tunerTuning = assertNotNull(tuning.instrumentTuning, tuning.id)
            assertEquals(tunerTuning.strings, tuning.strings, tuning.id)
        }
    }

    @Test
    fun `the tuning picked for the instrument is the one drawn for, and standard where none is`() {
        val preferences = testPreferences().copy(
            chordInstrument = UserPreferences.ChordInstrument.GUITAR,
            chordTunings = mapOf("guitar" to "guitar_drop_d", "ukulele" to "ukulele_baritone"),
        )
        assertEquals(ChordTuning.GUITAR_DROP_D, preferences.chordTuning())
        assertEquals(ChordTuning.UKULELE_BARITONE, preferences.copy(chordInstrument = UserPreferences.ChordInstrument.UKULELE).chordTuning())
        assertEquals(ChordTuning.MANDOLIN, preferences.copy(chordInstrument = UserPreferences.ChordInstrument.MANDOLIN).chordTuning())
        assertEquals(ChordTuning.KEYBOARD, preferences.copy(chordInstrument = UserPreferences.ChordInstrument.KEYBOARD).chordTuning())
        assertNull(preferences.copy(chordInstrument = UserPreferences.ChordInstrument.OTHER).chordTuning())
    }

    @Test
    fun `a tuning of another instrument or one this version does not know is read as standard`() {
        assertEquals(ChordTuning.GUITAR, chordTuningOf(ChordInstrument.GUITAR, mapOf("guitar" to "ukulele_baritone")))
        assertEquals(ChordTuning.GUITAR, chordTuningOf(ChordInstrument.GUITAR, mapOf("guitar" to "guitar_seven_string")))
        assertEquals(ChordTuning.KEYBOARD, chordTuningOf(ChordInstrument.KEYBOARD, mapOf("keyboard" to "guitar_drop_d")))
    }

    @Test
    fun `picking a tuning keeps it for its instrument alone, and picking standard leaves no entry`() {
        val preferences = testPreferences().copy(chordTunings = mapOf("ukulele" to "ukulele_baritone", "banjo" to "banjo_open_d"))
        val dropped = preferences.withChordTuning(ChordTuning.GUITAR_DROP_D)
        assertEquals(mapOf("ukulele" to "ukulele_baritone", "banjo" to "banjo_open_d", "guitar" to "guitar_drop_d"), dropped.chordTunings)
        assertEquals(mapOf("guitar" to "guitar_drop_d", "banjo" to "banjo_open_d"), dropped.withChordTuning(ChordTuning.UKULELE).chordTunings)
    }
}
