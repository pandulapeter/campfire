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
import com.pandulapeter.campfire.data.model.domain.SyncDeletionDirection
import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncFailureReason
import com.pandulapeter.campfire.data.model.domain.SyncOutcome
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.repository.api.SyncRepository
import com.pandulapeter.campfire.data.repository.implementation.sync.FakeLibraryFileLocalSource
import com.pandulapeter.campfire.data.repository.implementation.sync.FakePendingAuthorizationStore
import com.pandulapeter.campfire.data.repository.implementation.sync.FakeSyncAuthenticator
import com.pandulapeter.campfire.data.repository.implementation.sync.FakeSyncProvider
import com.pandulapeter.campfire.data.repository.implementation.sync.FakeSyncStateLocalSource
import com.pandulapeter.campfire.data.repository.implementation.sync.RecordingSetlistRepository
import com.pandulapeter.campfire.data.repository.implementation.sync.RecordingSongRepository
import com.pandulapeter.campfire.data.repository.implementation.sync.SyncIndexDocument
import com.pandulapeter.campfire.data.repository.implementation.sync.SyncIndexEntry
import com.pandulapeter.campfire.data.repository.implementation.sync.SyncKey
import com.pandulapeter.campfire.data.repository.implementation.sync.indexKey
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.remote.api.PendingAuthorization
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthenticator
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.SyncProviders
import com.pandulapeter.campfire.data.source.remote.api.SyncRemoteStorageFullException
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

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
    fun `a run stopped by the question on its second pass reads the library again`() = runTest {
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
        val local = FakeLibraryFileLocalSource(
            files = (1..10).associate { song(it) to "Song $it".encodeToByteArray() } + (song(11) to "New".encodeToByteArray()),
        )
        val stateLocalSource = FakeSyncStateLocalSource(
            index = json.encodeToString(
                SyncIndexDocument.of(
                    providerId = SyncProviderId.DROPBOX.id,
                    accountId = ACCOUNT.indexKey(),
                    lastSyncedAt = 1,
                    index = (1..10).associate {
                        song(it) to SyncIndexEntry(localHash = localContentHash("Song $it".encodeToByteArray()), remoteRevision = "r1")
                    },
                ),
            ),
        )
        val provider = FakeSyncProvider(
            files = (1..10).associate { song(it) to "Song $it".encodeToByteArray() } + (song(12) to "Incoming".encodeToByteArray()),
            account = ACCOUNT,
        )
        // The upload meets a file that appeared in the meantime, which makes the engine list again, and by then another
        // device has emptied most of the folder.
        provider.onUpload = { key ->
            if (key == song(11)) {
                (1..10).forEach { provider.files -= song(it) }
                provider.files[song(11)] = "Other".encodeToByteArray() to "r9"
            }
        }
        val snapshots = mutableListOf<Set<SyncKey>>()
        val repository = repository(
            provider = provider,
            stateLocalSource = stateLocalSource,
            libraryFileLocalSource = local,
            songRepository = RecordingSongRepository(onRescan = { snapshots += local.files.keys.toSet() }),
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        assertIs<SyncOutcome.DeletionsNeedConfirmation>(state.lastOutcome)
        assertTrue(song(12) in snapshots.last())
    }

    @Test
    fun `a run that would empty the cloud folder says so in its outcome`() = runTest {
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
        val library = (1..10).associate { song(it) to "Song $it".encodeToByteArray() }
        val stateLocalSource = FakeSyncStateLocalSource(
            index = json.encodeToString(
                SyncIndexDocument.of(
                    providerId = SyncProviderId.DROPBOX.id,
                    accountId = ACCOUNT.indexKey(),
                    lastSyncedAt = 1,
                    index = library.mapValues { (_, bytes) -> SyncIndexEntry(localHash = localContentHash(bytes), remoteRevision = "r1") },
                ),
            ),
        )
        val provider = FakeSyncProvider(files = library, account = ACCOUNT)
        val repository = repository(
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
        val stateLocalSource = FakeSyncStateLocalSource()
        val repository = repository(
            provider = FakeSyncProvider(
                files = (1..3).associate { song(it) to "Song $it".encodeToByteArray() },
                // Keyed by the file rather than counted, since the engine runs several downloads at once.
                onDownload = { if (it == song(2)) throw Error("Fail to fetch") },
                account = ACCOUNT,
            ),
            stateLocalSource = stateLocalSource,
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.awaitOutcome()

        assertEquals(SyncOutcome.Failure(SyncFailureReason.UNKNOWN), state.lastOutcome)
        assertNull(state.progress)
        assertFalse("\"isRunInProgress\": true" in stateLocalSource.index.orEmpty())
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

    @Test
    fun `restoring again while a run is going leaves the run alone`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val stateLocalSource = FakeSyncStateLocalSource()
        val repository = repository(
            provider = FakeSyncProvider(
                files = mapOf(song(1) to "One".encodeToByteArray()),
                onDownload = { gate.await() },
                account = ACCOUNT,
            ),
            stateLocalSource = stateLocalSource,
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        repository.syncState.first { (it as? SyncState.Connected)?.progress?.total == 1 }
        val result = repository.restore()

        assertTrue(result.isConnected)
        assertFalse(result.wasInterrupted)
        val state = assertIs<SyncState.Connected>(repository.syncState.value)
        assertTrue(state.isSyncing)
        assertNull(state.lastOutcome)
        assertTrue("\"isRunInProgress\": true" in stateLocalSource.index.orEmpty())
        gate.complete(Unit)
        assertIs<SyncOutcome.Success>(repository.awaitOutcome().lastOutcome)
    }

    @Test
    fun `restoring again after an interrupted run still reports it`() = runTest {
        val repository = repository(
            provider = FakeSyncProvider(account = ACCOUNT),
            stateLocalSource = FakeSyncStateLocalSource(index = """{"isRunInProgress":true}"""),
        )

        assertTrue(repository.restore().wasInterrupted)
        assertTrue(repository.restore().wasInterrupted)
        assertEquals(SyncOutcome.Interrupted, (repository.syncState.value as SyncState.Connected).lastOutcome)
    }

    @Test
    fun `a disconnect that is cancelled after the credentials went still ends disconnected`() = runTest {
        val stateLocalSource = FakeSyncStateLocalSource(index = "{}")
        val provider = FakeSyncProvider(account = ACCOUNT).apply { onDisconnect = { delay(1_000) } }
        val repository = repository(provider = provider, stateLocalSource = stateLocalSource)
        repository.restore()

        val job = launch { repository.disconnect() }
        runCurrent()
        assertFalse(provider.connected)
        job.cancel()
        advanceUntilIdle()

        assertEquals(SyncState.Disconnected, repository.syncState.first { it == SyncState.Disconnected })
        assertNull(stateLocalSource.index)
    }

    @Test
    fun `a run asked for after the credentials went reports the connection as failed`() = runTest {
        val provider = FakeSyncProvider(account = ACCOUNT)
        val repository = repository(provider = provider)
        repository.restore()

        provider.connected = false
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.syncState.first { it is SyncState.ConnectionFailed }

        assertEquals(SyncFailureReason.AUTHORIZATION, assertIs<SyncState.ConnectionFailed>(state).reason)
    }

    @Test
    fun `a run the service refuses reports the connection as failed and keeps the index`() = runTest {
        val stateLocalSource = FakeSyncStateLocalSource()
        val repository = repository(
            provider = FakeSyncProvider(
                files = mapOf(song(1) to "One".encodeToByteArray()),
                onDownload = { throw SyncAuthorizationException("Refused") },
                account = ACCOUNT,
            ),
            stateLocalSource = stateLocalSource,
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        val state = repository.syncState.first { it is SyncState.ConnectionFailed }

        assertEquals(SyncFailureReason.AUTHORIZATION, assertIs<SyncState.ConnectionFailed>(state).reason)
        assertNotNull(stateLocalSource.index)
        assertFalse("\"isRunInProgress\": true" in stateLocalSource.index.orEmpty())
    }

    @Test
    fun `a run whose index cannot be read stops before anything moves`() = runTest {
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
        // The library is empty and the index knows the song, so the index is what says it was deleted here.
        val index = json.encodeToString(
            SyncIndexDocument.of(
                providerId = SyncProviderId.DROPBOX.id,
                accountId = ACCOUNT.indexKey(),
                lastSyncedAt = 1,
                index = mapOf(
                    song(1) to SyncIndexEntry(localHash = localContentHash("One".encodeToByteArray()), remoteRevision = "r1"),
                ),
            ),
        )
        val stateLocalSource = FakeSyncStateLocalSource(index = index)
        val local = FakeLibraryFileLocalSource()
        val provider = FakeSyncProvider(files = mapOf(song(1) to "One".encodeToByteArray()), account = ACCOUNT)
        val repository = repository(provider = provider, stateLocalSource = stateLocalSource, libraryFileLocalSource = local)

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
    fun `restoring with an unreadable index still shows the account`() = runTest {
        val repository = repository(
            provider = FakeSyncProvider(account = ACCOUNT),
            stateLocalSource = FakeSyncStateLocalSource(onLoadIndex = { throw LibraryStorageException("Locked") }),
        )

        val result = repository.restore()

        assertTrue(result.isConnected)
        assertIs<SyncState.Connected>(repository.syncState.value)
    }

    @Test
    fun `restoring again with nothing going starts from the state`() = runTest {
        val repository = repository(
            provider = FakeSyncProvider(files = mapOf(song(1) to "One".encodeToByteArray()), account = ACCOUNT),
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        repository.awaitOutcome()
        val result = repository.restore()

        assertTrue(result.isConnected)
        assertFalse(result.wasInterrupted)
        assertIs<SyncOutcome.Success>((repository.syncState.value as SyncState.Connected).lastOutcome)
    }

    @Test
    fun `disconnecting right after stopping a run leaves no index behind`() = runTest {
        val stateLocalSource = FakeSyncStateLocalSource()
        val repository = repository(
            provider = FakeSyncProvider(
                files = mapOf(song(1) to "One".encodeToByteArray()),
                onDownload = { CompletableDeferred<Unit>().await() },
                account = ACCOUNT,
            ),
            stateLocalSource = stateLocalSource,
        )

        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        repository.syncState.first { (it as? SyncState.Connected)?.progress?.total == 1 }
        repository.cancelSynchronization()
        repository.disconnect()

        assertNull(stateLocalSource.index)
        assertEquals(SyncState.Disconnected, repository.syncState.value)
    }

    @Test
    fun `forgetting the connection leaves nothing to restore`() = runTest {
        val stateLocalSource = FakeSyncStateLocalSource(index = "{}")
        val provider = FakeSyncProvider(account = ACCOUNT)
        val repository = repository(provider = provider, stateLocalSource = stateLocalSource)

        repository.forgetStoredConnection()
        val result = repository.restore()

        assertEquals(
            SyncRepository.RestoreResult(isConnected = false, didReturnFromAuthorization = false, wasInterrupted = false),
            result,
        )
        assertEquals(SyncState.Disconnected, repository.syncState.value)
        assertNull(stateLocalSource.index)
    }

    @Test
    fun `forgetting the connection tells the service nothing`() = runTest {
        val provider = FakeSyncProvider(account = ACCOUNT).apply { onDisconnect = { fail("The service was told.") } }
        val repository = repository(provider = provider)

        repository.forgetStoredConnection()

        assertTrue(provider.hasForgottenCredentials)
        assertFalse(provider.connected)
    }

    @Test
    fun `forgetting the connection drops an unfinished authorization`() = runTest {
        val store = FakePendingAuthorizationStore().apply {
            pending = PendingAuthorization(SyncProviderId.DROPBOX, state = "state", verifier = "verifier", redirectUri = null)
        }
        val repository = repository(provider = FakeSyncProvider(), pendingAuthorizationStore = store)

        repository.forgetStoredConnection()

        assertNull(store.loadPendingAuthorization())
    }

    @Test
    fun `an ordinary launch does not forget anything`() = runTest {
        val provider = FakeSyncProvider(account = ACCOUNT)
        val repository = repository(provider = provider)

        val result = repository.restore()

        assertTrue(result.isConnected)
        assertFalse(provider.hasForgottenCredentials)
        assertEquals(ACCOUNT, (repository.syncState.value as SyncState.Connected).account)
    }

    @Test
    fun `a connection that was redirected away can still be cancelled`() = runTest {
        val store = FakePendingAuthorizationStore()
        val repository = repository(
            provider = FakeSyncProvider(),
            authenticator = FakeSyncAuthenticator(outcome = SyncAuthenticator.AuthorizationOutcome.Redirected),
            pendingAuthorizationStore = store,
        )

        assertFalse(repository.connect(SyncProviderId.DROPBOX, COMPLETION_PAGE))
        assertEquals(SyncState.Connecting(SyncProviderId.DROPBOX), repository.syncState.first())
        assertNotNull(store.pending)
        repository.cancelConnection()

        assertEquals(SyncState.Disconnected, repository.syncState.value)
        assertNull(store.pending)
    }

    @Test
    fun `cancelling a connection leaves every other state alone`() = runTest {
        val store = FakePendingAuthorizationStore()
        val repository = repository(provider = FakeSyncProvider(account = ACCOUNT), pendingAuthorizationStore = store)
        repository.restore()
        val pending = PendingAuthorization(SyncProviderId.DROPBOX, state = "state", verifier = "verifier", redirectUri = null)
        store.pending = pending

        repository.cancelConnection()

        assertIs<SyncState.Connected>(repository.syncState.value)
        assertEquals(pending, store.pending)
    }

    @Test
    fun `a connection whose storage refuses every write ends as a failure`() = runTest {
        val store = FakePendingAuthorizationStore().apply { onWrite = { throw LibraryStorageException("Full") } }
        val repository = repository(provider = FakeSyncProvider(), pendingAuthorizationStore = store)

        assertFalse(repository.connect(SyncProviderId.DROPBOX, COMPLETION_PAGE))

        assertEquals(SyncState.ConnectionFailed(SyncProviderId.DROPBOX, SyncFailureReason.STORAGE), repository.syncState.value)
    }

    @Test
    fun `an authorization that ends with a reason reports the connection as failed`() = runTest {
        val repository = repository(
            provider = FakeSyncProvider(),
            authenticator = FakeSyncAuthenticator(
                outcome = SyncAuthenticator.AuthorizationOutcome.Cancelled("Timed out waiting for the browser."),
            ),
        )

        assertFalse(repository.connect(SyncProviderId.DROPBOX, COMPLETION_PAGE))

        assertEquals(SyncState.ConnectionFailed(SyncProviderId.DROPBOX, SyncFailureReason.UNKNOWN), repository.syncState.value)
    }

    @Test
    fun `a closed browser is not a failure even when the clean up is`() = runTest {
        val store = FakePendingAuthorizationStore().apply { onWrite = failingAfterFirstWrite() }
        val repository = repository(
            provider = FakeSyncProvider(),
            authenticator = FakeSyncAuthenticator(outcome = SyncAuthenticator.AuthorizationOutcome.Cancelled()),
            pendingAuthorizationStore = store,
        )

        assertFalse(repository.connect(SyncProviderId.DROPBOX, COMPLETION_PAGE))

        assertEquals(SyncState.Disconnected, repository.syncState.value)
    }

    @Test
    fun `giving up on a connection whose clean up fails still ends disconnected`() = runTest {
        val store = FakePendingAuthorizationStore().apply { onWrite = failingAfterFirstWrite() }
        val repository = repository(
            provider = FakeSyncProvider(),
            authenticator = FakeSyncAuthenticator(onAuthorize = { awaitCancellation() }),
            pendingAuthorizationStore = store,
        )

        val job = launch { repository.connect(SyncProviderId.DROPBOX, COMPLETION_PAGE) }
        repository.syncState.first { it is SyncState.Connecting }
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(SyncState.Disconnected, repository.syncState.value)
    }

    /** A storage that takes the pending authorization and then refuses to let go of it. */
    private fun failingAfterFirstWrite(): () -> Unit {
        var writeCount = 0
        return { if (++writeCount > 1) throw LibraryStorageException("Full") }
    }

    private fun repository(
        provider: FakeSyncProvider,
        authenticator: FakeSyncAuthenticator = FakeSyncAuthenticator(),
        pendingAuthorizationStore: FakePendingAuthorizationStore = FakePendingAuthorizationStore(),
        stateLocalSource: FakeSyncStateLocalSource = FakeSyncStateLocalSource(),
        libraryFileLocalSource: FakeLibraryFileLocalSource = FakeLibraryFileLocalSource(),
        songRepository: RecordingSongRepository = RecordingSongRepository(),
        setlistRepository: RecordingSetlistRepository = RecordingSetlistRepository(),
    ) = SyncRepositoryImpl(
        syncProviders = SyncProviders(listOf(provider)),
        authenticator = authenticator,
        pendingAuthorizationStore = pendingAuthorizationStore,
        syncStateLocalSource = stateLocalSource,
        songRepository = songRepository,
        setlistRepository = setlistRepository,
        libraryFileLocalSource = libraryFileLocalSource,
        libraryFileLock = LibraryFileLock(),
    )

    /**
     * The run is on the repository's own dispatcher rather than the test's, so it is waited for through the state it
     * reports; a run that never reports is failed by `runTest`'s own timeout.
     */
    private suspend fun SyncRepositoryImpl.awaitOutcome() = syncState.first {
        it is SyncState.Connected && !it.isSyncing && it.lastOutcome != null
    } as SyncState.Connected

    private companion object {
        val COMPLETION_PAGE = AuthorizationCompletionPage(title = "", message = "")
        val ACCOUNT = SyncAccount(
            providerId = SyncProviderId.DROPBOX,
            id = "dbid:1",
            displayName = "Someone",
            email = "someone@example.com",
        )

        fun song(number: Int) = SyncKey(kind = LibraryFileKind.SONG, name = "song_$number.cho")
    }
}
