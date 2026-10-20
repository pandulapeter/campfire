/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.instrument

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstrumentPillWidthsTest {

    @Test
    fun `where both names fit the tuning may take everything the instrument leaves`() {
        assertEquals(80, tuningHalfMaxWidth(available = 200, instrumentWidth = 120))
    }

    @Test
    fun `where the instrument is too wide the tuning keeps its share`() {
        assertEquals(60, tuningHalfMaxWidth(available = 150, instrumentWidth = 140))
    }

    @Test
    fun `an instrument wider than the pill leaves the tuning its share`() {
        assertEquals(40, tuningHalfMaxWidth(available = 100, instrumentWidth = 300))
    }

    @Test
    fun `the tuning is never given more than the pill or less than nothing`() {
        for (available in listOf(-10, 0, 1, 50, 300)) {
            for (instrumentWidth in listOf(-50, 0, 10, 400)) {
                val width = tuningHalfMaxWidth(available, instrumentWidth)
                assertTrue(width in 0..maxOf(available, 0), "$width out of $available with an instrument of $instrumentWidth")
            }
        }
    }
}
