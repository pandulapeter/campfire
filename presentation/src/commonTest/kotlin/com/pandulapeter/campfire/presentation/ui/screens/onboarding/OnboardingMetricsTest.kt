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

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnboardingMetricsTest {

    @Test
    fun `the smallest phone gives up none of its width to the side margin`() {
        assertEquals(0.dp, onboardingSideMargin(360.dp))
        assertEquals(0.dp, onboardingSideMargin(320.dp))
    }

    @Test
    fun `the side margin grows with the width and then stays`() {
        val margins = listOf(360, 370, 380, 390, 400, 600, 1200).map { onboardingSideMargin(it.dp) }
        assertTrue(margins.zipWithNext().all { (narrower, wider) -> wider >= narrower })
        assertTrue(onboardingSideMargin(400.dp) > 0.dp)
        assertEquals(onboardingSideMargin(600.dp), onboardingSideMargin(1200.dp))
    }

    @Test
    fun `phones keep no bottom margin under the bar`() {
        assertEquals(0.dp, onboardingBottomMargin(width = 360.dp, height = 640.dp))
        assertEquals(0.dp, onboardingBottomMargin(width = 1400.dp, height = ONBOARDING_ROOMY_MIN_HEIGHT))
        assertEquals(0.dp, onboardingBottomMargin(width = 412.dp, height = 915.dp))
    }

    @Test
    fun `there is no bottom margin while the bar spans the window`() {
        assertEquals(0.dp, onboardingBottomMargin(width = ONBOARDING_CONTENT_WIDTH, height = 2000.dp))
    }

    @Test
    fun `the bottom margin grows with the height and then stays`() {
        val margins = listOf(720, 800, 900, 1000, 1100, 1400, 2000).map { onboardingBottomMargin(width = 1400.dp, height = it.dp) }
        assertTrue(margins.zipWithNext().all { (shorter, taller) -> taller >= shorter })
        assertTrue(onboardingBottomMargin(width = 1400.dp, height = 900.dp) > 0.dp)
        assertEquals(onboardingBottomMargin(width = 1400.dp, height = 1400.dp), onboardingBottomMargin(width = 1400.dp, height = 2000.dp))
    }

    @Test
    fun `the bottom margin grows with the width once the bar stops spanning the window`() {
        val widths = listOf(0, 20, 40, 80, 160, 800).map { ONBOARDING_CONTENT_WIDTH + it.dp }
        val margins = widths.map { onboardingBottomMargin(width = it, height = 2000.dp) }
        assertTrue(margins.zipWithNext().all { (narrower, wider) -> wider >= narrower })
        assertTrue(margins[2] > 0.dp)
        assertTrue(margins[2] < margins.last())
    }
}
