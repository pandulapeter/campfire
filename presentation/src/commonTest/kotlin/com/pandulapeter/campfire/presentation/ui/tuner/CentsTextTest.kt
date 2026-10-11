/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import kotlin.test.Test
import kotlin.test.assertEquals

class CentsTextTest {

    @Test
    fun `a flat reading is written with a minus sign and a sharp one with a plus`() {
        assertEquals("−18", signedCents(-18.4f))
        assertEquals("+4", signedCents(3.5f))
    }

    @Test
    fun `a reading that rounds to nothing is a plain zero either side of the centre`() {
        assertEquals("0", signedCents(0.3f))
        assertEquals("0", signedCents(-0.4f))
    }

    @Test
    fun `nothing heard is no reading whatever the in tune flag says`() {
        assertEquals(TunerReadingKind.NONE, tunerReadingKindOf(cents = null, isInTune = false))
        assertEquals(TunerReadingKind.NONE, tunerReadingKindOf(cents = null, isInTune = true))
    }

    @Test
    fun `a reading in tune is in tune at any cents`() {
        assertEquals(TunerReadingKind.IN_TUNE, tunerReadingKindOf(cents = 0f, isInTune = true))
        assertEquals(TunerReadingKind.IN_TUNE, tunerReadingKindOf(cents = 3.2f, isInTune = true))
    }

    @Test
    fun `a reading not in tune is a number of cents however close it is`() {
        assertEquals(TunerReadingKind.CENTS, tunerReadingKindOf(cents = 0f, isInTune = false))
        assertEquals(TunerReadingKind.CENTS, tunerReadingKindOf(cents = -12.6f, isInTune = false))
        assertEquals(TunerReadingKind.CENTS, tunerReadingKindOf(cents = 7.1f, isInTune = false))
    }
}
