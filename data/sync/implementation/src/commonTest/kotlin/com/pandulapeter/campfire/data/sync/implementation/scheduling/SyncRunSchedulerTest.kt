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
import com.pandulapeter.campfire.data.sync.implementation.FakeConnectivityMonitor
import com.pandulapeter.campfire.data.sync.implementation.FakeLibraryFileLocalSource
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncIndexLocalSource
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncProvider
import com.pandulapeter.campfire.data.sync.implementation.FakeUserPreferencesRepository
import com.pandulapeter.campfire.data.sync.implementation.NoSetlistComparison
import com.pandulapeter.campfire.data.sync.implementation.RecordingSetlistRepository
import com.pandulapeter.campfire.data.sync.implementation.RecordingSongRepository
import com.pandulapeter.campfire.data.sync.implementation.RunCounter
import com.pandulapeter.campfire.data.sync.implementation.index.SyncIndexDocument
import com.pandulapeter.campfire.data.sync.implementation.index.SyncIndexStore
import com.pandulapeter.campfire.data.sync.implementation.index.indexKey
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.encoded
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.SyncStateHolder
import com.pandulapeter.campfire.data.sync.implementation.preferences.SyncedPreferencesSync
import com.pandulapeter.campfire.data.sync.implementation.scheduling.SyncRunScheduler.Companion.AUTOMATIC_RUN_DELAY
import com.pandulapeter.campfire.data.sync.implementation.shared.sharedCopySync
import com.pandulapeter.campfire.data.sync.implementation.scheduling.SyncRunScheduler.Companion.NETWORK_RETRY_DELAYS
import com.pandulapeter.campfire.data.sync.implementation.TEST_ACCOUNT
import com.pandulapeter.campfire.data.sync.implementation.song
import com.pandulapeter.campfire.data.sync.implementation.synchronize
import com.pandulapeter.campfire.data.sync.implementation.testEnvironment
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.sync.SyncFailureReason
import com.pandulapeter.campfire.data.model.sync.SyncOutcome
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.source.local.api.LibraryChanges
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncProviders
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncRemoteStorageFullException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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

    @Test
    fun `a run the network failed is tried again`() = runTest {
        val world = world()
        world.provider.failListingOnce()

        world.scheduler.schedule()
        advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)
        assertEquals(1, world.runs.count)
        assertEquals(SyncOutcome.Failure(SyncFailureReason.NETWORK), world.lastOutcome)
        advanceTimeBy(NETWORK_RETRY_DELAYS[0] - MARGIN * 2)
        assertEquals(1, world.runs.count)
        advanceTimeBy(MARGIN * 2)

        assertEquals(2, world.runs.count)
        assertIs<SyncOutcome.Success>(world.lastOutcome)
    }

    @Test
    fun `the wait grows with each failure and starts over after a run that worked`() = runTest {
        val world = world()
        world.provider.onList = { throw SyncNetworkException("Offline") }

        world.scheduler.schedule()
        advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)
        advanceTimeBy(NETWORK_RETRY_DELAYS[0])
        assertEquals(2, world.runs.count)
        world.provider.onList = {}
        advanceTimeBy(NETWORK_RETRY_DELAYS[1] - MARGIN * 2)
        assertEquals(2, world.runs.count)
        advanceTimeBy(MARGIN * 2)
        assertEquals(3, world.runs.count)
        assertIs<SyncOutcome.Success>(world.lastOutcome)
        world.provider.failListingOnce()
        world.scheduler.schedule()
        advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)
        assertEquals(4, world.runs.count)
        advanceTimeBy(NETWORK_RETRY_DELAYS[0] - MARGIN * 2)
        assertEquals(4, world.runs.count)
        advanceTimeBy(MARGIN * 2)

        assertEquals(5, world.runs.count)
    }

    @Test
    fun `a failure that is not the network's is not tried again`() = runTest {
        val unknown = world()
        unknown.provider.onList = { throw IllegalStateException("Broken") }
        val storageFull = world(localFiles = mapOf(song(1) to "One".encodeToByteArray()))
        storageFull.provider.onUpload = { throw SyncRemoteStorageFullException("Full") }

        unknown.scheduler.schedule()
        storageFull.scheduler.schedule()
        advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)
        assertEquals(SyncOutcome.Failure(SyncFailureReason.UNKNOWN), unknown.lastOutcome)
        assertEquals(SyncOutcome.Failure(SyncFailureReason.REMOTE_STORAGE_FULL), storageFull.lastOutcome)
        advanceTimeBy(6.hours)

        assertEquals(1, unknown.runs.count)
        assertEquals(1, storageFull.runs.count)
    }

    @Test
    fun `a run stopped while the network fails it is not tried again`() = runTest {
        val stopped = world()
        stopped.provider.failListingWhenStopped()
        val disconnected = world()
        disconnected.provider.failListingWhenStopped()

        stopped.scheduler.synchronize(SyncDeletionPolicy.ASK)
        disconnected.scheduler.synchronize(SyncDeletionPolicy.ASK)
        runCurrent()
        stopped.scheduler.cancel()
        disconnected.scheduler.stopForConnectionChange()
        advanceTimeBy(NETWORK_RETRY_DELAYS.last() + MARGIN)

        assertEquals(1, stopped.runs.count)
        assertEquals(1, disconnected.runs.count)
    }

    @Test
    fun `a change made while a retry waits starts after the usual delay`() = runTest {
        val world = world()
        world.provider.failListingOnce()
        world.scheduler.schedule()
        advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)

        world.scheduler.schedule()
        advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)

        assertEquals(2, world.runs.count)
    }

    @Test
    fun `Stop drops a waiting retry`() = runTest {
        val world = world()
        world.provider.failListingOnce()
        world.scheduler.schedule()
        advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)

        world.scheduler.cancel()
        advanceTimeBy(NETWORK_RETRY_DELAYS.last() + MARGIN)

        assertEquals(1, world.runs.count)
    }

    @Test
    fun `leaving the app with only a retry waiting starts nothing`() = runTest {
        val world = world()
        world.provider.failListingOnce()
        world.scheduler.schedule()
        advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)

        val progress = world.scheduler.startScheduled()
        advanceTimeBy(NETWORK_RETRY_DELAYS[0] - MARGIN * 2)

        assertNull(progress)
        assertEquals(1, world.runs.count)
    }

    @Test
    fun `returning to the app starts a run that carries the waiting one`() = runTest {
        val world = world()

        world.scheduler.schedule()
        world.scheduler.synchronizeOnReturn()
        advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)

        assertEquals(1, world.runs.count)
    }

    @Test
    fun `returning to the app after a stopped run starts nothing`() = runTest {
        val world = world()
        world.provider.onList = { world.scheduler.cancel() }
        world.scheduler.synchronize(SyncDeletionPolicy.ASK)
        runCurrent()
        world.provider.onList = {}

        world.scheduler.synchronizeOnReturn()
        advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)

        assertEquals(SyncOutcome.Interrupted, world.lastOutcome)
        assertEquals(1, world.runs.count)
    }

    @Test
    fun `returning to the app leaves an open deletion question alone`() = runTest {
        val library = (1..10).associate { song(it) to "Song $it".encodeToByteArray() }
        val world = world(
            files = library,
            index = SyncIndexDocument.of(
                providerId = SyncProviderId.DROPBOX.id,
                accountId = TEST_ACCOUNT.indexKey(),
                lastSyncedAt = 1,
                syncedPreferences = null,
                index = library.mapValues { (_, bytes) -> SyncIndexEntry(localHash = localContentHash(bytes), remoteRevision = "r1") },
            ).encoded(),
        )
        world.scheduler.synchronize(SyncDeletionPolicy.ASK)
        runCurrent()
        val question = assertIs<SyncOutcome.DeletionsNeedConfirmation>(world.lastOutcome)
        val runs = world.runs.count

        world.scheduler.synchronizeOnReturn()
        advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)

        assertEquals(question, world.lastOutcome)
        assertEquals(runs, world.runs.count)
    }

    @Test
    fun `a run the network failed is tried again as soon as the device is back online`() = runTest {
        val world = world()
        world.provider.failListingOnce()
        world.scheduler.schedule()
        advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)

        world.connectivity.reconnect()
        advanceTimeBy(MARGIN)

        assertEquals(2, world.runs.count)
        assertIs<SyncOutcome.Success>(world.lastOutcome)
    }

    private fun FakeSyncProvider.failListingOnce() {
        onList = {
            onList = {}
            throw SyncNetworkException("Offline")
        }
    }

    /** Waits for the run to be stopped, then fails for the network in place of the cancellation, as a provider can. */
    private fun FakeSyncProvider.failListingWhenStopped() {
        onList = {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                throw SyncNetworkException("Offline")
            }
        }
    }

    private class World(
        val scheduler: SyncRunScheduler,
        val stateHolder: SyncStateHolder,
        val runs: RunCounter,
        val provider: FakeSyncProvider,
        val connectivity: FakeConnectivityMonitor,
    ) {
        val lastOutcome get() = (stateHolder.value as? SyncState.Connected)?.lastOutcome
    }

    private fun TestScope.world(
        files: Map<SyncKey, ByteArray> = emptyMap(),
        onDownload: suspend (SyncKey) -> Unit = {},
        localFiles: Map<SyncKey, ByteArray> = emptyMap(),
        index: String? = null,
    ): World {
        val environment = testEnvironment()
        val connectivityMonitor = FakeConnectivityMonitor()
        val runs = RunCounter()
        val provider = FakeSyncProvider(files = files, onDownload = onDownload, account = TEST_ACCOUNT)
        val syncProviders = SyncProviders(listOf(provider))
        val stateHolder = SyncStateHolder(Logger.Standard).apply {
            update { SyncState.Connected(account = TEST_ACCOUNT, progress = null, lastSyncedAt = null, lastOutcome = null) }
        }
        val userPreferencesRepository = FakeUserPreferencesRepository()
        val libraryFileLocalSource = FakeLibraryFileLocalSource(files = localFiles)
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
            sharedCopySync = sharedCopySync(library = libraryFileLocalSource, environment = environment),
            indexStore = SyncIndexStore(FakeSyncIndexLocalSource(index = index, onSaveIndex = runs::onSaveIndex), environment),
            libraryRefresher = SyncLibraryRefresher(RecordingSongRepository(), RecordingSetlistRepository(), environment),
            stateHolder = stateHolder,
            environment = environment,
        )
        val scheduler = SyncRunScheduler(
            runner = runner,
            stateHolder = stateHolder,
            libraryChanges = LibraryChanges(),
            syncedPreferencesSync = syncedPreferencesSync,
            connectivityMonitor = connectivityMonitor,
            environment = environment,
        )
        runCurrent()
        return World(scheduler = scheduler, stateHolder = stateHolder, runs = runs, provider = provider, connectivity = connectivityMonitor)
    }

    private companion object {
        /** Room on either side of a due time for a run's own steps, well inside the shortest delay. */
        val MARGIN = 1.seconds
    }
}
