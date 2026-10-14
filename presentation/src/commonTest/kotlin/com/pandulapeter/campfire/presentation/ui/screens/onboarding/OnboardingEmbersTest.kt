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

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingEmbersTest {

    private val embers = onboardingEmbers(random = Random(42), count = 200, colorCount = 3)

    @Test
    fun `every ember has burnt out when the celebration ends`() {
        assertTrue(embers.all { it.frameAt(1f) == null })
    }

    @Test
    fun `an ember is not drawn before it catches`() {
        val ember = embers.first { it.delay > 0f }
        assertNull(ember.frameAt(ember.delay / 2f))
    }

    @Test
    fun `an ember rises from under the window's bottom edge and dims as it goes`() {
        embers.forEach { ember ->
            val early = assertNotNull(ember.frameAt(ember.delay + ember.lifetime * 0.1f))
            val late = assertNotNull(ember.frameAt(ember.delay + ember.lifetime * 0.9f))
            assertTrue(assertNotNull(ember.frameAt(ember.delay)).y > 1f)
            assertTrue(late.y < early.y)
            assertTrue(late.alpha < early.alpha)
        }
    }

    @Test
    fun `embers keep to the colors they are given`() {
        assertTrue(embers.all { it.colorIndex in 0..2 })
    }
}
