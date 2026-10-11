/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.tuner.implementation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LatestEstimateTest {

    private val window = FloatArray(4)
    private val range = 70f..400f
    private var detections = 0
    private val estimates = LatestEstimate { _, _, _ -> PitchEstimate(frequency = (++detections).toFloat(), clarity = 1f, level = 1f) }

    @Test
    fun `a window handed out again is answered with the estimate it had without being analysed again`() {
        val first = estimates.of(window, position = 1_024, range = range)
        assertSame(first, estimates.of(window, position = 1_024, range = range))
        assertSame(first, estimates.of(window, position = 1_024, range = range))
        assertEquals(1, detections)
    }

    @Test
    fun `a window at a new position is analysed`() {
        estimates.of(window, position = 1_024, range = range)
        val next = estimates.of(window, position = 2_048, range = range)
        assertEquals(2, detections)
        assertEquals(2f, next.frequency)
    }

    @Test
    fun `the same window searched in another range is analysed again`() {
        estimates.of(window, position = 1_024, range = range)
        val wider = estimates.of(window, position = 1_024, range = 40f..400f)
        assertEquals(2, detections)
        assertEquals(2f, wider.frequency)
    }

    @Test
    fun `a position after another one is answered with its own estimate rather than the one before`() {
        estimates.of(window, position = 1_024, range = range)
        estimates.of(window, position = 2_048, range = range)
        assertEquals(3f, estimates.of(window, position = 1_024, range = range).frequency)
        assertEquals(3, detections)
    }
}
