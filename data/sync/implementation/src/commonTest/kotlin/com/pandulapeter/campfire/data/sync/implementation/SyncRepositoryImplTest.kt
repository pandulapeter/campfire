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

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SyncDeletionDirection
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncFailureReason
import com.pandulapeter.campfire.data.model.domain.SyncOutcome
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.SyncRemoteStorageFullException
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The repository around the engine: what a run reports and what it leaves in the index when something other than the
 * files goes wrong, which the engine's own tests cannot show since the engine never writes the index itself.
 */
class SyncRepositoryImplTest {

    @Test
    fun `a run whose index cannot be written ends as a storage failure`() = runTest {
        val stateLocalSource = FakeSyncIndexLocalSource(onSaveIndex = { throw LibraryStorageException("Full") })
        val repository = syncRepository(provider = FakeSyncProvider(account = TEST_ACCOUNT), stateLocalSource = stateLocalSource)

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        assertEquals(SyncOutcome.Failure(SyncFailureReason.STORAGE), state.lastOutcome)
        assertNull(state.progress)
    }

    @Test
    fun `an automatic run starts straight away when the app leaves the front`() = runTest {
        val repository = syncRepository(provider = FakeSyncProvider(files = mapOf(song(1) to "One".encodeToByteArray()), account = TEST_ACCOUNT))

        repository.restore()
        repository.scheduleSynchronization()
        repository.startScheduledSynchronization()
        val state = repository.awaitOutcome()

        assertEquals(1, assertIs<SyncOutcome.Success>(state.lastOutcome).summary.downloaded)
    }

    @Test
    fun `an automatic run started as the app leaves shows as going before the call returns`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = syncRepository(
            provider = FakeSyncProvider(
                files = mapOf(song(1) to "One".encodeToByteArray()),
                onDownload = { gate.await() },
                account = TEST_ACCOUNT,
            ),
        )

        repository.restore()
        repository.scheduleSynchronization()
        val progress = repository.startScheduledSynchronization()

