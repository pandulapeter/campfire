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

import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.repository.api.SyncRepository
import com.pandulapeter.campfire.data.source.remote.api.PendingAuthorization
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthenticator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Forgetting the connection a previous installation left in a store that outlives the app, without a word to the
 * service, and the note that keeps every start up trying again until it has (see [SyncConnectionManager]).
 */
class SyncConnectionManagerForgettingTest {

    @Test
    fun `forgetting the connection leaves nothing to restore`() = runTest {
        val stateLocalSource = FakeSyncIndexLocalSource(index = "{}")
        val provider = FakeSyncProvider(account = TEST_ACCOUNT)
        val repository = syncRepository(provider = provider, stateLocalSource = stateLocalSource)

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
        val provider = FakeSyncProvider(account = TEST_ACCOUNT).apply { onDisconnect = { fail("The service was told.") } }
        val repository = syncRepository(provider = provider)

        repository.forgetStoredConnection()

        assertTrue(provider.hasForgottenCredentials)
        assertFalse(provider.connected)
    }

    @Test
    fun `forgetting the connection drops an unfinished authorization`() = runTest {
        val store = FakePendingAuthorizationStore().apply {
            pending = PendingAuthorization(SyncProviderId.DROPBOX, state = "state", verifier = "verifier", redirectUri = null)
        }
        val repository = syncRepository(provider = FakeSyncProvider(), pendingAuthorizationStore = store)

        repository.forgetStoredConnection()

        assertNull(store.loadPendingAuthorization())
    }

    @Test
    fun `an ordinary launch does not forget anything`() = runTest {
        val provider = FakeSyncProvider(account = TEST_ACCOUNT)
        val stateLocalSource = FakeSyncIndexLocalSource().apply { onSetForgettingOwed = { fail("A forgetting was noted.") } }
        val repository = syncRepository(provider = provider, stateLocalSource = stateLocalSource)

        val result = repository.restore()

        assertTrue(result.isConnected)
        assertFalse(provider.hasForgottenCredentials)
        assertEquals(TEST_ACCOUNT, (repository.syncState.value as SyncState.Connected).account)
    }

    @Test
    fun `a forgetting that fails restores nothing and is owed`() = runTest {
        val stateLocalSource = FakeSyncIndexLocalSource()
        val provider = FakeSyncProvider(account = TEST_ACCOUNT).apply { onForgetStoredCredentials = { error("The Keychain is locked.") } }
        val repository = syncRepository(provider = provider, stateLocalSource = stateLocalSource)

        repository.forgetStoredConnection()
        val result = repository.restore()

        assertFalse(result.isConnected)
        assertEquals(SyncState.Disconnected, repository.syncState.value)
        assertTrue(stateLocalSource.isForgettingOwed)
        assertTrue(provider.connected)
    }

    @Test
    fun `the next start up forgets what the first one could not`() = runTest {
        val stateLocalSource = FakeSyncIndexLocalSource()
        val provider = FakeSyncProvider(account = TEST_ACCOUNT).apply { onForgetStoredCredentials = { error("The Keychain is locked.") } }
        syncRepository(provider = provider, stateLocalSource = stateLocalSource).forgetStoredConnection()
        provider.onForgetStoredCredentials = {}

        assertFalse(syncRepository(provider = provider, stateLocalSource = stateLocalSource).restore().isConnected)
        assertTrue(provider.hasForgottenCredentials)
        assertFalse(stateLocalSource.isForgettingOwed)

        provider.connected = true
        assertTrue(syncRepository(provider = provider, stateLocalSource = stateLocalSource).restore().isConnected)
    }

    @Test
    fun `a forgetting that works leaves nothing owed`() = runTest {
        val notes = mutableListOf<Boolean>()
        val stateLocalSource = FakeSyncIndexLocalSource().apply { onSetForgettingOwed = { notes += it } }
        val repository = syncRepository(provider = FakeSyncProvider(account = TEST_ACCOUNT), stateLocalSource = stateLocalSource)

        repository.forgetStoredConnection()

        assertEquals(listOf(true, false), notes)
        assertFalse(stateLocalSource.isForgettingOwed)
    }

    @Test
    fun `connecting crosses off a forgetting still owed`() = runTest {
        val stateLocalSource = FakeSyncIndexLocalSource().apply { isForgettingOwed = true }
        val provider = FakeSyncProvider(account = TEST_ACCOUNT).apply {
            connected = false
            onCompleteAuthorization = {
                connected = true
                TEST_ACCOUNT
            }
        }
        val repository = syncRepository(
            provider = provider,
            authenticator = FakeSyncAuthenticator(
                outcome = SyncAuthenticator.AuthorizationOutcome.Received("https://example.com/?code=c&state=state"),
            ),
            stateLocalSource = stateLocalSource,
        )

        assertTrue(repository.connect(SyncProviderId.DROPBOX, TEST_COMPLETION_PAGE))
        assertFalse(stateLocalSource.isForgettingOwed)
        assertTrue(syncRepository(provider = provider, stateLocalSource = stateLocalSource).restore().isConnected)
    }

    @Test
    fun `not knowing whether forgetting is owed restores as usual`() = runTest {
        val stateLocalSource = FakeSyncIndexLocalSource().apply { onIsForgettingOwed = { error("Not readable.") } }
        val repository = syncRepository(provider = FakeSyncProvider(account = TEST_ACCOUNT), stateLocalSource = stateLocalSource)

        assertTrue(repository.restore().isConnected)
    }
}
