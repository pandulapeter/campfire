/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.playing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PendingOverridesTest {

    private val stored = MutableStateFlow(SongOverrides<Int>())
    private val writes = mutableListOf<Pair<SongPlace, Int?>>()
    private var failures = 0
    private var isStoreWriting = true
    private var isFailing = false
    private var isThrowing = false

    private fun TestScope.overrides() = PendingOverrides(
        scope = backgroundScope,
        delayMillis = DELAY,
        stored = stored,
        name = "tempo",
        write = { place, value ->
            if (isThrowing) throw IllegalStateException("The disk is full.")
            writes += place to value
            if (isStoreWriting) stored.value = stored.value.with(place, value)
            !isFailing
        },
        onFailed = { failures++ },
    ).also { it.start() }

    private suspend fun PendingOverrides<Int>.current() = effective.first()

    @Test
    fun `a value is seen at once and written once after the delay`() = runTest {
        val overrides = overrides()
        overrides.set(PLACE, 100)
        runCurrent()
        assertEquals(100, overrides.current()[PLACE])
        assertEquals(emptyList<Pair<SongPlace, Int?>>(), writes)
        advanceTimeBy(DELAY + 1)
        assertEquals(listOf<Pair<SongPlace, Int?>>(PLACE to 100), writes)
    }

    @Test
    fun `two values within the delay are written once with the second`() = runTest {
        val overrides = overrides()
        overrides.set(PLACE, 100)
        advanceTimeBy(DELAY / 2)
        overrides.set(PLACE, 110)
        advanceTimeBy(DELAY + 1)
        assertEquals(listOf<Pair<SongPlace, Int?>>(PLACE to 110), writes)
        assertEquals(110, overrides.current()[PLACE])
    }

    @Test
    fun `a reset drops the waiting value and writes the removal at once`() = runTest {
        stored.value = stored.value.with(PLACE, 90)
        val overrides = overrides()
        overrides.set(PLACE, 100)
        overrides.reset(PLACE)
        runCurrent()
        assertEquals(listOf<Pair<SongPlace, Int?>>(PLACE to null), writes)
        advanceTimeBy(DELAY + 1)
        assertEquals(listOf<Pair<SongPlace, Int?>>(PLACE to null), writes)
        assertNull(overrides.current()[PLACE])
    }

    @Test
    fun `a pending value is let go of only once the store says the same`() = runTest {
        isStoreWriting = false
        val overrides = overrides()
        overrides.set(PLACE, 100)
        advanceTimeBy(DELAY + 1)
        assertEquals(100, overrides.current()[PLACE])
        stored.value = stored.value.with(PLACE, 100)
        runCurrent()
        stored.value = stored.value.with(PLACE, 120)
        runCurrent()
        assertEquals(120, overrides.current()[PLACE])
    }

    @Test
    fun `a value the store did not take is dropped and reported`() = runTest {
        isStoreWriting = false
        isFailing = true
        val overrides = overrides()
        overrides.set(PLACE, 100)
        advanceTimeBy(DELAY + 1)
        assertNull(overrides.current()[PLACE])
        assertEquals(1, failures)
    }

    @Test
    fun `a write that throws is dropped and reported`() = runTest {
        isThrowing = true
        val overrides = overrides()
        overrides.set(PLACE, 100)
        advanceTimeBy(DELAY + 1)
        assertNull(overrides.current()[PLACE])
        assertEquals(1, failures)
    }

    @Test
    fun `taking the waiting writes cancels them and returns exactly those`() = runTest {
        val other = SongPlace("b.cho", "set.setlist.json")
        val overrides = overrides()
        overrides.set(other, 80)
        advanceTimeBy(DELAY + 1)
        writes.clear()
        overrides.set(PLACE, 100)
        val waiting = overrides.takeWaiting()
        advanceTimeBy(DELAY + 1)
        assertEquals(emptyList<Pair<SongPlace, Int?>>(), writes)
        waiting()
        assertEquals(listOf<Pair<SongPlace, Int?>>(PLACE to 100), writes)
        overrides.takeWaiting()()
        assertEquals(listOf<Pair<SongPlace, Int?>>(PLACE to 100), writes)
    }

    private companion object {
        const val DELAY = 500L
        val PLACE = SongPlace("a.cho", null)
    }
}
