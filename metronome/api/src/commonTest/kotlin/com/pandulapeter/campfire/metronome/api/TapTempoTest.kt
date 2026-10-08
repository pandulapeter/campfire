/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.metronome.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class TapTempoTest {

    private val time = TestTimeSource()
    private val tapTempo = TapTempo(time)

    private fun tapAfter(gap: Duration): Int? {
        time += gap
        return tapTempo.tap()
    }

    @Test
    fun `the first tap gives no tempo`() = assertNull(tapTempo.tap())

    @Test
    fun `two taps give a tempo`() {
        tapTempo.tap()
        assertEquals(120, tapAfter(500.milliseconds))
    }

    @Test
    fun `a steady run gives its tempo`() {
        tapTempo.tap()
        repeat(10) { tapAfter(625.milliseconds) }
        assertEquals(96, tapAfter(625.milliseconds))
    }

    @Test
    fun `one late tap does not move the tempo`() {
        tapTempo.tap()
        repeat(5) { tapAfter(500.milliseconds) }
        assertEquals(120, tapAfter(800.milliseconds))
    }

    @Test
    fun `a new tempo takes over once it is most of the window`() {
        tapTempo.tap()
        repeat(8) { tapAfter(500.milliseconds) }
        assertEquals(listOf(120, 120, 120, 96, 80), List(5) { tapAfter(750.milliseconds) })
    }

    @Test
    fun `a long pause starts a new series`() {
        tapTempo.tap()
        tapAfter(500.milliseconds)
        assertNull(tapAfter(3.seconds))
        assertEquals(60, tapAfter(1.seconds))
    }

    @Test
    fun `the tempo is clamped to the range`() {
        tapTempo.tap()
        assertEquals(300, tapAfter(50.milliseconds))
        tapTempo.reset()
        tapTempo.tap()
        assertEquals(30, tapAfter(1_999.milliseconds))
    }
}
