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

import com.pandulapeter.campfire.data.model.domain.UserPreferences.Instrument
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingInstrumentsTest {

    @Test
    fun `with the tuner on every instrument is offered`() {
        assertEquals(Instrument.entries, onboardingInstruments(isTunerEnabled = true))
    }

    @Test
    fun `with the tuner off only the instruments with diagrams and Other are offered`() {
        assertEquals(
            listOf(Instrument.GUITAR, Instrument.UKULELE, Instrument.BANJO, Instrument.MANDOLIN, Instrument.KEYBOARD, Instrument.OTHER),
            onboardingInstruments(isTunerEnabled = false),
        )
    }
}
