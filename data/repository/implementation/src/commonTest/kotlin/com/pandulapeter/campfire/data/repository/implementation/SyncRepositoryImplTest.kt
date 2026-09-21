/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.model.domain.SyncAccount
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncFailureReason
import com.pandulapeter.campfire.data.model.domain.SyncOutcome
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.repository.implementation.sync.FakeLibraryFileLocalSource
import com.pandulapeter.campfire.data.repository.implementation.sync.FakePendingAuthorizationStore
import com.pandulapeter.campfire.data.repository.implementation.sync.FakeSyncAuthenticator
import com.pandulapeter.campfire.data.repository.implementation.sync.FakeSyncProvider
import com.pandulapeter.campfire.data.repository.implementation.sync.FakeSyncStateLocalSource
import com.pandulapeter.campfire.data.repository.implementation.sync.RecordingSetlistRepository
import com.pandulapeter.campfire.data.repository.implementation.sync.RecordingSongRepository
import com.pandulapeter.campfire.data.repository.implementation.sync.SyncKey
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.SyncProviders
import com.pandulapeter.campfire.data.source.remote.api.SyncRemoteStorageFullException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The repository around the engine: what a run reports and what it leaves in the index when something other than the
 * files goes wrong, which the engine's own tests cannot show since the engine never writes the index itself.
 */
class SyncRepositoryImplTest {

    @Test
    fun `a run whose index cannot be written ends as a storage failure`() = runTest {
        val stateLocalSource = FakeSyncStateLocalSource(onSaveIndex = { throw LibraryStorageException("Full") })
        val repository = repository(provider = FakeSyncProvider(account = ACCOUNT), stateLocalSource = stateLocalSource)

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        assertEquals(SyncOutcome.Failure(SyncFailureReason.STORAGE), state.lastOutcome)
        assertNull(state.progress)
    }

    @Test
    fun `a periodic index write that fails does not end the run`() = runTest {
        val stateLocalSource = FakeSyncStateLocalSource(
            onSaveIndex = { document ->
                if (document != null && "\"isRunInProgress\": true" in document && song(1).name in document) {
                    throw LibraryStorageException("Full")
                }
            },
        )
        val repository = repository(
            provider = FakeSyncProvider(files = mapOf(song(1) to "Song".encodeToByteArray()), account = ACCOUNT),
            stateLocalSource = stateLocalSource,
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        val outcome = assertIs<SyncOutcome.Success>(state.lastOutcome)
        assertEquals(1, outcome.summary.downloaded)
        val index = stateLocalSource.index.orEmpty()
        assertTrue(song(1).name in index)
        assertFalse("\"isRunInProgress\": true" in index)
    }

    @Test
    fun `a run that fails after moving files reads the library again`() = runTest {
        val local = FakeLibraryFileLocalSource(files = mapOf(song(2) to "Two".encodeToByteArray()))
        val snapshots = mutableListOf<Set<SyncKey>>()
        val repository = repository(
            provider = FakeSyncProvider(
                files = mapOf(song(1) to "One".encodeToByteArray()),
                onUpload = { throw SyncNetworkException("Offline") },
                account = ACCOUNT,
            ),
            libraryFileLocalSource = local,
            songRepository = RecordingSongRepository(onRescan = { snapshots += local.files.keys.toSet() }),
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        assertEquals(SyncOutcome.Failure(SyncFailureReason.NETWORK), state.lastOutcome)
        assertTrue(song(1) in snapshots.last())
    }

    @Test
    fun `a run in which a file failed keeps the time of the last run that was in step`() = runTest {
        val stateLocalSource = FakeSyncStateLocalSource(index = """{"lastSyncedAt":42}""")
        val repository = repository(
            provider = FakeSyncProvider(files = mapOf(song(1) to "One".encodeToByteArray()), account = ACCOUNT),
            stateLocalSource = stateLocalSource,
            libraryFileLocalSource = FakeLibraryFileLocalSource(onWrite = { throw LibraryStorageException("Full") }),
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        val outcome = assertIs<SyncOutcome.Success>(state.lastOutcome)
        assertEquals(listOf(song(1).name), outcome.summary.failed)
        assertEquals(42, state.lastSyncedAt)
        assertTrue("\"lastSyncedAt\": 42" in stateLocalSource.index.orEmpty())
    }

    @Test
    fun `a full remote folder is reported as that`() = runTest {
        val repository = repository(
            provider = FakeSyncProvider(onUpload = { throw SyncRemoteStorageFullException("Full") }, account = ACCOUNT),
            libraryFileLocalSource = FakeLibraryFileLocalSource(files = mapOf(song(1) to "One".encodeToByteArray())),
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        assertEquals(SyncOutcome.Failure(SyncFailureReason.REMOTE_STORAGE_FULL), state.lastOutcome)
    }

    private fun repository(
        provider: FakeSyncProvider,
        stateLocalSource: FakeSyncStateLocalSource = FakeSyncStateLocalSource(),
        libraryFileLocalSource: FakeLibraryFileLocalSource = FakeLibraryFileLocalSource(),
        songRepository: RecordingSongRepository = RecordingSongRepository(),
        setlistRepository: RecordingSetlistRepository = RecordingSetlistRepository(),
    ) = SyncRepositoryImpl(
        syncProviders = SyncProviders(listOf(provider)),
        authenticator = FakeSyncAuthenticator(),
        pendingAuthorizationStore = FakePendingAuthorizationStore(),
        syncStateLocalSource = stateLocalSource,
        songRepository = songRepository,
        setlistRepository = setlistRepository,
        libraryFileLocalSource = libraryFileLocalSource,
    )

    /**
     * The run is on the repository's own dispatcher rather than the test's, so it is waited for through the state it
     * reports; a run that never reports is failed by `runTest`'s own timeout.
     */
    private suspend fun SyncRepositoryImpl.awaitOutcome() = syncState.first {
        it is SyncState.Connected && !it.isSyncing && it.lastOutcome != null
    } as SyncState.Connected

    private companion object {
        val ACCOUNT = SyncAccount(providerId = SyncProviderId.DROPBOX, displayName = "Someone", email = "someone@example.com")

        fun song(number: Int) = SyncKey(kind = LibraryFileKind.SONG, name = "song_$number.cho")
    }
}
