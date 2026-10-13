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
import com.pandulapeter.campfire.tuner.api.model.StringCourses
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstrumentsTest {

    @Test
    fun `every tuning the tuner knows is offered under one instrument, or the guitar's under the twelve-string's too`() {
        val offered = Instrument.entries.filterNot { it.isTwelveString }.flatMap { it.tunings }
        assertEquals(InstrumentTuning.entries.toSet(), offered.toSet())
        assertEquals(offered.size, offered.toSet().size)
        assertEquals(Instrument.GUITAR.tunings, Instrument.GUITAR_TWELVE_STRING.tunings)
    }

    @Test
    fun `every instrument with tunings is strung in its standard tuning first, which has the instrument's own id`() {
        Instrument.entries.filter { it.tunings.isNotEmpty() && !it.isTwelveString }.forEach { assertEquals(it.id, it.tunings.first().id) }
        assertEquals(emptyList(), Instrument.KEYBOARD.tunings)
        assertEquals(emptyList(), Instrument.OTHER.tunings)
    }

    @Test
    fun `a tuning is standard where it is the first one of its instrument`() {
        assertTrue(InstrumentTuning.GUITAR.isStandard)
        assertTrue(InstrumentTuning.BANJO.isStandard)
        assertFalse(InstrumentTuning.GUITAR_DROP_D.isStandard)
        assertFalse(InstrumentTuning.UKULELE_LOW_G.isStandard)
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
            val instruments = Instrument.entries.filter { tuning in it.tunings }
            assertEquals(setOf(chordTuning.instrument), instruments.map { it.chordInstrument }.toSet(), chordTuning.id)
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
    fun `picking a tuning tunes the instrument picked, keeps the other instruments' tunings, and leaves no entry for standard`() {
        val preferences = testPreferences().copy(instrument = Instrument.GUITAR, tunings = mapOf("ukulele" to "ukulele_baritone", "banjo" to "banjo_open_d"))
        val dropped = preferences.withTuning(InstrumentTuning.GUITAR_DROP_D)
        assertEquals(mapOf("ukulele" to "ukulele_baritone", "banjo" to "banjo_open_d", "guitar" to "guitar_drop_d"), dropped.tunings)
        val twelveString = dropped.copy(instrument = Instrument.GUITAR_TWELVE_STRING).withTuning(InstrumentTuning.GUITAR_OPEN_G)
        assertEquals("guitar_open_g", twelveString.tunings["guitar_twelve_string"])
        assertEquals(InstrumentTuning.GUITAR_DROP_D, twelveString.tuningOf(Instrument.GUITAR))
        val standard = dropped.copy(instrument = Instrument.UKULELE).withTuning(InstrumentTuning.UKULELE)
        assertEquals(mapOf("guitar" to "guitar_drop_d", "banjo" to "banjo_open_d"), standard.tunings)
    }

    @Test
    fun `a tuning of another instrument than the one picked changes nothing`() {
        val preferences = testPreferences().copy(instrument = Instrument.UKULELE)
        assertEquals(preferences, preferences.withTuning(InstrumentTuning.GUITAR_DROP_D))
    }

    @Test
    fun `the mandolin and the twelve-string guitar are listened to by their pairs, every other instrument by its strings`() {
        assertEquals(StringCourses.UNISON_PAIRS, Instrument.MANDOLIN.courses)
        assertEquals(StringCourses.TWELVE_STRING, Instrument.GUITAR_TWELVE_STRING.courses)
        Instrument.entries.filterNot { it == Instrument.MANDOLIN || it.isTwelveString }.forEach { assertEquals(StringCourses.SINGLE, it.courses, it.id) }
    }

    @Test
    fun `the twelve-string guitar is drawn as the guitar in its own tuning`() {
        val preferences = testPreferences().copy(
            instrument = Instrument.GUITAR_TWELVE_STRING,
            tunings = mapOf("guitar" to "guitar_drop_d", "guitar_twelve_string" to "guitar_dadgad"),
        )
        assertEquals(ChordTuning.GUITAR_DADGAD, preferences.chordTuning())
        assertEquals(InstrumentTuning.GUITAR_DADGAD, preferences.instrumentTuning)
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
