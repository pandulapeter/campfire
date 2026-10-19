/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.dropbox

import com.pandulapeter.campfire.data.source.remote.implementation.auth.StoredSyncCredentials
import com.pandulapeter.campfire.data.source.remote.implementation.auth.connectedCredentials
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * How a disconnect leaves the device at once and tells Dropbox afterwards: the credentials are forgotten before any
 * request, and the revocation the caller runs in the background works from what was captured and stores nothing. The
 * revocation's requests run on the test's scheduler: its time bounds are virtual, and a request served anywhere else
 * would find them expired before it was answered.
 */
class DropboxDisconnectTest {

    @Test
    fun `disconnecting forgets the credentials before any request`() = runTest {
        val storage = StoredSyncCredentials(connectedCredentials())
        val provider = dropboxProvider(storage = storage) { error("No request was expected.") }

        val revocation = provider.disconnect()

        assertNotNull(revocation)
        assertNull(storage.credentials)
    }

    @Test
    fun `disconnecting with nothing stored has nothing to revoke`() = runTest {
        val provider = dropboxProvider(storage = StoredSyncCredentials()) { error("No request was expected.") }

        assertNull(provider.disconnect())
    }

    @Test
    fun `the revocation renews an expired token without storing it`() = runTest {
        val storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0))
        val requests = mutableListOf<String>()
        val provider = dropboxProvider(storage = storage, engineDispatcher = StandardTestDispatcher(testScheduler)) { request ->
            if (request.url.toString() == TOKEN_URL) {
                requests += "token"
                respondJson("""{"access_token":"renewed","refresh_token":"new refresh","expires_in":14400}""")
            } else {
                requests += "${request.url} ${request.headers["Authorization"]}"
                respondJson("null")
            }
        }

        assertNotNull(provider.disconnect()).invoke()

        assertEquals(expected = listOf("token", "$REVOKE_URL Bearer renewed"), actual = requests)
        assertNull(storage.credentials)
    }

    @Test
    fun `the revocation uses a token that is still good without renewing it`() = runTest {
        val requests = mutableListOf<String>()
        val provider = dropboxProvider(engineDispatcher = StandardTestDispatcher(testScheduler)) { request ->
            requests += "${request.url} ${request.headers["Authorization"]}"
            respondJson("null")
        }

        assertNotNull(provider.disconnect()).invoke()

        assertEquals(expected = listOf("$REVOKE_URL Bearer access"), actual = requests)
    }

    @Test
    fun `a revocation that cannot reach the service ends quietly`() = runTest {
        val storage = StoredSyncCredentials(connectedCredentials())
        var revocationAttempts = 0
        val provider = dropboxProvider(storage = storage, engineDispatcher = StandardTestDispatcher(testScheduler)) { request ->
            if (request.url.toString() == REVOKE_URL) {
                revocationAttempts++
                throw Error("Fail to fetch")
            }
            error("No other request was expected.")
        }

        assertNotNull(provider.disconnect()).invoke()

        assertEquals(1, revocationAttempts)
        assertNull(storage.credentials)
    }

    private companion object {
        const val REVOKE_URL = "https://api.dropboxapi.com/2/auth/token/revoke"
    }
}
