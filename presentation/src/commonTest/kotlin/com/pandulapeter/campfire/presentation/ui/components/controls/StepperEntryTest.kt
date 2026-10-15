/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.controls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StepperEntryTest {
    @Test
    fun `a typed number within the range is set as it is`() {
        assertEquals(132, typedNumber("132", 30..300))
        assertEquals(30, typedNumber("30", 30..300))
    }

    @Test
    fun `the numbers passed on the way to one of a narrow range set nothing`() {
        assertNull(typedNumber("4", 415..466))
        assertNull(typedNumber("44", 415..466))
        assertEquals(440, typedNumber("440", 415..466))
    }

    @Test
    fun `a number past the end of the range or nothing typed sets nothing`() {
        assertNull(typedNumber("500", 415..466))
        assertNull(typedNumber("", 30..300))
    }

    @Test
    fun `the text around the number is what the written value has around its last occurrence`() {
        assertEquals("A4 = " to " Hz", affixesAround("A4 = 440 Hz", 440))
        assertEquals("A4 = " to " Hz", affixesAround("A4 = 4 Hz", 4))
        assertEquals("" to "", affixesAround("120", 120))
    }

    @Test
    fun `a written value without the number has nothing around it`() {
        assertEquals("" to "", affixesAround("Allegro", 120))
    }
}
