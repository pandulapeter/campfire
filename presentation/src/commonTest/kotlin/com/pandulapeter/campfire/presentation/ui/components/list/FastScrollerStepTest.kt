/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.list

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FastScrollerStepTest {

    @Test
    fun `an exact estimate scrolls by the thumb's step`() {
        assertEquals(12f, fastScrollerStep(step = 12f, error = 0f))
        assertEquals(-12f, fastScrollerStep(step = -12f, error = 0f))
    }

    @Test
    fun `the list never goes against the thumb however wrong the estimate is`() {
        for (error in listOf(-10_000f, -100f, -1f, 0f, 1f, 100f, 10_000f)) {
            assertTrue(fastScrollerStep(step = 5f, error = error) > 0f)
            assertTrue(fastScrollerStep(step = -5f, error = error) < 0f)
        }
    }

    @Test
    fun `a wrong estimate is caught up with at most twice the thumb's speed`() {
        assertEquals(10f, fastScrollerStep(step = 5f, error = 10_000f))
        assertEquals(2.5f, fastScrollerStep(step = 5f, error = -10_000f))
        assertEquals(-10f, fastScrollerStep(step = -5f, error = -10_000f))
    }

    @Test
    fun `a thumb dragged at an even speed brings the list to where it says`() {
        var position = 0f
        var estimate = 300f
        repeat(150) {
            estimate += 4f
            position += fastScrollerStep(step = 4f, error = estimate - position - 4f)
        }
        assertEquals(estimate, position, absoluteTolerance = 1f)
    }
}
