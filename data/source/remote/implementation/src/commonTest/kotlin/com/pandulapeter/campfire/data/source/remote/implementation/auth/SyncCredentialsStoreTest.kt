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

import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.local.api.SyncStateLocalSource
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The cache every request of a run reads its token from, and the one read that is not allowed to be cached: the one
 * that never finished.
 */
class SyncCredentialsStoreTest {

    @Test
    fun `a cancelled first read is not remembered as no credentials`() = runTest {
        val storage = FakeStorage(CONNECTED).apply { shouldCancelNextRead = true }
        val store = SyncCredentialsStore(storage)

        assertFailsWith<CancellationException> { store.load() }

        assertEquals("refresh", store.load()?.refreshToken)
        assertEquals(2, storage.readCount)
    }

    @Test
    fun `an authorization started after a cancelled read keeps the stored tokens`() = runTest {
        val storage = FakeStorage(CONNECTED).apply { shouldCancelNextRead = true }
        val store = SyncCredentialsStore(storage)
        assertFailsWith<CancellationException> { store.load() }

        PendingAuthorizationStoreImpl(store).savePendingAuthorization(
            providerId = SyncProviderId.DROPBOX,
            request = RemoteAuthorizationRequest(
                authorizationUrl = "https://example.com",
                redirectUri = null,
                state = "state",
                verifier = "verifier",
            ),
        )

        val stored = Json { ignoreUnknownKeys = true }.decodeFromString<SyncCredentialsDocument>(storage.credentials.orEmpty())
        assertEquals("refresh", stored.refreshToken)
        assertEquals("state", stored.pending?.state)
    }

    @Test
    fun `a document that cannot be parsed is read as none, once`() = runTest {
        val storage = FakeStorage("not json")
        val store = SyncCredentialsStore(storage)

        assertNull(store.load())
        assertNull(store.load())
        assertEquals(1, storage.readCount)
    }

    @Test
    fun `a document that was read is not read again`() = runTest {
        val storage = FakeStorage(CONNECTED)
        val store = SyncCredentialsStore(storage)

        store.load()
        store.load()

        assertEquals(1, storage.readCount)
    }

    /** Credentials in a variable, with a first read that can be made to end the way a cleared view model ends it. */
    private class FakeStorage(var credentials: String?) : SyncStateLocalSource {
        var shouldCancelNextRead = false
        var readCount = 0

        override suspend fun loadSyncCredentials(): String? {
            readCount++
            if (shouldCancelNextRead) {
                shouldCancelNextRead = false
                throw CancellationException("The reader went away.")
            }
            return credentials
        }

        override suspend fun saveSyncCredentials(document: String?) {
            credentials = document
        }

        override suspend fun loadSyncIndex(): String? = null
        override suspend fun saveSyncIndex(document: String?) = Unit
    }

    private companion object {
        const val CONNECTED = """{"providerId":"dropbox","accessToken":"access","refreshToken":"refresh","expiresAt":1}"""
    }
}
