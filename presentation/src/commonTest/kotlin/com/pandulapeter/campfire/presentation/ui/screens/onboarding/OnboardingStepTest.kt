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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OnboardingStepTest {

    private fun steps(
        canConnectDropbox: Boolean = true,
        areChordsEnabled: Boolean = true,
        isTunerEnabled: Boolean = true,
    ) = onboardingSteps(
        canConnectDropbox = canConnectDropbox,
        areChordsEnabled = areChordsEnabled,
        isTunerEnabled = isTunerEnabled,
    )

    @Test
    fun `every step is shown with every feature on and Dropbox available`() {
        assertEquals(OnboardingStep.entries, steps())
    }

    @Test
    fun `a build without Dropbox leaves the cloud folder out`() {
        assertEquals(OnboardingStep.entries - OnboardingStep.SYNC, steps(canConnectDropbox = false))
    }

    @Test
    fun `without the chords and the tuner the instrument and the notation are not asked`() {
        assertEquals(
            listOf(OnboardingStep.WELCOME, OnboardingStep.FEATURES, OnboardingStep.SYNC),
            steps(areChordsEnabled = false, isTunerEnabled = false),
        )
    }

    @Test
    fun `the tuner alone asks for the instrument but not the notation`() {
        assertEquals(
            listOf(OnboardingStep.WELCOME, OnboardingStep.FEATURES, OnboardingStep.INSTRUMENT, OnboardingStep.SYNC),
            steps(areChordsEnabled = false, isTunerEnabled = true),
        )
    }

    @Test
    fun `the next and the previous step skip the ones that are not shown`() {
        val steps = steps(areChordsEnabled = false, isTunerEnabled = false)
        assertEquals(OnboardingStep.SYNC, nextOnboardingStep(OnboardingStep.FEATURES, steps))
        assertEquals(OnboardingStep.FEATURES, previousOnboardingStep(OnboardingStep.SYNC, steps))
        assertNull(nextOnboardingStep(OnboardingStep.SYNC, steps))
        assertNull(previousOnboardingStep(OnboardingStep.WELCOME, steps))
    }

    @Test
    fun `a step that is no longer shown still leads on and back`() {
        val steps = steps(areChordsEnabled = false, isTunerEnabled = false)
        assertEquals(OnboardingStep.SYNC, nextOnboardingStep(OnboardingStep.INSTRUMENT, steps))
        assertEquals(OnboardingStep.FEATURES, previousOnboardingStep(OnboardingStep.INSTRUMENT, steps))
    }
}
