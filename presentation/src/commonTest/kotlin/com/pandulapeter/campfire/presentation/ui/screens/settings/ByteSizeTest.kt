/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteSizeTest {

    @Test
    fun lessThanAKilobyteIsCountedInBytes() {
        assertEquals(ByteSize(unit = 0, whole = 0), byteSizeOf(0))
        assertEquals(ByteSize(unit = 0, whole = 999), byteSizeOf(999))
    }

    @Test
    fun belowTenThereIsOneDecimal() {
        assertEquals(ByteSize(unit = 1, whole = 1, tenths = 0), byteSizeOf(1_000))
        assertEquals(ByteSize(unit = 1, whole = 1, tenths = 0), byteSizeOf(1_049))
        assertEquals(ByteSize(unit = 1, whole = 1, tenths = 1), byteSizeOf(1_050))
        assertEquals(ByteSize(unit = 2, whole = 2, tenths = 5), byteSizeOf(2_500_000))
    }

    @Test
    fun fromTenThereIsNone() {
        assertEquals(ByteSize(unit = 1, whole = 10), byteSizeOf(9_950))
        assertEquals(ByteSize(unit = 1, whole = 999), byteSizeOf(999_400))
    }

    @Test
    fun theUnitIsSettledOnTheRoundedNumber() {
        assertEquals(ByteSize(unit = 2, whole = 1, tenths = 0), byteSizeOf(999_960))
        assertEquals(ByteSize(unit = 1, whole = 999), byteSizeOf(999_499))
    }

    @Test
    fun theLargestUnitTakesEverythingAboveIt() {
        assertEquals(ByteSize(unit = 3, whole = 5_500), byteSizeOf(5_500_000_000_000))
    }
}
