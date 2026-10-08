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

import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.domain.SyncFailureReason
import com.pandulapeter.campfire.data.model.domain.SyncOutcome
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.repository.api.SyncRepository
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.remote.api.PendingAuthorization
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthenticator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Who is connected: restoring the connection at start up, connecting, cancelling, disconnecting and forgetting what a
 * previous installation left, driven through the repository, whose facade only hands these to [SyncConnectionManager],
 * over the same fakes a run uses.
 */
class SyncConnectionManagerTest {

    @Test
    fun `restoring again while a run is going leaves the run alone`() = runTest {
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
        val result = repository.restore()

        assertTrue(result.isConnected)
        assertFalse(result.wasInterrupted)
        val state = assertIs<SyncState.Connected>(repository.syncState.value)
        assertTrue(state.isSyncing)
        assertNull(state.lastOutcome)
        assertTrue(assertNotNull(stateLocalSource.document).isRunInProgress)
        gate.complete(Unit)
        assertIs<SyncOutcome.Success>(repository.awaitOutcome().lastOutcome)
    }

    @Test
    fun `restoring again after an interrupted run still reports it`() = runTest {
        val repository = syncRepository(
            provider = FakeSyncProvider(account = TEST_ACCOUNT),
            stateLocalSource = FakeSyncIndexLocalSource(index = """{"isRunInProgress":true}"""),
        )

        assertTrue(repository.restore().wasInterrupted)
        assertTrue(repository.restore().wasInterrupted)
        assertEquals(SyncOutcome.Interrupted, (repository.syncState.value as SyncState.Connected).lastOutcome)
    }

    @Test
    fun `an interrupted automatic run is neither reported nor keeps the launch run from starting`() = runTest {
        val stateLocalSource = FakeSyncIndexLocalSource(index = """{"isRunInProgress":true,"isAutomaticRunInProgress":true}""")
        val repository = syncRepository(provider = FakeSyncProvider(account = TEST_ACCOUNT), stateLocalSource = stateLocalSource)

        assertFalse(repository.restore().wasInterrupted)
        assertNull((repository.syncState.value as SyncState.Connected).lastOutcome)
        assertFalse(assertNotNull(stateLocalSource.document).isRunInProgress)
        assertFalse(assertNotNull(stateLocalSource.document).isAutomaticRunInProgress)
    }

