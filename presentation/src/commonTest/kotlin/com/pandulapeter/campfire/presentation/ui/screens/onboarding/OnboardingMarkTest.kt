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

class OnboardingMarkTest {

    private val steps = listOf(OnboardingStep.WELCOME, OnboardingStep.LOOK, OnboardingStep.FEATURES)

    @Test
    fun `a page at rest is its own frame`() {
        assertEquals(OnboardingMarkFrame(OnboardingStep.LOOK, OnboardingStep.LOOK, 0f), onboardingMarkFrame(steps, 1f))
    }

    @Test
    fun `a turn is between the two pages it is between, by its offset`() {
        assertEquals(OnboardingMarkFrame(OnboardingStep.LOOK, OnboardingStep.FEATURES, 0.25f), onboardingMarkFrame(steps, 1.25f))
    }

    @Test
    fun `a position past either end stays on the end page`() {
        assertEquals(OnboardingMarkFrame(OnboardingStep.WELCOME, OnboardingStep.WELCOME, 0f), onboardingMarkFrame(steps, -0.3f))
        assertEquals(OnboardingMarkFrame(OnboardingStep.FEATURES, OnboardingStep.FEATURES, 0f), onboardingMarkFrame(steps, 2.4f))
    }

    @Test
    fun `no steps is no frame`() {
        assertNull(onboardingMarkFrame(emptyList(), 0f))
    }

    @Test
    fun `the mark moves between the tops of the two pages`() {
        val tops = mapOf(OnboardingStep.WELCOME to 100f, OnboardingStep.LOOK to 300f)
        assertEquals(150f, onboardingMarkTop(OnboardingMarkFrame(OnboardingStep.WELCOME, OnboardingStep.LOOK, 0.25f), tops))
    }

    @Test
    fun `a page whose top is not known yet borrows the other one's, and none known is no top`() {
        val tops = mapOf(OnboardingStep.WELCOME to 100f)
        assertEquals(100f, onboardingMarkTop(OnboardingMarkFrame(OnboardingStep.WELCOME, OnboardingStep.LOOK, 0.5f), tops))
        assertEquals(100f, onboardingMarkTop(OnboardingMarkFrame(OnboardingStep.LOOK, OnboardingStep.WELCOME, 0.5f), tops))
        assertNull(onboardingMarkTop(OnboardingMarkFrame(OnboardingStep.SYNC, OnboardingStep.SYNC, 0f), tops))
    }
}
