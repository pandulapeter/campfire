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

class AnchorTravelTest {

    private fun arrival(index: Int, top: Int, vararg shifts: Pair<Int, Int>) = arrivalShift(
        index = index,
        top = top,
        height = 100,
        shifts = shifts.map { (index, shift) -> ItemShift(index = index, shift = shift) },
        previousViewportStart = 0,
        previousViewportEnd = 1000,
    )

    @Test
    fun `an item below the ones on screen travels up with the last of them`() = assertEquals(400, arrival(index = 5, top = 700, 3 to 400, 4 to 400))

    @Test
    fun `an item below comes in from no higher than the bottom of the screen`() = assertEquals(300, arrival(index = 5, top = 700, 4 to 100))

    @Test
    fun `an item above the ones on screen travels down with the first of them`() = assertEquals(-200, arrival(index = 1, top = 50, 2 to -200, 3 to -200))

    @Test
    fun `an item above comes in from no lower than the top of the screen`() = assertEquals(-150, arrival(index = 1, top = 50, 2 to -10))

    @Test
    fun `an item in among the ones on screen fades in`() = assertEquals(null, arrival(index = 3, top = 500, 2 to 100, 4 to 100))

    @Test
    fun `an item below a list moving down fades in`() = assertEquals(null, arrival(index = 5, top = 900, 4 to -100))

    @Test
    fun `an item with nothing on screen before it fades in`() = assertEquals(null, arrival(index = 5, top = 900))

    @Test
    fun `an item below travels up at least as far as any of the ones on screen before it`() =
        assertEquals(500, arrival(index = 5, top = 700, 3 to 500, 4 to 400))

    private fun departure(index: Int, top: Int, vararg shifts: Pair<Int, Int>) = departureShift(
        index = index,
        top = top,
        height = 100,
        shifts = shifts.map { (index, shift) -> ItemShift(index = index, shift = shift) },
        viewportStart = 0,
        viewportEnd = 1000,
    )

    @Test
    fun `an item pushed off the bottom travels down with the ones still on screen`() =
        assertEquals(-400, departure(index = 5, top = 700, 3 to -400, 4 to -400))

    @Test
    fun `an item pushed off the bottom ends below the screen`() = assertEquals(-200, departure(index = 5, top = 800, 4 to -50))

    @Test
    fun `an item pushed off the bottom travels down at least as far as any of the ones before it`() =
        assertEquals(-500, departure(index = 5, top = 700, 3 to -500, 4 to -400))

    @Test
    fun `an item pushed off the top travels up with the ones still on screen`() = assertEquals(300, departure(index = 1, top = 50, 2 to 300))

    @Test
    fun `an item pushed off the top ends above the screen`() = assertEquals(150, departure(index = 1, top = 50, 2 to 20))

    @Test
    fun `an item that leaves from among the ones on screen does not travel`() =
        assertEquals(null, departure(index = 3, top = 500, 2 to -100, 4 to -100))

    @Test
    fun `an item below a list moving up does not travel`() = assertEquals(null, departure(index = 5, top = 900, 4 to 100))
}
