/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.connection

import com.pandulapeter.campfire.data.model.sync.SyncAccount
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.sync.SyncFailureReason
import com.pandulapeter.campfire.data.model.sync.SyncOutcome
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.remote.api.sync.PendingAuthorization
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncAuthenticator
import com.pandulapeter.campfire.data.sync.implementation.FakePendingAuthorizationStore
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncAuthenticator
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncProvider
import com.pandulapeter.campfire.data.sync.implementation.RecordingLogger
import com.pandulapeter.campfire.data.sync.implementation.TEST_ACCOUNT
import com.pandulapeter.campfire.data.sync.implementation.TEST_COMPLETION_PAGE
import com.pandulapeter.campfire.data.sync.implementation.TEST_REDIRECT
import com.pandulapeter.campfire.data.sync.implementation.awaitOutcome
import com.pandulapeter.campfire.data.sync.implementation.song
import com.pandulapeter.campfire.data.sync.implementation.syncRepository
import com.pandulapeter.campfire.data.sync.implementation.synchronize
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A build with a second provider beside Dropbox: the stored credentials say which one is connected, whatever the other
 * says about itself, so neither provider has to tell its credentials from another's to keep from looking connected.
 * Both fakes claim credentials throughout, which is exactly what the choice must not go by.
 */
class SyncConnectionManagerProvidersTest {

    private val dropbox = FakeSyncProvider(files = mapOf(song(1) to "One".encodeToByteArray()), account = TEST_ACCOUNT)
    private val other = FakeSyncProvider(files = mapOf(song(2) to "Two".encodeToByteArray()), account = OTHER_ACCOUNT, id = OTHER)

    @Test
    fun `credentials of the other provider never make Dropbox look connected`() = runTest {
        val repository = syncRepository(provider = dropbox, otherProvider = other, storedProviderId = { OTHER })

        assertTrue(repository.restore {}.isConnected)

        assertEquals(OTHER_ACCOUNT, assertIs<SyncState.Connected>(repository.syncState.value).account)
    }

    @Test
    fun `credentials of Dropbox never make the other provider look connected`() = runTest {
        val repository = syncRepository(provider = other, otherProvider = dropbox, storedProviderId = { SyncProviderId.DROPBOX })

        assertTrue(repository.restore {}.isConnected)

        assertEquals(TEST_ACCOUNT, assertIs<SyncState.Connected>(repository.syncState.value).account)
    }

    @Test
    fun `a run goes to the provider the credentials belong to`() = runTest {
        val repository = syncRepository(provider = dropbox, otherProvider = other, storedProviderId = { OTHER })
        repository.restore {}

        repository.synchronize(SyncDeletionPolicy.ASK)

        assertIs<SyncOutcome.Success>(repository.awaitOutcome().lastOutcome)
        assertEquals(0, dropbox.listCount)
        assertTrue(other.listCount > 0)
    }

    @Test
    fun `credentials of a provider the build does not have restore nothing`() = runTest {
        val repository = syncRepository(provider = dropbox, storedProviderId = { OTHER })

        assertFalse(repository.restore {}.isConnected)

        assertEquals(SyncState.Disconnected, repository.syncState.value)
    }

    @Test
    fun `unreadable credentials are reported against the provider they name`() = runTest {
        other.onIsConnected = { throw LibraryStorageException("Keystore busy") }
        val repository = syncRepository(provider = dropbox, otherProvider = other, storedProviderId = { OTHER })

        repository.restore {}

        assertEquals(SyncState.ConnectionFailed(OTHER, SyncFailureReason.STORAGE), repository.syncState.value)
    }

    @Test
    fun `credentials that name nobody readably are reported against the first provider`() = runTest {
        val repository = syncRepository(
            provider = dropbox,
            otherProvider = other,
            storedProviderId = { throw LibraryStorageException("Keystore busy") },
        )

        repository.restore {}

        assertEquals(SyncState.ConnectionFailed(SyncProviderId.DROPBOX, SyncFailureReason.STORAGE), repository.syncState.value)
    }

    @Test
    fun `disconnecting the other provider leaves Dropbox's credentials alone`() = runTest {
        val repository = syncRepository(provider = dropbox, otherProvider = other, storedProviderId = { OTHER })
        repository.restore {}

        repository.disconnect()

        assertEquals(SyncState.Disconnected, repository.syncState.value)
        assertFalse(other.connected)
        assertTrue(dropbox.connected)
    }

    @Test
    fun `backing out of connecting the other provider leaves Dropbox's credentials alone`() = runTest {
        val repository = syncRepository(
            provider = dropbox,
            otherProvider = other,
            authenticator = FakeSyncAuthenticator(onAuthorize = { awaitCancellation() }),
            storedProviderId = { SyncProviderId.DROPBOX },
        )
        repository.restore {}

        val job = launch { repository.connect(OTHER, TEST_COMPLETION_PAGE) }
        repository.syncState.first { it is SyncState.Connecting }
        job.cancelAndJoin()

        assertFalse(dropbox.hasForgottenCredentials)
        assertTrue(dropbox.connected)
        assertEquals(TEST_ACCOUNT, assertIs<SyncState.Connected>(repository.syncState.value).account)
    }

    @Test
    fun `giving up on the other provider's token exchange forgets only what it stored`() = runTest {
        other.connected = false
        other.onCompleteAuthorization = {
            other.connected = true
            awaitCancellation()
        }
        val repository = syncRepository(
            provider = dropbox,
            otherProvider = other,
            authenticator = FakeSyncAuthenticator(outcome = SyncAuthenticator.AuthorizationOutcome.Received(TEST_REDIRECT)),
            storedProviderId = { SyncProviderId.DROPBOX },
        )
        repository.restore {}

        val job = launch { repository.connect(OTHER, TEST_COMPLETION_PAGE) }
        runCurrent()
        job.cancelAndJoin()

        assertTrue(other.hasForgottenCredentials)
        assertFalse(dropbox.hasForgottenCredentials)
        assertTrue(dropbox.connected)
    }

    @Test
    fun `an answer for a provider the build does not have is dropped and the connection restored`() = runTest {
        val logger = RecordingLogger()
        val store = FakePendingAuthorizationStore().apply {
            pending = PendingAuthorization(OTHER, state = "state", verifier = "verifier", redirectUri = null)
        }
        val repository = syncRepository(
            provider = dropbox,
            authenticator = FakeSyncAuthenticator(pendingRedirect = TEST_REDIRECT),
            pendingAuthorizationStore = store,
            logger = logger,
        )

        var returned = false
        assertTrue(repository.restore { returned = true }.isConnected)

        assertFalse(returned)
        assertNull(store.pending)
        assertEquals(TEST_ACCOUNT, assertIs<SyncState.Connected>(repository.syncState.value).account)
        assertTrue(logger.lines.any { "Dropped the answer" in it })
    }

    private companion object {
        val OTHER = SyncProviderId("test-other")

        val OTHER_ACCOUNT = SyncAccount(providerId = OTHER, id = "other:1", displayName = "Someone else", email = null)
    }
}
