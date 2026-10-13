/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.instrument

import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.domain.UserPreferences.Instrument
import com.pandulapeter.campfire.presentation.ui.chords.instrumentTuning
import com.pandulapeter.campfire.presentation.ui.playing.testPreferences
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InstrumentsTest {

    @Test
    fun `every tuning the tuner knows is offered under exactly one instrument`() {
        val offered = Instrument.entries.flatMap { it.tunings }
        assertEquals(InstrumentTuning.entries.toSet(), offered.toSet())
        assertEquals(offered.size, offered.toSet().size)
    }

    @Test
    fun `every instrument with tunings is strung in its standard tuning first, which has the instrument's own id`() {
        Instrument.entries.filter { it.tunings.isNotEmpty() }.forEach { assertEquals(it.id, it.tunings.first().id) }
        assertEquals(emptyList(), Instrument.KEYBOARD.tunings)
        assertEquals(emptyList(), Instrument.OTHER.tunings)
    }

    @Test
    fun `a tuning is found under its instrument`() {
        assertEquals(Instrument.GUITAR_EIGHT_STRING, Instrument.of(InstrumentTuning.GUITAR_EIGHT_STRING_DROP_E))
        assertEquals(Instrument.BASS_FIVE_STRING, Instrument.of(InstrumentTuning.BASS_FIVE_STRING))
        assertEquals(Instrument.GUITAR, Instrument.of(InstrumentTuning.GUITAR_DADGAD))
        InstrumentTuning.entries.forEach { assertEquals(Instrument.ofTuningId(it.id), Instrument.of(it), it.id) }
    }

    @Test
    fun `every tuning of an instrument with shapes is a tuning the diagrams know, of the same id and strings`() {
        Instrument.entries.forEach { instrument ->
            val chordInstrument = instrument.chordInstrument ?: return@forEach
            instrument.tunings.forEach { tuning ->
                val chordTuning = assertNotNull(ChordTuning.fromId(tuning.id), tuning.id)
                assertEquals(chordInstrument, chordTuning.instrument, tuning.id)
                assertEquals(tuning.strings, chordTuning.strings, tuning.id)
            }
        }
    }

    @Test
    fun `every tuning the diagrams know is a tuning of the instrument its own maps to`() {
        ChordTuning.entries.filter { it.instrument.isFretted }.forEach { chordTuning ->
            val tuning = assertNotNull(chordTuning.instrumentTuning, chordTuning.id)
            assertEquals(chordTuning.instrument, Instrument.of(tuning).chordInstrument, chordTuning.id)
            assertEquals(tuning.strings, chordTuning.strings, chordTuning.id)
        }
    }

    @Test
    fun `the tuning picked for an instrument is read, and standard where none, an unknown one, or another instrument's is`() {
        val preferences = testPreferences().copy(
            tunings = mapOf("guitar" to "guitar_drop_d", "ukulele" to "theremin", "banjo" to "guitar_open_g"),
        )
        assertEquals(InstrumentTuning.GUITAR_DROP_D, preferences.tuningOf(Instrument.GUITAR))
        assertEquals(InstrumentTuning.UKULELE, preferences.tuningOf(Instrument.UKULELE))
        assertEquals(InstrumentTuning.BANJO, preferences.tuningOf(Instrument.BANJO))
        assertEquals(InstrumentTuning.MANDOLIN, preferences.tuningOf(Instrument.MANDOLIN))
        assertNull(preferences.tuningOf(Instrument.KEYBOARD))
    }

    @Test
    fun `picking a tuning picks its instrument, keeps the other instruments' tunings, and leaves no entry for standard`() {
        val preferences = testPreferences().copy(instrument = Instrument.UKULELE, tunings = mapOf("ukulele" to "ukulele_baritone", "banjo" to "banjo_open_d"))
        val dropped = preferences.withTuning(InstrumentTuning.GUITAR_DROP_D)
        assertEquals(Instrument.GUITAR, dropped.instrument)
        assertEquals(mapOf("ukulele" to "ukulele_baritone", "banjo" to "banjo_open_d", "guitar" to "guitar_drop_d"), dropped.tunings)
        val standard = dropped.withTuning(InstrumentTuning.UKULELE)
        assertEquals(Instrument.UKULELE, standard.instrument)
        assertEquals(mapOf("guitar" to "guitar_drop_d", "banjo" to "banjo_open_d"), standard.tunings)
    }

    @Test
    fun `the diagrams are drawn in the instrument's tuning, the keyboard's own for the keyboard, and not at all without shapes`() {
        val preferences = testPreferences().copy(tunings = mapOf("guitar" to "guitar_drop_d", "ukulele" to "ukulele_baritone"))
        assertEquals(ChordTuning.GUITAR_DROP_D, preferences.copy(instrument = Instrument.GUITAR).chordTuning())
        assertEquals(ChordTuning.UKULELE_BARITONE, preferences.copy(instrument = Instrument.UKULELE).chordTuning())
        assertEquals(ChordTuning.MANDOLIN, preferences.copy(instrument = Instrument.MANDOLIN).chordTuning())
        assertEquals(ChordTuning.KEYBOARD, preferences.copy(instrument = Instrument.KEYBOARD).chordTuning())
        assertNull(preferences.copy(instrument = Instrument.BASS).chordTuning())
        assertNull(preferences.copy(instrument = Instrument.GUITAR_SEVEN_STRING).chordTuning())
        assertNull(preferences.copy(instrument = Instrument.OTHER).chordTuning())
    }

    @Test
    fun `the tuner listens for the instrument's tuning, and chromatically for the keyboard and Other`() {
        val preferences = testPreferences().copy(tunings = mapOf("bass" to "bass_drop_d"))
        assertEquals(InstrumentTuning.BASS_DROP_D, preferences.copy(instrument = Instrument.BASS).instrumentTuning)
        assertNull(preferences.copy(instrument = Instrument.KEYBOARD).instrumentTuning)
        assertNull(preferences.copy(instrument = Instrument.OTHER).instrumentTuning)
    }
}
