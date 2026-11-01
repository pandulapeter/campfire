/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songs

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RandomPicksDrawTest {

    private val clock = FakeClock()
    private val draw = RandomPicksDraw(clock = clock, random = Random(seed = 42))

    @Test
    fun `opening the list within the hour keeps the picks, and after it draws new ones`() {
        val first = draw.seed.value
        clock += 59.minutes
        draw.catchUp()
        assertEquals(first, draw.seed.value)
        clock += 1.minutes
        draw.catchUp()
        assertNotEquals(first, draw.seed.value)
    }

    @Test
    fun `a refresh draws at once and starts the hour again`() {
        val first = draw.seed.value
        clock += 50.minutes
        draw.reshuffle()
        val refreshed = draw.seed.value
        assertNotEquals(first, refreshed)
        clock += 30.minutes
        draw.catchUp()
        assertEquals(refreshed, draw.seed.value)
    }

    @Test
    fun `a start with no stop before it draws nothing`() {
        val first = draw.seed.value
        clock += 2.hours
        draw.onAppStarted(isSongsOnTop = true)
        assertEquals(first, draw.seed.value)
    }

    @Test
    fun `a return after seconds keeps the picks, and one after five minutes draws new ones`() {
        val first = draw.seed.value
        clock += 1.hours
        draw.onAppStopped()
        clock += 10.seconds
        draw.onAppStarted(isSongsOnTop = true)
        assertEquals(first, draw.seed.value)
        draw.onAppStopped()
        clock += 5.minutes
        draw.onAppStarted(isSongsOnTop = true)
        assertNotEquals(first, draw.seed.value)
    }

    @Test
    fun `a return is counted once`() {
        val first = draw.seed.value
        draw.onAppStopped()
        clock += 30.minutes
        draw.onAppStarted(isSongsOnTop = true)
        clock += 30.minutes
        draw.onAppStarted(isSongsOnTop = true)
        assertEquals(first, draw.seed.value)
    }

    @Test
    fun `a return to another screen draws nothing`() {
        val first = draw.seed.value
        clock += 1.hours
        draw.onAppStopped()
        clock += 10.minutes
        draw.onAppStarted(isSongsOnTop = false)
        assertEquals(first, draw.seed.value)
    }

    @Test
    fun `a clock set back during a stay away draws nothing`() {
        val first = draw.seed.value
        clock += 2.hours
        draw.onAppStopped()
        clock += (-1).hours
        draw.onAppStarted(isSongsOnTop = true)
        assertEquals(first, draw.seed.value)
    }

    private class FakeClock : Clock {
        private var now = Instant.fromEpochSeconds(1_800_000_000)

        override fun now() = now

        operator fun plusAssign(duration: Duration) {
            now += duration
        }
    }
}
