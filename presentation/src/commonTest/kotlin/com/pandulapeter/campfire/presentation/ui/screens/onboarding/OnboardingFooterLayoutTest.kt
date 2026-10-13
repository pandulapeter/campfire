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

class OnboardingFooterLayoutTest {

    @Test
    fun `slots that fit on both sides center the dots`() {
        assertEquals((600 - 112) / 2, onboardingDotsStart(width = 600, dotsWidth = 112, startSlotWidth = 100, endSlotWidth = 120, gap = 8))
    }

    @Test
    fun `a wide end slot moves the dots toward the start, leaving exactly the gap to it`() {
        val start = onboardingDotsStart(width = 400, dotsWidth = 112, startSlotWidth = 60, endSlotWidth = 180, gap = 4)
        assertEquals(400 - 180 - 4, start + 112)
        assertTrue(start >= 60 + 4)
    }

    @Test
    fun `a wide start slot moves the dots no further than the end slot allows`() {
        val start = onboardingDotsStart(width = 400, dotsWidth = 112, startSlotWidth = 200, endSlotWidth = 80, gap = 4)
        assertEquals(200 + 4, start)
        assertTrue(start + 112 + 4 + 80 <= 400)
    }

    @Test
    fun `the dots stay where they are whichever label a slot shows`() {
        // A slot is as wide as the widest label it can hold, so every page asks with the same widths.
        val startSlotWidth = listOf(64, 90).max()
        val endSlotWidth = listOf(70, 113).max()
        val starts = listOf(64 to 70, 90 to 70, 64 to 113, 90 to 113).map {
            onboardingDotsStart(width = 328, dotsWidth = 112, startSlotWidth = startSlotWidth, endSlotWidth = endSlotWidth, gap = 0)
        }
        assertEquals(1, starts.distinct().size)
    }

    @Test
    fun `slots that cannot all fit leave the dots centered`() {
        assertEquals((328 - 112) / 2, onboardingDotsStart(width = 328, dotsWidth = 112, startSlotWidth = 140, endSlotWidth = 150, gap = 0))
    }

    @Test
    fun `start playing and skip fit beside six dots on a 360dp phone`() {
        val start = onboardingDotsStart(width = 328, dotsWidth = 112, startSlotWidth = 90, endSlotWidth = 113, gap = 8)
        assertTrue(start - 90 >= 6)
        assertTrue(328 - 113 - (start + 112) >= 6)
    }

    @Test
    fun `room for less than the gap is shared between the two sides`() {
        assertEquals(95, onboardingDotsStart(width = 300, dotsWidth = 100, startSlotWidth = 90, endSlotWidth = 100, gap = 8))
    }
}
