/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
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
        val songRepository = RecordingSongRepository(onRefresh = { if (++refreshCount == 1) awaitCancellation() })
        val refresher = SyncLibraryRefresher(songRepository, RecordingSetlistRepository(), testEnvironment())

        refresher.onFileChanged(song("a"))
        refresher.scheduleLiveRefresh()
        runCurrent()
        assertEquals(1, refreshCount)
        assertEquals(emptyList(), songRepository.refreshed)
        refresher.refreshAfterRun()

        assertEquals(2, refreshCount)
        assertEquals(listOf("a.cho"), songRepository.refreshed)
    }

    @Test
    fun `a live refresh asked for inside the pause does nothing`() = runTest {
        val songRepository = RecordingSongRepository()
        val refresher = SyncLibraryRefresher(songRepository, RecordingSetlistRepository(), testEnvironment())

        refresher.onFileChanged(song("a"))
        refresher.scheduleLiveRefresh()
        runCurrent()
        refresher.onFileChanged(song("b"))
        refresher.scheduleLiveRefresh()
        runCurrent()
        assertEquals(listOf("a.cho"), songRepository.refreshed)
        advanceTimeBy(LIVE_RESCAN_INTERVAL.inWholeMilliseconds + 100)
        refresher.scheduleLiveRefresh()
        runCurrent()

        assertEquals(listOf("a.cho", "b.cho"), songRepository.refreshed)
    }

    private fun song(name: String) = SyncKey(kind = LibraryFileKind.SONG, name = "$name.cho")
}
