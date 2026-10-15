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

import com.pandulapeter.campfire.data.sync.implementation.DataSyncModule
import com.pandulapeter.campfire.data.sync.implementation.FakeLibraryFileLocalSource
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncIndexLocalSource
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncProvider
import com.pandulapeter.campfire.data.sync.implementation.FakeUserPreferencesRepository
import com.pandulapeter.campfire.data.sync.implementation.NoSetlistComparison
import com.pandulapeter.campfire.data.sync.implementation.RecordingSetlistRepository
import com.pandulapeter.campfire.data.sync.implementation.RecordingSongRepository
import com.pandulapeter.campfire.data.sync.implementation.RunCounter
import com.pandulapeter.campfire.data.sync.implementation.index.SyncIndexStore
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.SyncStateHolder
import com.pandulapeter.campfire.data.sync.implementation.preferences.SyncedPreferencesSync
import com.pandulapeter.campfire.data.sync.implementation.scheduling.SyncRunScheduler.Companion.AUTOMATIC_RUN_DELAY
import com.pandulapeter.campfire.data.sync.implementation.TEST_ACCOUNT
import com.pandulapeter.campfire.data.sync.implementation.song
import com.pandulapeter.campfire.data.sync.implementation.synchronize
import com.pandulapeter.campfire.data.sync.implementation.testEnvironment
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.source.local.api.LibraryChanges
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncProviders
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * When runs start, against a real [SyncRunner] on the in-memory fakes, on virtual time. The scheduler's scope is
 * background work of the test, so time is moved with [advanceTimeBy] and [runCurrent], never `advanceUntilIdle`.
 */
class SyncRunSchedulerTest {

    @Test
    fun `a second request moves the start of the automatic run`() = runTest {
        val world = world()

        world.scheduler.schedule()
        advanceTimeBy(AUTOMATIC_RUN_DELAY / 2)
        world.scheduler.schedule()
        advanceTimeBy(AUTOMATIC_RUN_DELAY - 100.milliseconds)
        assertEquals(0, world.runs.count)
        advanceTimeBy(200.milliseconds)

        assertEquals(1, world.runs.count)
    }

    @Test
    fun `a request during a run follows it`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val world = world(files = mapOf(song(1) to "One".encodeToByteArray()), onDownload = { gate.await() })

        world.scheduler.synchronize(SyncDeletionPolicy.ASK)
        world.stateHolder.state.first { (it as? SyncState.Connected)?.progress?.total == 1 }
        world.scheduler.schedule()
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, world.runs.count)
        advanceTimeBy(AUTOMATIC_RUN_DELAY + 100.milliseconds)

        assertEquals(2, world.runs.count)
    }

    @Test
    fun `a run asked for takes the place of the waiting automatic one`() = runTest {
        val world = world()

        world.scheduler.schedule()
        world.scheduler.synchronize(SyncDeletionPolicy.ASK)
        advanceTimeBy(20_000)

        assertEquals(1, world.runs.count)
    }

    @Test
    fun `cancelling drops the waiting automatic run`() = runTest {
        val world = world()

        world.scheduler.schedule()
        world.scheduler.cancel()
        // Not even leaving the app, which starts a waiting run at once, finds one.
        world.scheduler.startScheduled()
        advanceTimeBy(20_000)

        assertEquals(0, world.runs.count)
    }

    @Test
    fun `leaving the app with nothing waiting starts nothing`() = runTest {
        val world = world()

        val progress = world.scheduler.startScheduled()
        advanceTimeBy(20_000)

        assertNull(progress)
        assertEquals(0, world.runs.count)
    }

    private class World(val scheduler: SyncRunScheduler, val stateHolder: SyncStateHolder, val runs: RunCounter)

    private fun TestScope.world(
        files: Map<SyncKey, ByteArray> = emptyMap(),
        onDownload: suspend (SyncKey) -> Unit = {},
    ): World {
        val environment = testEnvironment()
        val runs = RunCounter()
        val syncProviders = SyncProviders(listOf(FakeSyncProvider(files = files, onDownload = onDownload, account = TEST_ACCOUNT)))
        val stateHolder = SyncStateHolder(Logger.Standard).apply {
            update { SyncState.Connected(account = TEST_ACCOUNT, progress = null, lastSyncedAt = null, lastOutcome = null) }
        }
        val userPreferencesRepository = FakeUserPreferencesRepository()
        val libraryFileLocalSource = FakeLibraryFileLocalSource()
        val syncedPreferencesSync = SyncedPreferencesSync(userPreferencesRepository, libraryFileLocalSource, Logger.Standard)
        val runner = SyncRunner(
            syncProviders = syncProviders,
            engine = DataSyncModule.syncEngine(
                libraryFileLocalSource = libraryFileLocalSource,
                libraryFileLock = LibraryFileLock(),
                setlistComparison = NoSetlistComparison,
                userPreferencesRepository = userPreferencesRepository,
                logger = Logger.Standard,
            ),
            syncedPreferencesSync = syncedPreferencesSync,
            indexStore = SyncIndexStore(FakeSyncIndexLocalSource(onSaveIndex = runs::onSaveIndex), environment),
            libraryRefresher = SyncLibraryRefresher(RecordingSongRepository(), RecordingSetlistRepository(), environment),
            stateHolder = stateHolder,
            environment = environment,
        )
        val scheduler = SyncRunScheduler(
            runner = runner,
            stateHolder = stateHolder,
            libraryChanges = LibraryChanges(),
            syncedPreferencesSync = syncedPreferencesSync,
            environment = environment,
        )
        runCurrent()
        return World(scheduler = scheduler, stateHolder = stateHolder, runs = runs)
    }
}
