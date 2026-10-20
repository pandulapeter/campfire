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

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.source.local.api.LibraryChanges
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncProviders
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
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.SyncStateHolder
import com.pandulapeter.campfire.data.sync.implementation.TEST_ACCOUNT
import com.pandulapeter.campfire.data.sync.implementation.index.SyncIndexStore
import com.pandulapeter.campfire.data.sync.implementation.preferences.SyncedPreferencesDocument
import com.pandulapeter.campfire.data.sync.implementation.preferences.SyncedPreferencesSync
import com.pandulapeter.campfire.data.sync.implementation.scheduling.SyncRunScheduler.Companion.AUTOMATIC_RUN_DELAY
import com.pandulapeter.campfire.data.sync.implementation.song
import com.pandulapeter.campfire.data.sync.implementation.testEnvironment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * What watching the folder brings: a run for another device's change, none for this device's own, against a real
 * [SyncRunner] and [SyncRunScheduler] on the in-memory fakes and virtual time.
 */
class SyncRemoteWatcherTest {

    @Test
    fun `another device's new song is brought over`() = runTest {
        val world = world(localFiles = mapOf(song(1) to ONE))
        world.synchronizeAndWatch()

        world.provider.writeElsewhere(song(2), TWO)
        advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)

        assertEquals(2, world.runs.count)
        assertContentEquals(TWO, world.local.files[song(2)])
    }

    @Test
    fun `another device's deletion is brought over`() = runTest {
        val world = world(localFiles = mapOf(song(1) to ONE, song(2) to TWO))
        world.synchronizeAndWatch()

        world.provider.deleteElsewhere(song(2))
        advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)

        assertEquals(2, world.runs.count)
        assertEquals(setOf(song(1)), world.local.files.keys)
    }

    @Test
    fun `another device's preferences bring a run`() = runTest {
        val world = world(localFiles = mapOf(song(1) to ONE))
        world.synchronizeAndWatch()

        world.provider.writeDocumentElsewhere(
            name = SyncedPreferencesDocument.FILE_NAME,
            bytes = SyncedPreferencesDocument.encode(JsonObject(mapOf("tagColors" to JsonObject(mapOf("Rock" to JsonPrimitive(3)))))),
        )
        advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)

        assertEquals(2, world.runs.count)
    }

    @Test
    fun `another device's preferences are not lost to a failed download`() = runTest {
        val world = world(localFiles = mapOf(song(1) to ONE))
        world.synchronizeAndWatch()
        world.provider.onDownloadDocument = {
            world.provider.onDownloadDocument = {}
            throw SyncNetworkException("Offline")
        }

        world.provider.writeDocumentElsewhere(
            name = SyncedPreferencesDocument.FILE_NAME,
            bytes = SyncedPreferencesDocument.encode(JsonObject(mapOf("tagColors" to JsonObject(mapOf("Rock" to JsonPrimitive(3)))))),
        )
        advanceTimeBy(1.minutes)

        assertEquals(2, world.runs.count)
    }

    @Test
    fun `this device's own uploads bring no run`() = runTest {
        val world = world(localFiles = mapOf(song(1) to ONE, song(2) to TWO))
        world.synchronizeAndWatch()

        advanceTimeBy(1.hours)

        assertEquals(1, world.runs.count)
    }

    @Test
    fun `a change while the run is going is brought over by the run after it`() = runTest {
        val world = world(localFiles = mapOf(song(1) to ONE))
        world.provider.onUpload = { world.provider.writeElsewhere(song(2), TWO) }
        world.synchronizeAndWatch()
        world.provider.onUpload = {}

        advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)

        assertEquals(2, world.runs.count)
        assertContentEquals(TWO, world.local.files[song(2)])
    }

    @Test
    fun `an unreachable feed is asked again later`() = runTest {
        val world = world(localFiles = mapOf(song(1) to ONE))
        world.synchronizeAndWatch()
        world.provider.onChangesSince = {
            world.provider.onChangesSince = {}
            throw SyncNetworkException("Offline")
        }

        world.provider.writeElsewhere(song(2), TWO)
        advanceTimeBy(1.minutes)

        assertEquals(2, world.runs.count)
        assertContentEquals(TWO, world.local.files[song(2)])
    }

    @Test
    fun `a wait that reached the service starts the retries over`() = runTest {
        val world = world(localFiles = mapOf(song(1) to ONE))
        val offline: suspend () -> Boolean = { throw SyncNetworkException("Offline") }
        world.provider.awaitAnswers.addAll(listOf(offline, offline, { false }, offline))
        world.synchronizeAndWatch()
        world.provider.writeElsewhere(song(2), TWO)

        // Two failures wait 30 seconds and a minute; after the quiet answer, the next one waits 30 seconds again.
        advanceTimeBy(2.minutes + 5.seconds)

        assertEquals(2, world.runs.count)
    }

    @Test
    fun `the feed is asked again as soon as the device is back online`() = runTest {
        val world = world(localFiles = mapOf(song(1) to ONE))
        world.provider.awaitAnswers.add { throw SyncNetworkException("Offline") }
        world.synchronizeAndWatch()
        world.provider.writeElsewhere(song(2), TWO)
        advanceTimeBy(5.seconds)

        world.connectivity.reconnect()
        advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)

        assertEquals(2, world.runs.count)
        assertContentEquals(TWO, world.local.files[song(2)])
    }

    @Test
    fun `a cursor the service no longer takes brings a run`() = runTest {
        val world = world(localFiles = mapOf(song(1) to ONE))
        world.synchronizeAndWatch()
        world.provider.onChangesSince = { throw IllegalStateException("reset") }

        world.provider.writeElsewhere(song(2), TWO)
        advanceTimeBy(AUTOMATIC_RUN_DELAY + MARGIN)

        assertEquals(2, world.runs.count)
    }

    @Test
    fun `nothing is watched after a stopped run`() = runTest {
        val world = world(localFiles = mapOf(song(1) to ONE))
        world.synchronizeAndWatch()
        world.provider.onList = { world.scheduler.cancel() }
        world.scheduler.synchronize(SyncDeletionPolicy.ASK)
        runCurrent()
        world.provider.onList = {}
        val awaits = world.provider.awaitCount

        world.provider.writeElsewhere(song(2), TWO)
        advanceTimeBy(1.hours)

        assertEquals(2, world.runs.count)
        assertEquals(awaits, world.provider.awaitCount)
    }

    @Test
    fun `a change made while the app was away is carried by the run of its return alone`() = runTest {
        val world = world(localFiles = mapOf(song(1) to ONE))
        world.synchronizeAndWatch().cancel()
        world.provider.writeElsewhere(song(2), TWO)
        // The run is held before it takes its lock, as a run on another thread can be, while the watcher goes ahead.
        val gate = CompletableDeferred<Unit>()
        world.provider.onIsConnected = { gate.await() }

        world.scheduler.synchronizeOnReturn()
        world.scope.backgroundScope.launch { world.watcher.watch() }
        runCurrent()
        world.provider.onIsConnected = {}
        gate.complete(Unit)
        advanceTimeBy(1.hours)

        assertEquals(2, world.runs.count)
        assertContentEquals(TWO, world.local.files[song(2)])
    }

    @Test
    fun `nothing is watched once the watching is cancelled`() = runTest {
        val world = world(localFiles = mapOf(song(1) to ONE))
        world.synchronizeAndWatch().cancel()

        world.provider.writeElsewhere(song(2), TWO)
        advanceTimeBy(1.hours)

        assertEquals(1, world.runs.count)
    }

    private class World(
        val scope: TestScope,
        val scheduler: SyncRunScheduler,
        val watcher: SyncRemoteWatcher,
        val runs: RunCounter,
        val provider: FakeSyncProvider,
        val local: FakeLibraryFileLocalSource,
        val connectivity: FakeConnectivityMonitor,
    ) {

        /** A first run, then the watching the app does while in front, for as long as the test lasts. */
        fun synchronizeAndWatch(): Job {
            scheduler.synchronize(SyncDeletionPolicy.ASK)
            scope.advanceTimeBy(MARGIN)
            return scope.backgroundScope.launch { watcher.watch() }.also { scope.runCurrent() }
        }
    }

    private fun TestScope.world(localFiles: Map<SyncKey, ByteArray>): World {
        val environment = testEnvironment()
        val connectivityMonitor = FakeConnectivityMonitor()
        val runs = RunCounter()
        val provider = FakeSyncProvider(account = TEST_ACCOUNT)
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
            connectivityMonitor = connectivityMonitor,
            environment = environment,
        )
        runCurrent()
        return World(
            scope = this,
            scheduler = scheduler,
            watcher = SyncRemoteWatcher(runner = runner, scheduler = scheduler, connectivityMonitor = connectivityMonitor, environment = environment),
            runs = runs,
            provider = provider,
            local = libraryFileLocalSource,
            connectivity = connectivityMonitor,
        )
    }

    private companion object {
        val ONE = "One".encodeToByteArray()
        val TWO = "Two".encodeToByteArray()

        /** Room for a run's own steps, well inside the shortest delay. */
        val MARGIN = 1.seconds
    }
}
