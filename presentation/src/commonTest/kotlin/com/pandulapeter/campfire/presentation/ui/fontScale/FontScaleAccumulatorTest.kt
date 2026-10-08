/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.fontScale

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

class FontScaleAccumulatorTest {

    @Test
    fun `changes smaller than the rounding add up`() {
        val accumulator = FontScaleAccumulator()
        var displayed = 1f
        repeat(10) { displayed = accumulator.next(displayed) { it + 0.002f }.roundedToPercent() }
        assertEquals(1.02f, displayed)
    }

    @Test
    fun `a scale set by something else starts the changes again from there`() {
        val accumulator = FontScaleAccumulator()
        accumulator.next(1f) { it + 0.004f }
        assertEquals(1.2f + 0.004f, accumulator.next(1.2f) { it + 0.004f }, absoluteTolerance = 0.0001f)
    }

    @Test
    fun `a change past a bound turns back from the bound`() {
        val accumulator = FontScaleAccumulator()
        assertEquals(UserPreferences.MAX_FONT_SCALE, accumulator.next(UserPreferences.MAX_FONT_SCALE) { it * 2f })
        assertEquals(UserPreferences.MAX_FONT_SCALE - 0.1f, accumulator.next(UserPreferences.MAX_FONT_SCALE) { it - 0.1f })
    }

    /** What the view model does to every value it is given. */
    private fun Float.roundedToPercent() = (this * 100).roundToInt() / 100f
}
