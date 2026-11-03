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

import com.pandulapeter.campfire.data.model.preferences.RandomPicksSeed
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.playing.testPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RandomPicksDrawTest {

    private val clock = FakeClock()
    private val persisted = mutableListOf<RandomPicksDraw.SavedDraw>()
    private val preferences = MutableStateFlow<UserPreferences?>(testPreferences())
    private val draw = launch(Random(seed = 42))

    @Test
    fun `a first launch draws and stores the draw`() {
        assertEquals(RandomPicksSeed(value = draw.seed.value!!, drawnAtMillis = clock.now().toEpochMilliseconds()), preferences.value?.randomPicksSeed)
    }

    @Test
    fun `a launch within the hour of the stored draw shows the same picks, and one after it draws new ones`() {
        val first = draw.seed.value
        clock += 59.minutes
        assertEquals(first, launch(Random(seed = 7)).seed.value)
        clock += 1.minutes
        val relaunched = launch(Random(seed = 7))
        assertNotEquals(first, relaunched.seed.value)
        assertEquals(relaunched.seed.value, preferences.value?.randomPicksSeed?.value)
    }

    @Test
    fun `a launch keeps the hour of the stored draw`() {
        val first = draw.seed.value
        clock += 50.minutes
        val relaunched = launch(Random(seed = 7))
        clock += 10.minutes
        relaunched.catchUp()
        assertNotEquals(first, relaunched.seed.value)
    }

    @Test
    fun `nothing is drawn before the preferences are read`() {
        preferences.value = null
        val launched = launch(Random(seed = 7))
        assertNull(launched.seed.value)
        preferences.value = testPreferences()
        assertEquals(preferences.value?.randomPicksSeed?.value, launched.seed.value)
    }

    @Test
    fun `a restored process keeps its own draw over the stored one`() {
        val first = draw.seed.value
        val saved = persisted.last()
        draw.reshuffle()
        assertEquals(first, restore(saved).seed.value)
    }

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

    @Test
    fun `a restored process keeps the picks and the hour they were drawn in`() {
        val first = draw.seed.value
        val restored = restore()
        assertEquals(first, restored.seed.value)
        clock += 59.minutes
        restored.catchUp()
        assertEquals(first, restored.seed.value)
        clock += 1.minutes
        restored.catchUp()
        assertNotEquals(first, restored.seed.value)
    }

    @Test
    fun `a restored process judges its first start as a return`() {
        val first = draw.seed.value
        clock += 1.hours
        draw.onAppStopped()
        val saved = persisted.last()
        clock += 10.seconds
        restore(saved).run {
            onAppStarted(isSongsOnTop = true)
            assertEquals(first, seed.value)
        }
        clock += 6.minutes
        restore(saved).run {
            onAppStarted(isSongsOnTop = true)
            assertNotEquals(first, seed.value)
        }
    }

    @Test
    fun `a refresh is persisted`() {
        draw.reshuffle()
        assertEquals(draw.seed.value, persisted.last().seed)
    }

    /** A draw as a restored process builds it, from what [draw] saved, with a different random to tell the two apart. */
    private fun restore(saved: RandomPicksDraw.SavedDraw = persisted.last()) =
        RandomPicksDraw(clock = clock, random = Random(seed = 7), restored = saved, persist = {}).started()

    /** A draw as a launch builds it, reading what earlier launches stored in [preferences]. */
    private fun launch(random: Random) = RandomPicksDraw(clock = clock, random = random, restored = null, persist = { persisted += it }).started()

    private fun RandomPicksDraw.started() = apply {
        start(CoroutineScope(Dispatchers.Unconfined), preferences) { change -> preferences.value = preferences.value?.change() }
    }

    private class FakeClock : Clock {
        private var now = Instant.fromEpochSeconds(1_800_000_000)

        override fun now() = now

        operator fun plusAssign(duration: Duration) {
            now += duration
        }
    }
}
