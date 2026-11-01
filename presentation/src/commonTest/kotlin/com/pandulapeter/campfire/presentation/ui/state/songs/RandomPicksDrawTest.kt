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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

class RandomPicksDrawTest {

    private val timeSource = TestTimeSource()
    private val draw = RandomPicksDraw(timeSource = timeSource, random = Random(seed = 42))

    @Test
    fun `opening the list within the hour keeps the picks, and after it draws new ones`() {
        val first = draw.seed.value
        timeSource += 59.minutes
        draw.catchUp()
        assertEquals(first, draw.seed.value)
        timeSource += 1.minutes
        draw.catchUp()
        assertNotEquals(first, draw.seed.value)
    }

    @Test
    fun `a refresh draws at once and starts the hour again`() {
        val first = draw.seed.value
        timeSource += 50.minutes
        draw.reshuffle()
        val refreshed = draw.seed.value
        assertNotEquals(first, refreshed)
        timeSource += 30.minutes
        draw.catchUp()
        assertEquals(refreshed, draw.seed.value)
    }
}
