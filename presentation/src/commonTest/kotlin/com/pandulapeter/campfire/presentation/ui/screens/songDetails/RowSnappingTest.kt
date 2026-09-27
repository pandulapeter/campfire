/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import kotlin.test.Test
import kotlin.test.assertEquals

class RowSnappingTest {

    private fun snap(target: Float, dividers: List<Int>, viewport: Int = 1000, maxValue: Int = 3000) = snappedScrollTarget(
        target = target,
        dividerOffsets = dividers,
        viewportHeight = viewport,
        maxValue = maxValue,
    )

    @Test
    fun rowsShorterThanTheViewportSnapToTheNearestDivider() {
        assertEquals(800f, snap(target = 700f, dividers = listOf(800, 1600)))
        assertEquals(800f, snap(target = 1100f, dividers = listOf(800, 1600)))
        assertEquals(1600f, snap(target = 1300f, dividers = listOf(800, 1600)))
        assertEquals(0f, snap(target = 300f, dividers = listOf(800, 1600)))
    }

    @Test
    fun theEndsOfTheSongAreSnapPoints() {
        assertEquals(3000f, snap(target = 2900f, dividers = listOf(2400)))
        assertEquals(0f, snap(target = -500f, dividers = listOf(800)))
        assertEquals(3000f, snap(target = 5000f, dividers = listOf(800)))
    }

    @Test
    fun aRowTallerThanTheViewportScrollsFreelyUntilItsBottomIsReached() {
        // The row from 500 to 2500 has its bottom at the bottom of the viewport at 1500.
        assertEquals(1200f, snap(target = 1200f, dividers = listOf(500, 2500)))
        assertEquals(500f, snap(target = 500f, dividers = listOf(500, 2500)))
        assertEquals(1500f, snap(target = 1700f, dividers = listOf(500, 2500)))
        assertEquals(2500f, snap(target = 2200f, dividers = listOf(500, 2500)))
    }

    @Test
    fun aDividerPastTheEndOfTheScrollIsTheEnd() {
        assertEquals(3000f, snap(target = 2800f, dividers = listOf(2000, 3400)))
        assertEquals(2000f, snap(target = 2400f, dividers = listOf(2000, 3400)))
    }
}
