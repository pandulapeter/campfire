/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FastScrollerTest {

    @Test
    fun pointerBurstsScrollToTheLatestPositionOnEachFrame() = runTest {
        val clock = BroadcastFrameClock()
        val requests = Channel<Float>(Channel.CONFLATED)
        val positions = mutableListOf<Float>()
        backgroundScope.launch(clock) { scrollOncePerFrame(requests) { positions += it } }
        requests.trySend(0.1f)
        runCurrent()
        repeat(100) { requests.trySend(it / 100f) }
        runCurrent()
        assertEquals(emptyList(), positions)
        assertTrue(clock.hasAwaiters)

        clock.sendFrame(1L)
        runCurrent()
        assertEquals(listOf(0.99f), positions)

        requests.trySend(0.5f)
        runCurrent()
        clock.sendFrame(2L)
        runCurrent()
        assertEquals(listOf(0.99f, 0.5f), positions)
        // No request is replayed when the pointer has stopped.
        clock.sendFrame(3L)
        runCurrent()
        assertEquals(listOf(0.99f, 0.5f), positions)
    }

    @Test
    fun aSlowLayoutKeepsTheFinalRequestEvenWhenThePointerReturnsToAnEarlierPosition() = runTest {
        val clock = BroadcastFrameClock()
        val requests = Channel<Float>(Channel.CONFLATED)
        val layoutFinished = CompletableDeferred<Unit>()
        val positions = mutableListOf<Float>()
        backgroundScope.launch(clock) {
            scrollOncePerFrame(requests) {
                positions += it
                if (positions.size == 1) layoutFinished.await()
            }
        }
        requests.trySend(0.1f)
        runCurrent()
        requests.trySend(0.8f)
        clock.sendFrame(1L)
        runCurrent()
        assertEquals(listOf(0.8f), positions)

        requests.trySend(0.6f)
        requests.trySend(0.1f)
        runCurrent()
        layoutFinished.complete(Unit)
        runCurrent()
        clock.sendFrame(2L)
        runCurrent()
        assertEquals(listOf(0.8f, 0.1f), positions)
    }
}
