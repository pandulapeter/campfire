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

import com.pandulapeter.campfire.data.model.domain.UserPreferences.ChordInstrument
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OnboardingInstrumentTest {

    @Test
    fun `every instrument is read back from the settings it writes`() {
        OnboardingInstrument.entries.forEach { instrument ->
            assertEquals(instrument, onboardingInstrumentOf(instrument.chordInstrument, instrument.tuningFor(null)))
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
        assertEquals(OnboardingInstrument.GUITAR, onboardingInstrumentOf(ChordInstrument.GUITAR, InstrumentTuning.GUITAR_SEVEN_STRING_DROP_A))
        assertEquals(OnboardingInstrument.BASS, onboardingInstrumentOf(ChordInstrument.OTHER, InstrumentTuning.BASS_FIVE_STRING))
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
            listOf(OnboardingInstrument.GUITAR, OnboardingInstrument.UKULELE, OnboardingInstrument.KEYBOARD, OnboardingInstrument.OTHER),
            onboardingInstruments(isTunerEnabled = false),
        )
    }

    @Test
    fun `with the tuner every instrument is offered`() {
        assertEquals(OnboardingInstrument.entries, onboardingInstruments(isTunerEnabled = true))
    }
}