        assertNotNull(progress)
        assertNotNull((repository.syncState.value as SyncState.Connected).progress)
        gate.complete(Unit)
        assertIs<SyncOutcome.Success>(repository.awaitOutcome().lastOutcome)
    }

    @Test
    fun `an automatic run asked for during a run follows it at once when the app leaves`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val automaticRunStarted = CompletableDeferred<Unit>()
        val stateLocalSource = FakeSyncIndexLocalSource(
            onSaveIndex = { if (it?.let(::decodeSyncIndex)?.isAutomaticRunInProgress == true) automaticRunStarted.complete(Unit) },
        )
        val repository = syncRepository(
            provider = FakeSyncProvider(
                files = mapOf(song(1) to "One".encodeToByteArray()),
                onDownload = { gate.await() },
                account = TEST_ACCOUNT,
            ),
            stateLocalSource = stateLocalSource,
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        repository.syncState.first { (it as? SyncState.Connected)?.progress?.total == 1 }
        repository.scheduleSynchronization()
        val progress = repository.startScheduledSynchronization()
        gate.complete(Unit)

        assertEquals(1, progress?.total)
        automaticRunStarted.await()
        // Well inside the ten seconds an automatic run would otherwise wait for.
        assertTrue(testScheduler.currentTime < 10_000)
        assertIs<SyncOutcome.Success>(repository.awaitOutcome().lastOutcome)
    }

    @Test
    fun `lastSyncedAt is the clock's time`() = runTest {
        val repository = syncRepository(provider = FakeSyncProvider(account = TEST_ACCOUNT))

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        assertIs<SyncOutcome.Success>(state.lastOutcome)
        assertEquals(TEST_NOW.toEpochMilliseconds(), state.lastSyncedAt)
    }

    @Test
    fun `a run stopped before it got going shows no progress`() = runTest {
        val repository = syncRepository(provider = FakeSyncProvider(account = TEST_ACCOUNT))

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        repository.cancelSynchronization()
        val state = repository.syncState.first { (it as? SyncState.Connected)?.progress == null }

        assertNull((state as SyncState.Connected).progress)
    }

    @Test
    fun `a periodic index write that fails does not end the run`() = runTest {
        val stateLocalSource = FakeSyncIndexLocalSource(
            onSaveIndex = { text ->
                val document = text?.let(::decodeSyncIndex)
                if (document != null && document.isRunInProgress && song(1).path in document.entries) {
                    throw LibraryStorageException("Full")
                }
            },
        )
        val logger = RecordingLogger()
        val repository = syncRepository(
            provider = FakeSyncProvider(files = mapOf(song(1) to "Song".encodeToByteArray()), account = TEST_ACCOUNT),
            stateLocalSource = stateLocalSource,
            logger = logger,
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        val outcome = assertIs<SyncOutcome.Success>(state.lastOutcome)
        assertEquals(1, outcome.summary.downloaded)
        assertTrue("Could not write the sync index: Full" in logger.lines)
        val index = assertNotNull(stateLocalSource.document)
        assertTrue(song(1).path in index.entries)
        assertFalse(index.isRunInProgress)
    }

    @Test
    fun `a run reads no file before the first read of the library is done`() = runTest {
        val reads = mutableListOf<SyncKey>()
        val local = FakeLibraryFileLocalSource(files = mapOf(song(1) to "One".encodeToByteArray()), onRead = { reads += it })
        val isWaiting = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val songs = MutableStateFlow<DataState<List<Song>>>(DataState.Loading(null))
        val repository = syncRepository(
            provider = FakeSyncProvider(account = TEST_ACCOUNT),
            libraryFileLocalSource = local,
            songRepository = RecordingSongRepository(
                songs = songs,
                onLoadIfNeeded = {
                    isWaiting.complete(Unit)
                    release.await()
                    songs.value = DataState.Idle(emptyList())
                },
            ),
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        isWaiting.await()
        assertTrue(reads.isEmpty())
        release.complete(Unit)
        val state = repository.awaitOutcome()

        assertIs<SyncOutcome.Success>(state.lastOutcome)
        assertTrue(song(1) in reads)
    }

    @Test
    fun `a completed run reads again only the file it downloaded`() = runTest {
        val songRepository = RecordingSongRepository()
        val repository = syncRepository(
            provider = FakeSyncProvider(files = mapOf(song(1) to "One".encodeToByteArray()), account = TEST_ACCOUNT),
            libraryFileLocalSource = FakeLibraryFileLocalSource(files = mapOf(song(2) to "Two".encodeToByteArray())),
            songRepository = songRepository,
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        assertIs<SyncOutcome.Success>(state.lastOutcome)
        assertEquals(listOf(song(1).name), songRepository.refreshed)
        assertEquals(0, songRepository.rescanCount)
    }

    @Test
    fun `a run that fails after moving files reads them again`() = runTest {
        val local = FakeLibraryFileLocalSource(files = mapOf(song(2) to "Two".encodeToByteArray()))
        val songRepository = RecordingSongRepository()
        val repository = syncRepository(
            provider = FakeSyncProvider(
                files = mapOf(song(1) to "One".encodeToByteArray()),
                onUpload = { throw SyncNetworkException("Offline") },
                account = TEST_ACCOUNT,
            ),
            libraryFileLocalSource = local,
            songRepository = songRepository,
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        assertEquals(SyncOutcome.Failure(SyncFailureReason.NETWORK), state.lastOutcome)
        assertTrue(song(1) in local.files)
        assertTrue(song(1).name in songRepository.refreshed)
        assertEquals(0, songRepository.rescanCount)
    }

    @Test
    fun `a run stopped by the question on its second pass reads again what it brought in`() = runTest {
        val local = FakeLibraryFileLocalSource(
            files = (1..10).associate { song(it) to "Song $it".encodeToByteArray() } + (song(11) to "New".encodeToByteArray()),
        )
        val stateLocalSource = FakeSyncIndexLocalSource(
            index = SyncIndexDocument.of(
                providerId = SyncProviderId.DROPBOX.id,
                accountId = TEST_ACCOUNT.indexKey(),
                lastSyncedAt = 1,
                syncedPreferences = null,
                index = (1..10).associate {
                    song(it) to SyncIndexEntry(localHash = localContentHash("Song $it".encodeToByteArray()), remoteRevision = "r1")
                },
            ).encoded(),
        )
        val provider = FakeSyncProvider(
            files = (1..10).associate { song(it) to "Song $it".encodeToByteArray() } + (song(12) to "Incoming".encodeToByteArray()),
            account = TEST_ACCOUNT,
        )
        // The upload meets a file that appeared in the meantime, which makes the engine list again, and by then another
        // device has emptied most of the folder.
        provider.onUpload = { key ->
            if (key == song(11)) {
                (1..10).forEach { provider.files -= song(it) }
                provider.files[song(11)] = "Other".encodeToByteArray() to "r9"
            }
        }
        val songRepository = RecordingSongRepository()
        val repository = syncRepository(
            provider = provider,
            stateLocalSource = stateLocalSource,
            libraryFileLocalSource = local,
            songRepository = songRepository,
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        assertIs<SyncOutcome.DeletionsNeedConfirmation>(state.lastOutcome)
        assertTrue(song(12) in local.files)
        assertTrue(song(12).name in songRepository.refreshed)
    }

    @Test
    fun `a run that would empty the cloud folder says so in its outcome`() = runTest {
        val library = (1..10).associate { song(it) to "Song $it".encodeToByteArray() }
        val stateLocalSource = FakeSyncIndexLocalSource(
            index = SyncIndexDocument.of(
                providerId = SyncProviderId.DROPBOX.id,
                accountId = TEST_ACCOUNT.indexKey(),
                lastSyncedAt = 1,
                syncedPreferences = null,
                index = library.mapValues { (_, bytes) -> SyncIndexEntry(localHash = localContentHash(bytes), remoteRevision = "r1") },
            ).encoded(),
        )
        val provider = FakeSyncProvider(files = library, account = TEST_ACCOUNT)
        val repository = syncRepository(
            provider = provider,
            stateLocalSource = stateLocalSource,
            libraryFileLocalSource = FakeLibraryFileLocalSource(files = emptyMap()),
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        assertEquals(
            SyncOutcome.DeletionsNeedConfirmation(count = 10, total = 10, direction = SyncDeletionDirection.REMOTE),
            state.lastOutcome,
        )
        assertEquals(library.keys, provider.files.keys)
    }

    /** What the browser engine of the HTTP client throws for a request that failed, were it to get past the provider. */
    @Test
    fun `a run that ends in something other than an exception still reports and clears its marker`() = runTest {
        val stateLocalSource = FakeSyncIndexLocalSource()
        val repository = syncRepository(
            provider = FakeSyncProvider(
                files = (1..3).associate { song(it) to "Song $it".encodeToByteArray() },
                // Keyed by the file rather than counted, since the engine runs several downloads at once.
                onDownload = { if (it == song(2)) throw Error("Fail to fetch") },
                account = TEST_ACCOUNT,
            ),
            stateLocalSource = stateLocalSource,
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        assertEquals(SyncOutcome.Failure(SyncFailureReason.UNKNOWN), state.lastOutcome)
        assertNull(state.progress)
        assertFalse(assertNotNull(stateLocalSource.document).isRunInProgress)
    }

    @Test
    fun `a run in which a file failed keeps the time of the last run that was in step`() = runTest {
        val stateLocalSource = FakeSyncIndexLocalSource(index = """{"lastSyncedAt":42}""")
        val repository = syncRepository(
            provider = FakeSyncProvider(files = mapOf(song(1) to "One".encodeToByteArray()), account = TEST_ACCOUNT),
            stateLocalSource = stateLocalSource,
            libraryFileLocalSource = FakeLibraryFileLocalSource(onWrite = { throw LibraryStorageException("Full") }),
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        val outcome = assertIs<SyncOutcome.Success>(state.lastOutcome)
        assertEquals(listOf(song(1).name), outcome.summary.failed)
        assertEquals(42, state.lastSyncedAt)
        assertEquals(42, assertNotNull(stateLocalSource.document).lastSyncedAt)
    }

    @Test
    fun `a full remote folder is reported as that`() = runTest {
        val repository = syncRepository(
            provider = FakeSyncProvider(onUpload = { throw SyncRemoteStorageFullException("Full") }, account = TEST_ACCOUNT),
            libraryFileLocalSource = FakeLibraryFileLocalSource(files = mapOf(song(1) to "One".encodeToByteArray())),
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        assertEquals(SyncOutcome.Failure(SyncFailureReason.REMOTE_STORAGE_FULL), state.lastOutcome)
    }

    @Test
    fun `an automatic run marks itself as one in the index`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val stateLocalSource = FakeSyncIndexLocalSource()
        val repository = syncRepository(
            provider = FakeSyncProvider(
                files = mapOf(song(1) to "One".encodeToByteArray()),
                onDownload = { gate.await() },
                account = TEST_ACCOUNT,
            ),
            stateLocalSource = stateLocalSource,
        )

        repository.restore()
        repository.scheduleSynchronization()
        repository.startScheduledSynchronization()
        repository.syncState.first { (it as? SyncState.Connected)?.progress?.total == 1 }

        assertTrue(assertNotNull(stateLocalSource.document).isAutomaticRunInProgress)
        gate.complete(Unit)
        repository.awaitOutcome()
        assertFalse(assertNotNull(stateLocalSource.document).isRunInProgress)
        assertFalse(assertNotNull(stateLocalSource.document).isAutomaticRunInProgress)
    }

    @Test
    fun `a run somebody asked for is not marked as automatic`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val stateLocalSource = FakeSyncIndexLocalSource()
        val repository = syncRepository(
            provider = FakeSyncProvider(
                files = mapOf(song(1) to "One".encodeToByteArray()),
                onDownload = { gate.await() },
                account = TEST_ACCOUNT,
            ),
            stateLocalSource = stateLocalSource,
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        repository.syncState.first { (it as? SyncState.Connected)?.progress?.total == 1 }

        assertTrue(assertNotNull(stateLocalSource.document).isRunInProgress)
        assertFalse(assertNotNull(stateLocalSource.document).isAutomaticRunInProgress)
        gate.complete(Unit)
        repository.awaitOutcome()
    }

    @Test
    fun `a run asked for after the credentials went reports the connection as failed`() = runTest {
        val provider = FakeSyncProvider(account = TEST_ACCOUNT)
        val repository = syncRepository(provider = provider)
        repository.restore()

        provider.connected = false
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.syncState.first { it is SyncState.ConnectionFailed }

        assertEquals(SyncFailureReason.AUTHORIZATION, assertIs<SyncState.ConnectionFailed>(state).reason)
    }

    @Test
    fun `a run the service refuses reports the connection as failed and keeps the index`() = runTest {
        val stateLocalSource = FakeSyncIndexLocalSource()
        val repository = syncRepository(
            provider = FakeSyncProvider(
                files = mapOf(song(1) to "One".encodeToByteArray()),
                onDownload = { throw SyncAuthorizationException("Refused") },
                account = TEST_ACCOUNT,
            ),
            stateLocalSource = stateLocalSource,
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.syncState.first { it is SyncState.ConnectionFailed }

        assertEquals(SyncFailureReason.AUTHORIZATION, assertIs<SyncState.ConnectionFailed>(state).reason)
        assertNotNull(stateLocalSource.index)
        assertFalse(assertNotNull(stateLocalSource.document).isRunInProgress)
    }

    @Test
    fun `a run whose index cannot be read stops before anything moves`() = runTest {
        // The library is empty and the index knows the song, so the index is what says it was deleted here.
        val index = SyncIndexDocument.of(
            providerId = SyncProviderId.DROPBOX.id,
            accountId = TEST_ACCOUNT.indexKey(),
            lastSyncedAt = 1,
            syncedPreferences = null,
            index = mapOf(
                song(1) to SyncIndexEntry(localHash = localContentHash("One".encodeToByteArray()), remoteRevision = "r1"),
            ),
        ).encoded()
        val stateLocalSource = FakeSyncIndexLocalSource(index = index)
        val local = FakeLibraryFileLocalSource()
        val provider = FakeSyncProvider(files = mapOf(song(1) to "One".encodeToByteArray()), account = TEST_ACCOUNT)
        val repository = syncRepository(provider = provider, stateLocalSource = stateLocalSource, libraryFileLocalSource = local)

        repository.restore()
        stateLocalSource.onLoadIndex = { throw LibraryStorageException("Locked") }
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        assertEquals(SyncOutcome.Failure(SyncFailureReason.STORAGE), state.lastOutcome)
        assertEquals(index, stateLocalSource.index)
        assertTrue(local.files.isEmpty())
        assertTrue(song(1) in provider.files)
    }

    @Test
    fun `a run takes the folder's version of a remembered demo song`() = runTest {
        val planted = "Demo, as this version plants it".encodeToByteArray()
        val there = "Demo, as an older version planted it".encodeToByteArray()
        val libraryFileLocalSource = FakeLibraryFileLocalSource(files = mapOf(song(1) to planted))
        val userPreferencesRepository = FakeUserPreferencesRepository()
        val repository = syncRepository(
            provider = FakeSyncProvider(files = mapOf(song(1) to there), account = TEST_ACCOUNT),
            libraryFileLocalSource = libraryFileLocalSource,
            userPreferencesRepository = userPreferencesRepository,
        )

        DemoLibraryRepositoryImpl(
            libraryFileLocalSource = libraryFileLocalSource,
            libraryFileLock = LibraryFileLock(),
            userPreferencesRepository = userPreferencesRepository,
            logger = Logger.Standard,
        ).rememberDemoLibraryFiles(songFileNames = listOf(song(1).name), setlistFileNames = emptyList())
        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        assertEquals(emptyList(), assertIs<SyncOutcome.Success>(state.lastOutcome).summary.conflicts)
        assertEquals(setOf(song(1)), libraryFileLocalSource.files.keys)
        assertContentEquals(there, libraryFileLocalSource.files[song(1)])
    }
}
