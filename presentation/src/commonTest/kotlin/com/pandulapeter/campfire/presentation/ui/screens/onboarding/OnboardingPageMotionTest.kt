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
import kotlin.test.assertTrue

class OnboardingPageMotionTest {

    @Test
    fun `the page shown is drawn as it is laid out`() {
        listOf(onboardingHeaderMotion(0f), onboardingAnswersMotion(0f)).forEach { motion ->
            assertEquals(0f, motion.shift, absoluteTolerance = 0f)
            assertEquals(1f, motion.scale)
            assertEquals(1f, motion.alpha)
        }
    }

    @Test
    fun `a leaving question lags behind its page while its answers move ahead of it`() {
        assertTrue(onboardingHeaderMotion(0.5f).shift > 0f)
        assertTrue(onboardingAnswersMotion(0.5f).shift < 0f)
        assertTrue(onboardingHeaderMotion(-0.5f).shift < 0f)
        assertTrue(onboardingAnswersMotion(-0.5f).shift > 0f)
    }

    @Test
    fun `the question is never scaled, since the mark follows its place`() {
        listOf(-1f, -0.3f, 0.6f, 1f).forEach { assertEquals(1f, onboardingHeaderMotion(it).scale) }
    }

    @Test
    fun `a page a whole width away is invisible and its answers shrunk back`() {
        assertEquals(0f, onboardingHeaderMotion(1f).alpha)
        assertEquals(0f, onboardingAnswersMotion(-1f).alpha)
        assertTrue(onboardingAnswersMotion(1f).scale < 1f)
    }

    @Test
    fun `an offset past a page is held at a page`() {
        assertEquals(onboardingAnswersMotion(1f), onboardingAnswersMotion(2f))
        assertEquals(onboardingHeaderMotion(-1f), onboardingHeaderMotion(-3f))
    }
}
