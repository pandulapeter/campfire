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

import com.pandulapeter.campfire.data.source.remote.api.sync.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteAuthorizationResponse
import com.pandulapeter.campfire.data.source.remote.implementation.auth.StoredSyncCredentials
import com.pandulapeter.campfire.data.source.remote.implementation.auth.connectedCredentials
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * What of the token handling is Dropbox's own: its token endpoint and app key, its name in a refusal, and the exchange
 * of an authorization code. The rules every OAuth connection shares are `OAuthTokensTest`'s.
 */
class DropboxTokensTest {

    @Test
    fun `an expired token is renewed at Dropbox's token endpoint with the app key before the first request`() = runTest {
        val requests = mutableListOf<String>()
        var refreshForm = ""
        val provider = dropboxProvider(storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0))) { request ->
            if (request.url.toString() == TOKEN_URL) {
                requests += "token"
                refreshForm = (request.body as TextContent).text
                respondJson("""{"access_token":"renewed","expires_in":14400}""")
            } else {
                requests += request.headers["Authorization"].orEmpty()
                respondJson(EMPTY_LISTING)
            }
        }
        provider.list()
        assertEquals(expected = listOf("token", "Bearer renewed"), actual = requests)
        assertEquals(expected = "grant_type=refresh_token&refresh_token=refresh&client_id=test-app-key", actual = refreshForm)
    }

    @Test
    fun `reports why the token endpoint refused a refresh`() = runTest {
        val provider = dropboxProvider { request ->
            if (request.url.toString() == TOKEN_URL) {
                respondJson("""{"error":"invalid_grant","error_description":"refresh token is invalid or revoked"}""", HttpStatusCode.BadRequest)
            } else {
                respondJson("""{"error_summary":"expired_access_token/..."}""", HttpStatusCode.Unauthorized)
            }
        }
        val exception = assertFailsWith<SyncAuthorizationException> { provider.list() }
        assertEquals(
            expected = "Dropbox refused the authorization: 400 invalid_grant: refresh token is invalid or revoked",
            actual = exception.message,
        )
    }

    /** A code that was spent or expired, after a reload or a slow consent page: connecting failed. */
    @Test
    fun `an authorization code the token endpoint refuses is a refusal and stores nothing`() = runTest {
        val storage = StoredSyncCredentials()
        val provider = dropboxProvider(storage = storage) {
            respondJson("""{"error":"invalid_grant","error_description":"code doesn't exist or has expired"}""", HttpStatusCode.BadRequest)
        }
        assertFailsWith<SyncAuthorizationException> { provider.completeAuthorization(AUTHORIZATION_RESPONSE, verifier = "verifier", redirectUri = null) }
        assertNull(storage.credentials)
    }

    @Test
    fun `an authorization code exchange a proxy blocks is the network`() = runTest {
        val storage = StoredSyncCredentials()
        val provider = dropboxProvider(storage = storage) {
            respond(content = "<html>Blocked</html>", status = HttpStatusCode.Forbidden, headers = headersOf(HttpHeaders.ContentType, "text/html"))
        }
        assertFailsWith<SyncNetworkException> { provider.completeAuthorization(AUTHORIZATION_RESPONSE, verifier = "verifier", redirectUri = null) }
        assertNull(storage.credentials)
    }

    @Test
    fun `the token request carries its own bound`() = runTest {
        val bounds = mutableListOf<Long?>()
        val provider = dropboxProvider(storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0))) { request ->
            if (request.url.toString() == TOKEN_URL) {
                bounds += request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis
                respondJson("""{"access_token":"renewed","expires_in":14400,"refresh_token":"refresh","account_id":"dbid:1"}""")
            } else {
                respondJson(EMPTY_LISTING)
            }
        }
        provider.list()
        provider.completeAuthorization(AUTHORIZATION_RESPONSE, verifier = "verifier", redirectUri = null)
        assertEquals(expected = listOf<Long?>(30_000L, 30_000L), actual = bounds)
    }

    private companion object {
        val AUTHORIZATION_RESPONSE = RemoteAuthorizationResponse(code = "code", state = "state")
    }
}
