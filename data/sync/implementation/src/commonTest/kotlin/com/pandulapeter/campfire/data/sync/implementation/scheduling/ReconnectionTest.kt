/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.scheduling

import com.pandulapeter.campfire.data.source.remote.api.ConnectivityMonitor
import com.pandulapeter.campfire.data.sync.implementation.FakeConnectivityMonitor
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class ReconnectionTest {

    @Test
    fun `a monitor that cannot tell waits the full length`() = runTest {
        val monitor = object : ConnectivityMonitor {
            override val reconnections = emptyFlow<Unit>()
        }
        val start = testScheduler.currentTime

        monitor.awaitReconnection(5.minutes)

        assertEquals(5.minutes.inWholeMilliseconds, testScheduler.currentTime - start)
    }

    @Test
    fun `a reconnection cuts the wait short`() = runTest {
        val monitor = FakeConnectivityMonitor()
        var isDone = false
        launch {
            monitor.awaitReconnection(5.minutes)
            isDone = true
        }
        advanceTimeBy(1.minutes)
        runCurrent()
        monitor.reconnect()
        runCurrent()

        assertEquals(true, isDone)
        assertEquals(1.minutes.inWholeMilliseconds, testScheduler.currentTime)
    }
}
