/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.auth

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteAuthorizationRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The cache every request of a run reads its token from, and the one read that is not allowed to be cached: the one
 * that never finished.
 */
class SyncCredentialsStoreTest {

    @Test
    fun `a cancelled first read is not remembered as no credentials`() = runTest {
        val storage = StoredSyncCredentials(CONNECTED).apply { shouldCancelNextRead = true }
        val store = SyncCredentialsStore(storage, Logger.Standard)

        assertFailsWith<CancellationException> { store.load() }

        assertEquals("refresh", store.load()?.refreshToken)
    }

    @Test
    fun `an authorization started after a cancelled read keeps the stored tokens`() = runTest {
        val storage = StoredSyncCredentials(CONNECTED).apply { shouldCancelNextRead = true }
        val store = SyncCredentialsStore(storage, Logger.Standard)
        assertFailsWith<CancellationException> { store.load() }

        PendingAuthorizationStoreImpl(store).savePendingAuthorization(providerId = SyncProviderId.DROPBOX, request = REQUEST)

        val stored = assertNotNull(storage.document)
        assertEquals("refresh", stored.refreshToken)
        assertEquals("state", stored.pending?.state)
    }

    @Test
    fun `a read the storage refused is not remembered as no credentials`() = runTest {
        val storage = StoredSyncCredentials(CONNECTED).apply { shouldFailNextRead = true }
        val store = SyncCredentialsStore(storage, Logger.Standard)

        assertFailsWith<LibraryStorageException> { store.load() }

        assertEquals("refresh", store.load()?.refreshToken)
    }

    @Test
    fun `an authorization started while the credentials cannot be read writes nothing over them`() = runTest {
        val storage = StoredSyncCredentials(CONNECTED).apply { shouldFailNextRead = true }
        val store = SyncCredentialsStore(storage, Logger.Standard)

        assertFailsWith<LibraryStorageException> {
            PendingAuthorizationStoreImpl(store).savePendingAuthorization(providerId = SyncProviderId.DROPBOX, request = REQUEST)
        }

        assertEquals(0, storage.writeAttemptCount)
        assertEquals(CONNECTED, storage.credentials)
    }

    @Test
    fun `a document that cannot be parsed is read as none, once`() = runTest {
        val storage = StoredSyncCredentials("not json")
        val store = SyncCredentialsStore(storage, Logger.Standard)

        assertNull(store.load())
        assertNull(store.load())
        assertEquals(1, storage.readCount)
    }

    @Test
    fun `a document that was read is not read again`() = runTest {
        val storage = StoredSyncCredentials(CONNECTED)
        val store = SyncCredentialsStore(storage, Logger.Standard)

        store.load()
        store.load()

        assertEquals(1, storage.readCount)
    }

    @Test
    fun `a write that was refused is not remembered`() = runTest {
        val storage = StoredSyncCredentials(CONNECTED)
        val store = SyncCredentialsStore(storage, Logger.Standard)
        val stored = assertNotNull(store.load())
        storage.shouldRefuseNextWrite = true

        assertFailsWith<IllegalStateException> { store.save(stored.copy(accessToken = "new")) }

        assertEquals("access", store.load()?.accessToken)
    }

    @Test
    fun `a pending authorization that could not be saved leaves nothing to clear`() = runTest {
        val storage = StoredSyncCredentials(CONNECTED)
        val pendingStore = PendingAuthorizationStoreImpl(SyncCredentialsStore(storage, Logger.Standard))
        storage.shouldRefuseNextWrite = true
        assertFailsWith<IllegalStateException> { pendingStore.savePendingAuthorization(SyncProviderId.DROPBOX, REQUEST) }
        storage.shouldRefuseNextWrite = true

        pendingStore.clearPendingAuthorization()

        assertEquals(1, storage.writeAttemptCount)
        assertNull(pendingStore.loadPendingAuthorization())
        assertEquals(CONNECTED, storage.credentials)
    }

