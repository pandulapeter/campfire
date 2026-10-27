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
        canConnectSync: Boolean = true,
        areChordsEnabled: Boolean = true,
    ) = onboardingSteps(
        canConnectSync = canConnectSync,
        areChordsEnabled = areChordsEnabled,
    )

    @Test
    fun `every step is shown with every feature on and Dropbox available`() {
        assertEquals(OnboardingStep.entries, steps())
    }

    @Test
    fun `a build without Dropbox leaves the cloud folder out`() {
        assertEquals(OnboardingStep.entries - OnboardingStep.SYNC, steps(canConnectSync = false))
    }

    @Test
    fun `the instrument is asked before the features, and without the chords the notation is not asked`() {
        assertEquals(
            listOf(
                OnboardingStep.WELCOME,
                OnboardingStep.LOOK,
                OnboardingStep.INSTRUMENT,
                OnboardingStep.FEATURES,
                OnboardingStep.SONG_LAYOUT,
                OnboardingStep.SYNC,
            ),
            steps(areChordsEnabled = false),
        )
    }

    @Test
    fun `the next and the previous step skip the ones that are not shown`() {
        val steps = steps(areChordsEnabled = false)
        assertEquals(OnboardingStep.SONG_LAYOUT, nextOnboardingStep(OnboardingStep.FEATURES, steps))
        assertEquals(OnboardingStep.FEATURES, previousOnboardingStep(OnboardingStep.SONG_LAYOUT, steps))
        assertNull(nextOnboardingStep(OnboardingStep.SYNC, steps))
        assertNull(previousOnboardingStep(OnboardingStep.WELCOME, steps))
    }

    @Test
    fun `a step that is no longer shown still leads on and back`() {
        val steps = steps(areChordsEnabled = false)
        assertEquals(OnboardingStep.SONG_LAYOUT, nextOnboardingStep(OnboardingStep.NOTATION, steps))
        assertEquals(OnboardingStep.FEATURES, previousOnboardingStep(OnboardingStep.NOTATION, steps))
    }
}