    @Test
    fun `a disconnect that is cancelled after the credentials went still ends disconnected`() = runTest {
        val stateLocalSource = FakeSyncIndexLocalSource(index = "{}")
        val provider = FakeSyncProvider(account = TEST_ACCOUNT).apply { onDisconnect = { delay(1_000) } }
        val repository = syncRepository(provider = provider, stateLocalSource = stateLocalSource)
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
    fun `backing out of reconnecting a refused connection keeps it failed`() = runTest {
        val repository = syncRepository(
            provider = FakeSyncProvider(
                files = mapOf(song(1) to "One".encodeToByteArray()),
                onDownload = { throw SyncAuthorizationException("Refused") },
                account = TEST_ACCOUNT,
            ),
            authenticator = FakeSyncAuthenticator(outcome = SyncAuthenticator.AuthorizationOutcome.Cancelled()),
        )
        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        repository.syncState.first { it is SyncState.ConnectionFailed }

        assertFalse(repository.connect(SyncProviderId.DROPBOX, TEST_COMPLETION_PAGE))

        assertEquals(SyncState.ConnectionFailed(SyncProviderId.DROPBOX, SyncFailureReason.AUTHORIZATION), repository.syncState.value)
    }

    @Test
    fun `cancelling the reconnect of a refused connection keeps it failed`() = runTest {
        val repository = syncRepository(
            provider = FakeSyncProvider(
                files = mapOf(song(1) to "One".encodeToByteArray()),
                onDownload = { throw SyncAuthorizationException("Refused") },
                account = TEST_ACCOUNT,
            ),
            authenticator = FakeSyncAuthenticator(onAuthorize = { awaitCancellation() }),
        )
        repository.restore()
        repository.synchronize(SyncDeletionPolicy.ASK)
        repository.syncState.first { it is SyncState.ConnectionFailed }

        // In the order the view model gives up in: the waiting connection first, then the authorization it started.
        val job = launch { repository.connect(SyncProviderId.DROPBOX, TEST_COMPLETION_PAGE) }
        runCurrent()
        assertEquals(SyncState.Connecting(SyncProviderId.DROPBOX), repository.syncState.value)
        job.cancelAndJoin()
        repository.cancelConnection()

        assertEquals(SyncState.ConnectionFailed(SyncProviderId.DROPBOX, SyncFailureReason.AUTHORIZATION), repository.syncState.value)
    }

    @Test
    fun `backing out of retrying a connection that never stored credentials ends disconnected`() = runTest {
        val store = FakePendingAuthorizationStore().apply { onWrite = { throw LibraryStorageException("Full") } }
        val provider = FakeSyncProvider()
        val repository = syncRepository(
            provider = provider,
            authenticator = FakeSyncAuthenticator(outcome = SyncAuthenticator.AuthorizationOutcome.Cancelled()),
            pendingAuthorizationStore = store,
        )
        assertFalse(repository.connect(SyncProviderId.DROPBOX, TEST_COMPLETION_PAGE))
        assertEquals(SyncState.ConnectionFailed(SyncProviderId.DROPBOX, SyncFailureReason.STORAGE), repository.syncState.value)
        provider.connected = false
        store.onWrite = {}

        assertFalse(repository.connect(SyncProviderId.DROPBOX, TEST_COMPLETION_PAGE))

        assertEquals(SyncState.Disconnected, repository.syncState.value)
    }

    @Test
    fun `restoring with an unreadable index still shows the account`() = runTest {
        val repository = syncRepository(
            provider = FakeSyncProvider(account = TEST_ACCOUNT),
            stateLocalSource = FakeSyncIndexLocalSource(onLoadIndex = { throw LibraryStorageException("Locked") }),
        )

        val result = repository.restore()

        assertTrue(result.isConnected)
        assertIs<SyncState.Connected>(repository.syncState.value)
    }

    @Test
    fun `restoring again with nothing going starts from the state`() = runTest {
        val repository = syncRepository(
            provider = FakeSyncProvider(files = mapOf(song(1) to "One".encodeToByteArray()), account = TEST_ACCOUNT),
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
        val stateLocalSource = FakeSyncIndexLocalSource()
        val repository = syncRepository(
            provider = FakeSyncProvider(
                files = mapOf(song(1) to "One".encodeToByteArray()),
                onDownload = { CompletableDeferred<Unit>().await() },
                account = TEST_ACCOUNT,
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
    fun `a connection that was redirected away can still be cancelled`() = runTest {
        val store = FakePendingAuthorizationStore()
        val repository = syncRepository(
            provider = FakeSyncProvider(),
            authenticator = FakeSyncAuthenticator(outcome = SyncAuthenticator.AuthorizationOutcome.Redirected),
            pendingAuthorizationStore = store,
        )

        assertFalse(repository.connect(SyncProviderId.DROPBOX, TEST_COMPLETION_PAGE))
        assertEquals(SyncState.Connecting(SyncProviderId.DROPBOX), repository.syncState.first())
        assertNotNull(store.pending)
        repository.cancelConnection()

        assertEquals(SyncState.Disconnected, repository.syncState.value)
        assertNull(store.pending)
    }

    @Test
    fun `cancelling a connection leaves every other state alone`() = runTest {
        val store = FakePendingAuthorizationStore()
        val repository = syncRepository(provider = FakeSyncProvider(account = TEST_ACCOUNT), pendingAuthorizationStore = store)
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
        val repository = syncRepository(provider = FakeSyncProvider(), pendingAuthorizationStore = store)

        assertFalse(repository.connect(SyncProviderId.DROPBOX, TEST_COMPLETION_PAGE))

        assertEquals(SyncState.ConnectionFailed(SyncProviderId.DROPBOX, SyncFailureReason.STORAGE), repository.syncState.value)
    }

    @Test
    fun `an authorization that ends with a reason reports the connection as failed`() = runTest {
        val repository = syncRepository(
            provider = FakeSyncProvider(),
            authenticator = FakeSyncAuthenticator(
                outcome = SyncAuthenticator.AuthorizationOutcome.Cancelled("Timed out waiting for the browser."),
            ),
        )

        assertFalse(repository.connect(SyncProviderId.DROPBOX, TEST_COMPLETION_PAGE))

        assertEquals(SyncState.ConnectionFailed(SyncProviderId.DROPBOX, SyncFailureReason.UNKNOWN), repository.syncState.value)
    }

    @Test
    fun `a closed browser is not a failure even when the clean up is`() = runTest {
        val store = FakePendingAuthorizationStore().apply { onWrite = failingAfterFirstWrite() }
        val logger = RecordingLogger()
        val repository = syncRepository(
            provider = FakeSyncProvider(),
            authenticator = FakeSyncAuthenticator(outcome = SyncAuthenticator.AuthorizationOutcome.Cancelled()),
            pendingAuthorizationStore = store,
            logger = logger,
        )

        assertFalse(repository.connect(SyncProviderId.DROPBOX, TEST_COMPLETION_PAGE))

        assertEquals(SyncState.Disconnected, repository.syncState.value)
        // Only the kind of the failure, since the message of one that came from the credentials document may quote it.
        val line = logger.lines.single { "pending authorization" in it }
        assertTrue("LibraryStorageException" in line)
        assertFalse("Full" in line)
    }

    @Test
    fun `giving up on a connection whose clean up fails still ends disconnected`() = runTest {
        val store = FakePendingAuthorizationStore().apply { onWrite = failingAfterFirstWrite() }
        val repository = syncRepository(
            provider = FakeSyncProvider(),
            authenticator = FakeSyncAuthenticator(onAuthorize = { awaitCancellation() }),
            pendingAuthorizationStore = store,
        )

        val job = launch { repository.connect(SyncProviderId.DROPBOX, TEST_COMPLETION_PAGE) }
        repository.syncState.first { it is SyncState.Connecting }
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(SyncState.Disconnected, repository.syncState.value)
    }

    @Test
    fun `giving up after the tokens were stored forgets them`() = runTest {
        val provider = FakeSyncProvider().apply {
            connected = false
            onCompleteAuthorization = {
                connected = true
                awaitCancellation()
            }
        }
        val repository = syncRepository(
            provider = provider,
            authenticator = FakeSyncAuthenticator(outcome = SyncAuthenticator.AuthorizationOutcome.Received(TEST_REDIRECT)),
        )

        val job = launch { repository.connect(SyncProviderId.DROPBOX, TEST_COMPLETION_PAGE) }
        runCurrent()
        assertTrue(provider.connected)
        job.cancelAndJoin()

        assertTrue(provider.hasForgottenCredentials)
        assertFalse(provider.isConnected())
        assertEquals(SyncState.Disconnected, repository.syncState.value)
        assertFalse(repository.restore().isConnected)
    }

    @Test
    fun `a connection whose index cannot be reset forgets the tokens it stored`() = runTest {
        val provider = FakeSyncProvider().apply {
            connected = false
            onCompleteAuthorization = {
                connected = true
                TEST_ACCOUNT
            }
        }
        val repository = syncRepository(
            provider = provider,
            authenticator = FakeSyncAuthenticator(outcome = SyncAuthenticator.AuthorizationOutcome.Received(TEST_REDIRECT)),
            stateLocalSource = FakeSyncIndexLocalSource(
                index = """{"accountId":"someone-else"}""",
                onSaveIndex = { throw LibraryStorageException("Full") },
            ),
        )

        assertFalse(repository.connect(SyncProviderId.DROPBOX, TEST_COMPLETION_PAGE))

        assertEquals(SyncState.ConnectionFailed(SyncProviderId.DROPBOX, SyncFailureReason.STORAGE), repository.syncState.value)
        assertTrue(provider.hasForgottenCredentials)
    }

    @Test
    fun `credentials that cannot be read right now are reported as that and start nothing`() = runTest {
        val provider = FakeSyncProvider(account = TEST_ACCOUNT).apply {
            onIsConnected = { throw LibraryStorageException("Keystore busy") }
        }
        val repository = syncRepository(provider = provider)

        assertFalse(repository.restore().isConnected)

        assertEquals(SyncState.ConnectionFailed(SyncProviderId.DROPBOX, SyncFailureReason.STORAGE), repository.syncState.value)
        assertEquals(0, provider.listCount)
    }

    @Test
    fun `a disconnect whose credentials cannot be read still ends disconnected`() = runTest {
        val provider = FakeSyncProvider(account = TEST_ACCOUNT)
        val stateLocalSource = FakeSyncIndexLocalSource(index = "{}")
        val repository = syncRepository(provider = provider, stateLocalSource = stateLocalSource)
        repository.restore()
        provider.onIsConnected = { throw LibraryStorageException("Keystore busy") }

        repository.disconnect()

        assertEquals(SyncState.Disconnected, repository.syncState.value)
        assertNull(stateLocalSource.index)
    }

    @Test
    fun `connecting an account whose index belongs to another account starts the index over`() = runTest {
        val stateLocalSource = FakeSyncIndexLocalSource(index = indexWithOneSong(accountId = "dropbox:someone-else").encoded())
        val repository = syncRepository(
            provider = providerThatConnects(),
            authenticator = FakeSyncAuthenticator(outcome = SyncAuthenticator.AuthorizationOutcome.Received(TEST_REDIRECT)),
            stateLocalSource = stateLocalSource,
        )

        assertTrue(repository.connect(SyncProviderId.DROPBOX, TEST_COMPLETION_PAGE))

        assertEquals(SyncIndexDocument(), stateLocalSource.document)
    }

    @Test
    fun `connecting the account the index belongs to again keeps the index`() = runTest {
        val document = indexWithOneSong(accountId = TEST_ACCOUNT.indexKey())
        val stateLocalSource = FakeSyncIndexLocalSource(index = document.encoded())
        val repository = syncRepository(
            provider = providerThatConnects(),
            authenticator = FakeSyncAuthenticator(outcome = SyncAuthenticator.AuthorizationOutcome.Received(TEST_REDIRECT)),
            stateLocalSource = stateLocalSource,
        )

        assertTrue(repository.connect(SyncProviderId.DROPBOX, TEST_COMPLETION_PAGE))

        assertEquals(document, stateLocalSource.document)
    }

    @Test
    fun `a start up that returns from the consent page completes the connection`() = runTest {
        val store = FakePendingAuthorizationStore().apply { pending = PENDING }
        val repository = syncRepository(
            provider = providerThatConnects(),
            authenticator = FakeSyncAuthenticator(pendingRedirect = TEST_REDIRECT),
            pendingAuthorizationStore = store,
        )

        val result = repository.restore()

        assertEquals(SyncRepository.RestoreResult(isConnected = true, didReturnFromAuthorization = true, wasInterrupted = false), result)
        assertEquals(TEST_ACCOUNT, assertIs<SyncState.Connected>(repository.syncState.value).account)
        assertNull(store.pending)
    }

    @Test
    fun `a returning redirect that does not carry the state the authorization started with stores nothing`() = runTest {
        val store = FakePendingAuthorizationStore().apply { pending = PENDING }
        val provider = FakeSyncProvider().apply {
            connected = false
            onCompleteAuthorization = { fail("The code was exchanged.") }
        }
        val repository = syncRepository(
            provider = provider,
            authenticator = FakeSyncAuthenticator(pendingRedirect = "campfire://sync?code=c&state=forged"),
            pendingAuthorizationStore = store,
        )

        val result = repository.restore()

        assertFalse(result.isConnected)
        assertTrue(result.didReturnFromAuthorization)
        assertEquals(SyncState.ConnectionFailed(SyncProviderId.DROPBOX, SyncFailureReason.UNKNOWN), repository.syncState.value)
        assertFalse(provider.connected)
        assertNull(store.pending)
    }

    @Test
    fun `a consent page the user declined reports the authorization as refused`() = runTest {
        val store = FakePendingAuthorizationStore().apply { pending = PENDING }
        val provider = FakeSyncProvider().apply {
            connected = false
            onCompleteAuthorization = { fail("The code was exchanged.") }
        }
        val repository = syncRepository(
            provider = provider,
            authenticator = FakeSyncAuthenticator(pendingRedirect = "campfire://sync?error=access_denied&state=state"),
            pendingAuthorizationStore = store,
        )

        assertFalse(repository.restore().isConnected)

        assertEquals(SyncState.ConnectionFailed(SyncProviderId.DROPBOX, SyncFailureReason.AUTHORIZATION), repository.syncState.value)
        assertFalse(provider.connected)
        assertNull(store.pending)
    }

    /** What Android hands over again when a finished task is reopened from the recents. */
    @Test
    fun `a spent redirect with no authorization waiting still restores the connected account`() = runTest {
        val repository = syncRepository(
            provider = FakeSyncProvider(account = TEST_ACCOUNT),
            authenticator = FakeSyncAuthenticator(pendingRedirect = TEST_REDIRECT),
        )

        val result = repository.restore()

        assertEquals(SyncRepository.RestoreResult(isConnected = true, didReturnFromAuthorization = false, wasInterrupted = false), result)
        assertEquals(TEST_ACCOUNT, assertIs<SyncState.Connected>(repository.syncState.value).account)
    }

    /** A provider with nothing stored, which [TEST_ACCOUNT]'s credentials are stored in once a code is exchanged. */
    private fun providerThatConnects() = FakeSyncProvider().apply {
        connected = false
        onCompleteAuthorization = {
            connected = true
            TEST_ACCOUNT
        }
    }

    private fun indexWithOneSong(accountId: String) = SyncIndexDocument(
        providerId = SyncProviderId.DROPBOX.id,
        accountId = accountId,
        lastSyncedAt = 5,
        entries = mapOf(song(1).path to SyncIndexDocument.Entry(localHash = "hash", remoteRevision = "r1")),
    )

    /** A storage that takes the pending authorization and then refuses to let go of it. */
    private fun failingAfterFirstWrite(): () -> Unit {
        var writeCount = 0
        return { if (++writeCount > 1) throw LibraryStorageException("Full") }
    }

    private companion object {

        /** The authorization [FakeSyncProvider] starts, as it is written down before the consent page opens. */
        val PENDING = PendingAuthorization(SyncProviderId.DROPBOX, state = "state", verifier = "verifier", redirectUri = null)
    }
}
