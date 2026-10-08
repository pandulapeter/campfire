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

import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.local.api.SyncCredentialsLocalSource
import com.pandulapeter.campfire.data.source.remote.api.PendingAuthorization
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The authorization that was started and not finished shares the credentials document with the tokens, so starting
 * or abandoning one must never cost the connection it sits beside.
 */
class PendingAuthorizationStoreTest {

    private val storage = Storage()
    private val store = PendingAuthorizationStoreImpl(SyncCredentialsStore(storage, Logger.Standard))

    @Test
    fun `an authorization started while connected keeps the tokens`() = runTest {
        storage.credentials = CONNECTED

        store.savePendingAuthorization(SyncProviderId.DROPBOX, REQUEST)

        assertEquals("refresh", storage.document?.refreshToken)
        assertEquals(
            expected = PendingAuthorization(SyncProviderId.DROPBOX, state = "state", verifier = "verifier", redirectUri = "campfire://oauth"),
            actual = store.loadPendingAuthorization(),
        )
    }

    @Test
    fun `an authorization abandoned while connected keeps the tokens`() = runTest {
        storage.credentials = CONNECTED
        store.savePendingAuthorization(SyncProviderId.DROPBOX, REQUEST)

        store.clearPendingAuthorization()

        assertNull(store.loadPendingAuthorization())
        assertEquals("refresh", storage.document?.refreshToken)
        assertNull(storage.document?.pending)
    }

    @Test
    fun `an authorization abandoned before any token arrived leaves no document behind`() = runTest {
        store.savePendingAuthorization(SyncProviderId.DROPBOX, REQUEST)

        store.clearPendingAuthorization()

        assertNull(storage.credentials)
    }

    /** The credentials document in a variable, as the platform storage would keep it. */
    private class Storage : SyncCredentialsLocalSource {
        var credentials: String? = null

        val document get() = credentials?.let { Json { ignoreUnknownKeys = true }.decodeFromString<SyncCredentialsDocument>(it) }

        override suspend fun loadSyncCredentials() = credentials

        override suspend fun saveSyncCredentials(document: String?) {
            credentials = document
        }
    }

    private companion object {
        const val CONNECTED = """{"providerId":"dropbox","accessToken":"access","refreshToken":"refresh","expiresAt":1}"""

        val REQUEST = RemoteAuthorizationRequest(
            authorizationUrl = "https://example.com",
            redirectUri = "campfire://oauth",
            state = "state",
            verifier = "verifier",
        )
    }
}
