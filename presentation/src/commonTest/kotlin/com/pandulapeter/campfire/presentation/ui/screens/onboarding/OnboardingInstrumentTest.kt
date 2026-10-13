/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.domain.UserPreferences.ChordInstrument
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingInstrumentTest {

    @Test
    fun `every instrument is read back from the settings it writes`() {
        OnboardingInstrument.entries.forEach { instrument ->
            val tuning = instrument.tuningFor(null)
            assertEquals(instrument, onboardingInstrumentOf(instrument.chordInstrumentFor(tuning), tuning))
        }
    }

    @Test
    fun `a fresh installation has no instrument picked`() {
        assertNull(onboardingInstrumentOf(ChordInstrument.GUITAR, null))
    }

    @Test
    fun `settings mixed by hand have no instrument picked`() {
        assertNull(onboardingInstrumentOf(ChordInstrument.UKULELE, InstrumentTuning.GUITAR))
        assertNull(onboardingInstrumentOf(ChordInstrument.GUITAR, InstrumentTuning.BASS))
    }

    @Test
    fun `any tuning of an instrument reads as that instrument`() {
        assertEquals(OnboardingInstrument.GUITAR, onboardingInstrumentOf(ChordInstrument.OTHER, InstrumentTuning.GUITAR_SEVEN_STRING_DROP_A))
        assertEquals(OnboardingInstrument.UKULELE, onboardingInstrumentOf(ChordInstrument.UKULELE, InstrumentTuning.UKULELE_LOW_G))
        assertEquals(OnboardingInstrument.BASS, onboardingInstrumentOf(ChordInstrument.OTHER, InstrumentTuning.BASS_FIVE_STRING))
    }

    @Test
    fun `the diagrams are only drawn for the tunings they know`() {
        assertEquals(ChordInstrument.GUITAR, OnboardingInstrument.GUITAR.chordInstrumentFor(InstrumentTuning.GUITAR))
        assertEquals(ChordInstrument.GUITAR, OnboardingInstrument.GUITAR.chordInstrumentFor(InstrumentTuning.GUITAR_DROP_D))
        assertEquals(ChordInstrument.OTHER, OnboardingInstrument.GUITAR.chordInstrumentFor(InstrumentTuning.GUITAR_OPEN_G))
        assertEquals(ChordInstrument.OTHER, OnboardingInstrument.GUITAR.chordInstrumentFor(InstrumentTuning.GUITAR_SEVEN_STRING))
        assertEquals(ChordInstrument.UKULELE, OnboardingInstrument.UKULELE.chordInstrumentFor(InstrumentTuning.UKULELE_LOW_G))
        assertEquals(ChordInstrument.UKULELE, OnboardingInstrument.UKULELE.chordInstrumentFor(InstrumentTuning.UKULELE_BARITONE))
        assertEquals(ChordInstrument.OTHER, OnboardingInstrument.MANDOLIN.chordInstrumentFor(InstrumentTuning.MANDOLIN_GDGD))
        assertEquals(ChordInstrument.KEYBOARD, OnboardingInstrument.KEYBOARD.chordInstrumentFor(null))
        assertEquals(ChordInstrument.OTHER, OnboardingInstrument.BASS.chordInstrumentFor(InstrumentTuning.BASS))
    }

    @Test
    fun `guitar diagrams over a tuning they are not drawn for have no instrument picked`() {
        assertNull(onboardingInstrumentOf(ChordInstrument.GUITAR, InstrumentTuning.GUITAR_OPEN_G))
    }

    @Test
    fun `the diagrams follow the tuning where they know it, and go back to standard where they do not`() {
        assertEquals(ChordTuning.GUITAR_DROP_D, OnboardingInstrument.GUITAR.chordTuningWrittenFor(InstrumentTuning.GUITAR_DROP_D))
        assertEquals(ChordTuning.UKULELE_BARITONE, OnboardingInstrument.UKULELE.chordTuningWrittenFor(InstrumentTuning.UKULELE_BARITONE))
        assertEquals(ChordTuning.GUITAR, OnboardingInstrument.GUITAR.chordTuningWrittenFor(InstrumentTuning.GUITAR_OPEN_G))
        assertEquals(ChordTuning.GUITAR, OnboardingInstrument.GUITAR.chordTuningWrittenFor(InstrumentTuning.GUITAR_EIGHT_STRING))
        assertNull(OnboardingInstrument.KEYBOARD.chordTuningWrittenFor(null))
        assertNull(OnboardingInstrument.BASS.chordTuningWrittenFor(InstrumentTuning.BASS))
    }

    @Test
    fun `the guitar offers its own tunings and the standard one of its seven and eight strings`() {
        val offered = OnboardingInstrument.GUITAR.tuningsOffered(InstrumentTuning.GUITAR)
        assertEquals(InstrumentTuning.GUITAR, offered.first())
        assertTrue(InstrumentTuning.GUITAR_DROP_D in offered)
        assertTrue(InstrumentTuning.GUITAR_SEVEN_STRING in offered && InstrumentTuning.GUITAR_EIGHT_STRING in offered)
        assertFalse(InstrumentTuning.GUITAR_SEVEN_STRING_DROP_A in offered)
    }

    @Test
    fun `a tuning of the instrument that is not offered is added, so that it shows picked`() {
        assertEquals(InstrumentTuning.GUITAR_SEVEN_STRING_DROP_A, OnboardingInstrument.GUITAR.tuningsOffered(InstrumentTuning.GUITAR_SEVEN_STRING_DROP_A).last())
        assertFalse(InstrumentTuning.BASS in OnboardingInstrument.GUITAR.tuningsOffered(InstrumentTuning.BASS))
    }

    @Test
    fun `an instrument the tuner has no strings for offers no tunings`() {
        assertTrue(OnboardingInstrument.KEYBOARD.tuningsOffered(null).isEmpty())
        assertTrue(OnboardingInstrument.OTHER.tuningsOffered(InstrumentTuning.GUITAR).isEmpty())
    }

    @Test
    fun `picking the instrument already tuned for keeps its tuning`() {
        assertEquals(InstrumentTuning.GUITAR_DROP_D, OnboardingInstrument.GUITAR.tuningFor(InstrumentTuning.GUITAR_DROP_D))
    }

    @Test
    fun `picking another instrument tunes to its standard tuning`() {
        assertEquals(InstrumentTuning.UKULELE, OnboardingInstrument.UKULELE.tuningFor(InstrumentTuning.GUITAR_DROP_D))
        assertEquals(InstrumentTuning.GUITAR, OnboardingInstrument.GUITAR.tuningFor(null))
    }

    @Test
    fun `an instrument without strings tunes chromatically`() {
        assertNull(OnboardingInstrument.KEYBOARD.tuningFor(InstrumentTuning.GUITAR))
        assertNull(OnboardingInstrument.OTHER.tuningFor(InstrumentTuning.VIOLIN))
    }

    @Test
    fun `without the tuner only the instruments the diagrams are drawn for are offered, and Something else`() {
        assertEquals(
            listOf(OnboardingInstrument.GUITAR, OnboardingInstrument.UKULELE, OnboardingInstrument.MANDOLIN, OnboardingInstrument.KEYBOARD, OnboardingInstrument.OTHER),
            onboardingInstruments(isTunerEnabled = false),
        )
    }

    @Test
    fun `with the tuner every instrument is offered`() {
        assertEquals(OnboardingInstrument.entries, onboardingInstruments(isTunerEnabled = true))
    }
}
