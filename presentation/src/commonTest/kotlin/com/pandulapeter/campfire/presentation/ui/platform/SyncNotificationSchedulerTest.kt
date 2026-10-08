/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SyncNotificationSchedulerTest {

    private class Harness(scope: CoroutineScope, onRelease: Harness.() -> Unit = {}) {
        var posts = 0
        var releases = 0
        var isRunGoing = false
        val scheduler: SyncNotificationScheduler = SyncNotificationScheduler(
            scope = scope,
            updateInterval = INTERVAL,
            handoverGrace = GRACE,
            postUpdate = { posts++ },
            release = {
                releases++
                onRelease()
            },
            isRunGoing = { isRunGoing },
        )
    }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `a burst of requests posts once at the end of the interval`() = runTest {
        val harness = Harness(backgroundScope)
        repeat(10) { harness.scheduler.requestUpdate(immediately = false) }
        advance(INTERVAL.inWholeMilliseconds - 1)
        assertEquals(0, harness.posts)
        advance(1)
        assertEquals(1, harness.posts)
    }

    @Test
    fun `a request after a post posts one interval later`() = runTest {
        val harness = Harness(backgroundScope)
        harness.scheduler.requestUpdate(immediately = false)
        advance(INTERVAL.inWholeMilliseconds)
        harness.scheduler.requestUpdate(immediately = false)
        advance(INTERVAL.inWholeMilliseconds - 1)
        assertEquals(1, harness.posts)
        advance(1)
        assertEquals(2, harness.posts)
    }

    @Test
    fun `an immediate request posts at once and drops the pending one`() = runTest {
        val harness = Harness(backgroundScope)
        harness.scheduler.requestUpdate(immediately = false)
        harness.scheduler.requestUpdate(immediately = true)
        assertEquals(1, harness.posts)
        advance(INTERVAL.inWholeMilliseconds * 2)
        assertEquals(1, harness.posts)
    }

    @Test
    fun `a cancelled update is not posted`() = runTest {
        val harness = Harness(backgroundScope)
        harness.scheduler.requestUpdate(immediately = false)
        advance(INTERVAL.inWholeMilliseconds / 2)
        harness.scheduler.cancelUpdate()
        advance(INTERVAL.inWholeMilliseconds * 2)
        assertEquals(0, harness.posts)
    }

    @Test
    fun `a release comes exactly at the end of the grace`() = runTest {
        val harness = Harness(backgroundScope)
        harness.scheduler.scheduleRelease()
        advance(GRACE.inWholeMilliseconds - 1)
        assertEquals(0, harness.releases)
        advance(1)
        assertEquals(1, harness.releases)
    }

    @Test
    fun `a release scheduled twice comes once`() = runTest {
        val harness = Harness(backgroundScope)
        harness.scheduler.scheduleRelease()
        advance(GRACE.inWholeMilliseconds / 2)
        harness.scheduler.scheduleRelease()
        advance(GRACE.inWholeMilliseconds * 2)
        assertEquals(1, harness.releases)
    }

    @Test
    fun `a cancelled release does not come`() = runTest {
        val harness = Harness(backgroundScope)
        harness.scheduler.scheduleRelease()
        advance(GRACE.inWholeMilliseconds / 2)
        harness.scheduler.cancelRelease()
        advance(GRACE.inWholeMilliseconds * 2)
        assertEquals(0, harness.releases)
    }

    @Test
    fun `a run started within the grace keeps it`() = runTest {
        val harness = Harness(backgroundScope)
        harness.scheduler.scheduleRelease()
        advance(GRACE.inWholeMilliseconds / 2)
        harness.isRunGoing = true
        advance(GRACE.inWholeMilliseconds * 2)
        assertEquals(0, harness.releases)
    }

    @Test
    fun `a release that cancels the grace itself is safe`() = runTest {
        val harness = Harness(backgroundScope) {
            scheduler.cancelRelease()
            scheduler.cancelUpdate()
        }
        harness.scheduler.scheduleRelease()
        advance(GRACE.inWholeMilliseconds)
        assertEquals(1, harness.releases)
        harness.scheduler.scheduleRelease()
        advance(GRACE.inWholeMilliseconds)
        assertEquals(2, harness.releases)
    }

    private companion object {
        val INTERVAL = 500.milliseconds
        val GRACE = 2.seconds
    }
}
