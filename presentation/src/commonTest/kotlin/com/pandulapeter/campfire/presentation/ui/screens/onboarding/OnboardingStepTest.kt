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
        isMetronomeEnabled: Boolean = true,
        isTunerEnabled: Boolean = true,
    ) = onboardingSteps(
        canConnectDropbox = canConnectDropbox,
        areChordsEnabled = areChordsEnabled,
        isMetronomeEnabled = isMetronomeEnabled,
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
    fun `a singer is only asked about the look, the role, and the cloud folder`() {
        assertEquals(
            listOf(OnboardingStep.WELCOME, OnboardingStep.ROLE, OnboardingStep.SYNC, OnboardingStep.DONE),
            steps(areChordsEnabled = false, isMetronomeEnabled = false, isTunerEnabled = false),
        )
    }

    @Test
    fun `the tuner alone asks for the instrument but not the notation or what a song shows`() {
        assertEquals(
            listOf(OnboardingStep.WELCOME, OnboardingStep.ROLE, OnboardingStep.INSTRUMENT, OnboardingStep.SYNC, OnboardingStep.DONE),
            steps(areChordsEnabled = false, isMetronomeEnabled = false, isTunerEnabled = true),
        )
    }

    @Test
    fun `the metronome alone asks what a song shows, for its tempo`() {
        assertEquals(
            listOf(OnboardingStep.WELCOME, OnboardingStep.ROLE, OnboardingStep.AT_A_GLANCE, OnboardingStep.SYNC, OnboardingStep.DONE),
            steps(areChordsEnabled = false, isMetronomeEnabled = true, isTunerEnabled = false),
        )
    }

    @Test
    fun `the next and the previous step skip the ones that are not shown`() {
        val steps = steps(areChordsEnabled = false, isMetronomeEnabled = false, isTunerEnabled = false)
        assertEquals(OnboardingStep.SYNC, nextOnboardingStep(OnboardingStep.ROLE, steps))
        assertEquals(OnboardingStep.ROLE, previousOnboardingStep(OnboardingStep.SYNC, steps))
        assertNull(nextOnboardingStep(OnboardingStep.DONE, steps))
        assertNull(previousOnboardingStep(OnboardingStep.WELCOME, steps))
    }

    @Test
    fun `a step that is no longer shown still leads on and back`() {
        val steps = steps(areChordsEnabled = false, isMetronomeEnabled = false, isTunerEnabled = false)
        assertEquals(OnboardingStep.SYNC, nextOnboardingStep(OnboardingStep.INSTRUMENT, steps))
        assertEquals(OnboardingStep.ROLE, previousOnboardingStep(OnboardingStep.INSTRUMENT, steps))
    }
}
