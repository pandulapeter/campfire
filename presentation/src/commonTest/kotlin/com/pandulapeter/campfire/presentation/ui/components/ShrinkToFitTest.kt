/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ShrinkToFitTest {

    @Test
    fun `the largest size that fits is chosen`() {
        assertEquals(10.5f, largestFittingSize(smallest = 9f, largest = 12f, step = 0.25f) { it < 10.6f })
    }

    @Test
    fun `text that fits at the largest size keeps it`() {
        assertEquals(12f, largestFittingSize(smallest = 9f, largest = 12f, step = 0.25f) { true })
    }

    @Test
    fun `text that fits at no size gets the smallest`() {
        assertEquals(9f, largestFittingSize(smallest = 9f, largest = 12f, step = 0.25f) { false })
    }

    @Test
    fun `a largest size between two steps is tried too`() {
        assertEquals(12.1f, largestFittingSize(smallest = 9f, largest = 12.1f, step = 0.25f) { true })
    }
}
