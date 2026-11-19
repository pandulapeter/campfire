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

import com.pandulapeter.campfire.data.sync.implementation.run.OtherAppLibrary.note
import com.pandulapeter.campfire.data.sync.implementation.testEnvironment
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncLibraryRefresherTest {

    @Test
    fun `the refresh a run ends with stops a live one and reads what it put back`() = runTest {
        var refreshCount = 0
        val observer = RecordingLibraryObserver(onRefresh = { if (++refreshCount == 1) awaitCancellation() })
        val refresher = SyncLibraryRefresher(observer, testEnvironment())

        refresher.onFileChanged(note("a"))
        refresher.scheduleLiveRefresh()
        runCurrent()
        assertEquals(1, refreshCount)
        assertEquals(emptyList(), observer.refreshed)
        refresher.refreshAfterRun()

        assertEquals(2, refreshCount)
        assertEquals(listOf(note("a")), observer.refreshed)
    }

    @Test
    fun `a live refresh asked for inside the pause waits for its end`() = runTest {
        val observer = RecordingLibraryObserver()
        val refresher = SyncLibraryRefresher(observer, testEnvironment())

        refresher.onFileChanged(note("a"))
        refresher.scheduleLiveRefresh()
        runCurrent()
        refresher.onFileChanged(note("b"))
        refresher.scheduleLiveRefresh()
        runCurrent()
        assertEquals(listOf(note("a")), observer.refreshed)
        advanceTimeBy(LIVE_RESCAN_INTERVAL.inWholeMilliseconds + 100)

        assertEquals(listOf(note("a"), note("b")), observer.refreshed)
    }

    @Test
    fun `a live refresh with nothing to read starts no pause`() = runTest {
        val observer = RecordingLibraryObserver()
        val refresher = SyncLibraryRefresher(observer, testEnvironment())

        refresher.scheduleLiveRefresh()
        runCurrent()
        refresher.onFileChanged(note("a"))
        refresher.scheduleLiveRefresh()
        runCurrent()

        assertEquals(listOf(note("a")), observer.refreshed)
    }

    @Test
    fun `a file changed again before a refresh is read once`() = runTest {
        val observer = RecordingLibraryObserver()
        val refresher = SyncLibraryRefresher(observer, testEnvironment())

        refresher.onFileChanged(note("a"))
        refresher.onFileChanged(note("a"))
        refresher.refreshAfterRun()
        refresher.refreshAfterRun()

        assertEquals(listOf(note("a")), observer.refreshed)
    }

    @Test
    fun `a rescan reaches the observer whole`() = runTest {
        val observer = RecordingLibraryObserver()
        val refresher = SyncLibraryRefresher(observer, testEnvironment())

        refresher.onFileChanged(note("a"))
        refresher.rescanLibrary()

        assertEquals(1, observer.rescanCount)
        assertEquals(emptyList(), observer.refreshed)
    }
}