    @Test
    fun `a write that was cancelled after it was stored is read back`() = runTest {
        val storage = StoredSyncCredentials().apply { shouldCancelNextWriteAfterStoring = true }
        val store = SyncCredentialsStore(storage, Logger.Standard)

        assertFailsWith<CancellationException> {
            store.save(SyncCredentialsDocument(providerId = "dropbox", accessToken = "access", refreshToken = "refresh"))
        }

        assertEquals("refresh", store.load()?.refreshToken)
    }

    @Test
    fun `the stored tokens name their provider`() = runTest {
        val store = SyncCredentialsStore(StoredSyncCredentials(CONNECTED), Logger.Standard)

        assertEquals(SyncProviderId.DROPBOX, store.storedProviderId())
    }

    @Test
    fun `an authorization that was only started names no provider`() = runTest {
        val store = SyncCredentialsStore(StoredSyncCredentials(), Logger.Standard)
        PendingAuthorizationStoreImpl(store).savePendingAuthorization(providerId = SyncProviderId.DROPBOX, request = REQUEST)

        assertNull(store.storedProviderId())
    }

    @Test
    fun `forgetting its own credentials leaves nothing behind`() = runTest {
        val storage = StoredSyncCredentials(CONNECTED)
        val store = SyncCredentialsStore(storage, Logger.Standard)

        assertEquals("refresh", store.forget(SyncProviderId.DROPBOX)?.refreshToken)

        assertNull(storage.credentials)
    }

    @Test
    fun `forgetting another provider's credentials keeps the stored ones`() = runTest {
        val storage = StoredSyncCredentials(CONNECTED)
        val store = SyncCredentialsStore(storage, Logger.Standard)

        assertNull(store.forget(OTHER))

        assertEquals(CONNECTED, storage.credentials)
        assertEquals(SyncProviderId.DROPBOX, store.storedProviderId())
    }

    @Test
    fun `backing out of another provider's authorization keeps the stored tokens`() = runTest {
        val storage = StoredSyncCredentials(CONNECTED)
        val store = SyncCredentialsStore(storage, Logger.Standard)
        PendingAuthorizationStoreImpl(store).savePendingAuthorization(providerId = OTHER, request = REQUEST)

        assertNull(store.forget(OTHER))

        val stored = assertNotNull(storage.document)
        assertEquals("refresh", stored.refreshToken)
        assertNull(stored.pending)
    }

    @Test
    fun `forgetting its own credentials keeps an authorization another provider started`() = runTest {
        val storage = StoredSyncCredentials(CONNECTED)
        val store = SyncCredentialsStore(storage, Logger.Standard)
        PendingAuthorizationStoreImpl(store).savePendingAuthorization(providerId = OTHER, request = REQUEST)

        store.forget(SyncProviderId.DROPBOX)

        val stored = assertNotNull(storage.document)
        assertEquals("", stored.refreshToken)
        assertEquals(OTHER.id, stored.pending?.providerId)
        assertNull(store.storedProviderId())
    }

    @Test
    fun `forgetting its own authorization before any token arrived leaves no document behind`() = runTest {
        val storage = StoredSyncCredentials()
        val store = SyncCredentialsStore(storage, Logger.Standard)
        PendingAuthorizationStoreImpl(store).savePendingAuthorization(providerId = SyncProviderId.DROPBOX, request = REQUEST)

        store.forget(SyncProviderId.DROPBOX)

        assertNull(storage.credentials)
    }

    @Test
    fun `forgetting a document that cannot be parsed removes it`() = runTest {
        val storage = StoredSyncCredentials("not json")
        val store = SyncCredentialsStore(storage, Logger.Standard)

        store.forget(SyncProviderId.DROPBOX)

        assertNull(storage.credentials)
    }

    private companion object {
        val OTHER = SyncProviderId("test-other")

        val CONNECTED = connectedCredentials()

        val REQUEST = RemoteAuthorizationRequest(
            authorizationUrl = "https://example.com",
            redirectUri = null,
            state = "state",
            verifier = "verifier",
        )
    }
}
