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
}
