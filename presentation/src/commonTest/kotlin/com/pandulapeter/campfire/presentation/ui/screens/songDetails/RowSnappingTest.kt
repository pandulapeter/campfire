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

    /** Unless given, every row's content reaches down to where the next one rests, and the last one's to the end. */
    private fun snap(
        target: Float,
        dividers: List<Int>,
        start: Float = 0f,
        bottoms: List<Int> = dividers.drop(1) + (3000 + 1000),
        viewport: Int = 1000,
        maxValue: Int = 3000,
    ) = snappedScrollTarget(
        start = start,
        target = target,
        rows = SongRows(restingOffsets = dividers, bottoms = bottoms),
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
        assertEquals(0f, snap(target = -500f, dividers = listOf(800)))
        assertEquals(3000f, snap(start = 900f, target = 5000f, dividers = listOf(800)))
    }

    @Test
    fun aTallRowIsLandedOnAtItsTop() {
        // The row from 500 to 2500 has its bottom at the bottom of the viewport at 1500.
        assertEquals(500f, snap(start = 0f, target = 1200f, dividers = listOf(500, 2500)))
        assertEquals(500f, snap(start = 2500f, target = 1700f, dividers = listOf(500, 2500)))
        assertEquals(2500f, snap(start = 0f, target = 2200f, dividers = listOf(500, 2500)))
    }

    @Test
    fun aTallRowScrollsFreelyOnceItIsBeingRead() {
        assertEquals(1200f, snap(start = 500f, target = 1200f, dividers = listOf(500, 2500)))
        assertEquals(700f, snap(start = 1200f, target = 700f, dividers = listOf(500, 2500)))
        assertEquals(1500f, snap(start = 900f, target = 1700f, dividers = listOf(500, 2500)))
        assertEquals(2500f, snap(start = 900f, target = 2200f, dividers = listOf(500, 2500)))
    }

    @Test
    fun aTallLastRowScrollsFreelyToTheEndOfTheSong() {
        assertEquals(2400f, snap(start = 1000f, target = 2900f, dividers = listOf(2400)))
        assertEquals(2900f, snap(start = 2400f, target = 2900f, dividers = listOf(2400)))
        assertEquals(2600f, snap(start = 2900f, target = 2600f, dividers = listOf(2400)))
    }

    @Test
    fun theSpaceAfterARowDoesNotMakeItTall() {
        // Both rows end 700 below where they rest, and are followed by empty space down to the next one.
        val dividers = listOf(300, 1400)
        val bottoms = listOf(1000, 2100)
        assertEquals(300f, snap(start = 700f, target = 700f, dividers = dividers, bottoms = bottoms, maxValue = 1400))
        assertEquals(1400f, snap(start = 300f, target = 1000f, dividers = dividers, bottoms = bottoms, maxValue = 1400))
    }

    @Test
    fun aDividerPastTheEndOfTheScrollIsTheEnd() {
        assertEquals(3000f, snap(target = 2800f, dividers = listOf(2000, 3400)))
        assertEquals(2000f, snap(target = 2400f, dividers = listOf(2000, 3400)))
    }

    @Test
    fun theButtonsStepToTheNeighbouringRows() {
        val rows = listOf(300, 1300, 2300)
        assertEquals(300, nextStepOffset(scroll = 0, stepOffsets = rows, maxValue = 3000))
        assertEquals(null, previousStepOffset(scroll = 0, stepOffsets = rows, maxValue = 3000))
        // The header is a row of its own, so only the very top of the song has nothing above it.
        assertEquals(0, previousStepOffset(scroll = 300, stepOffsets = rows, maxValue = 3000))
        assertEquals(0, previousStepOffset(scroll = 150, stepOffsets = rows, maxValue = 3000))
        assertEquals(2300, nextStepOffset(scroll = 1300, stepOffsets = rows, maxValue = 3000))
        assertEquals(300, previousStepOffset(scroll = 1300, stepOffsets = rows, maxValue = 3000))
        // Inside a tall row, the previous button goes back to its own top.
        assertEquals(1300, previousStepOffset(scroll = 1800, stepOffsets = rows, maxValue = 3000))
        assertEquals(null, nextStepOffset(scroll = 2300, stepOffsets = rows, maxValue = 3000))
        // A row the scroll cannot reach the top of is as far as it goes, and there is no row after that.
        assertEquals(2000, nextStepOffset(scroll = 1300, stepOffsets = rows, maxValue = 2000))
        assertEquals(null, nextStepOffset(scroll = 2000, stepOffsets = rows, maxValue = 2000))
    }
}
