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

import com.pandulapeter.campfire.data.source.remote.api.sync.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.implementation.network.TEST_TOKEN_URL
import com.pandulapeter.campfire.data.source.remote.implementation.network.TestSyncService
import com.pandulapeter.campfire.data.source.remote.implementation.network.respondTokens
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * How a stored OAuth connection's tokens are kept good: renewed before they expire, renewed once when the service
 * refuses one, and a refusal of the renewal itself told apart from the token endpoint being busy.
 */
class OAuthTokensTest {

    @Test
    fun `an expired token is renewed before the first request rather than after a refusal`() = runTest {
        val requests = mutableListOf<String>()
        var refreshForm = ""
        val service = TestSyncService(storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0))) { request ->
            if (request.url.toString() == TEST_TOKEN_URL) {
                requests += "token"
                refreshForm = (request.body as TextContent).text
                respondTokens("""{"access_token":"renewed","expires_in":14400}""")
            } else {
                requests += request.headers["Authorization"].orEmpty()
                respondTokens("{}")
            }
        }
        service.call()
        assertEquals(expected = listOf("token", "Bearer renewed"), actual = requests)
        assertEquals(expected = "grant_type=refresh_token&refresh_token=refresh&client_id=test-client", actual = refreshForm)
    }

    @Test
    fun `a renewal carries the client secret where the service has one`() = runTest {
        var refreshForm = ""
        val service = TestSyncService(
            storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0)),
            clientSecret = "secret value",
        ) { request ->
            if (request.url.toString() == TEST_TOKEN_URL) {
                refreshForm = (request.body as TextContent).text
                respondTokens("""{"access_token":"renewed","expires_in":14400}""")
            } else {
                respondTokens("{}")
            }
        }
        service.call()
        assertEquals(
            expected = "grant_type=refresh_token&refresh_token=refresh&client_id=test-client&client_secret=secret%20value",
            actual = refreshForm,
        )
    }

    /** A service may hand out a new refresh token, and keeping the old one would end the connection silently. */
    @Test
    fun `a renewal that carries a new refresh token stores it`() = runTest {
        val storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0))
        val service = TestSyncService(storage = storage) { request ->
            if (request.url.toString() == TEST_TOKEN_URL) {
                respondTokens("""{"access_token":"renewed","refresh_token":"new refresh","expires_in":14400}""")
            } else {
                respondTokens("{}")
            }
        }
        service.call()
        assertEquals("renewed", storage.document?.accessToken)
        assertEquals("new refresh", storage.document?.refreshToken)
    }

    @Test
    fun `a renewal without a refresh token keeps the old one`() = runTest {
        val storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0))
        val service = TestSyncService(storage = storage) { request ->
            if (request.url.toString() == TEST_TOKEN_URL) respondTokens("""{"access_token":"renewed","expires_in":14400}""") else respondTokens("{}")
        }
        service.call()
        assertEquals("renewed", storage.document?.accessToken)
        assertEquals("refresh", storage.document?.refreshToken)
    }

    /** Being told to wait says nothing about the credentials, and must not send the user off to connect again. */
    @Test
    fun `a busy token endpoint is the service not being reached rather than a refusal`() = runTest {
        for (status in listOf(HttpStatusCode.TooManyRequests, HttpStatusCode.ServiceUnavailable)) {
            val storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0))
            val service = TestSyncService(storage = storage) { request ->
                if (request.url.toString() == TEST_TOKEN_URL) respond(content = "", status = status) else respondTokens("{}")
            }
            assertFailsWith<SyncNetworkException> { service.call() }
            assertEquals("refresh", storage.document?.refreshToken)
        }
    }

    @Test
    fun `a token exchange the browser could not send is the service not being reached`() = runTest {
        val service = TestSyncService(storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0))) { request ->
            if (request.url.toString() == TEST_TOKEN_URL) throw Error("Fail to fetch")
            respondTokens("{}")
        }
        assertFailsWith<SyncNetworkException> { service.call() }
    }

    /** A token the device's clock still believes in can be one the service has stopped accepting. */
    @Test
    fun `refreshes the token once when the service refuses it`() = runTest {
        val authorizations = mutableListOf<String?>()
        var tokenRequestCount = 0
        val service = TestSyncService { request ->
            if (request.url.toString() == TEST_TOKEN_URL) {
                tokenRequestCount++
                respondTokens("""{"access_token":"renewed","expires_in":14400}""")
            } else {
                authorizations += request.headers["Authorization"]
                if (authorizations.size == 1) respond(content = "expired", status = HttpStatusCode.Unauthorized) else respondTokens("{}")
            }
        }
        service.call()
        assertEquals(expected = 1, actual = tokenRequestCount)
        assertEquals(expected = listOf<String?>("Bearer access", "Bearer renewed"), actual = authorizations)
    }

    @Test
    fun `several requests refused at once renew the token once`() = runTest {
        var tokenRequestCount = 0
        val service = TestSyncService { request ->
            when {
                request.url.toString() == TEST_TOKEN_URL -> {
                    tokenRequestCount++
                    respondTokens("""{"access_token":"renewed","expires_in":14400}""")
                }

                request.headers["Authorization"] == "Bearer renewed" -> respondTokens("song")
                else -> respond(content = "expired", status = HttpStatusCode.Unauthorized)
            }
        }
        coroutineScope {
            repeat(6) { launch { assertEquals("song", service.call()) } }
        }
        assertEquals(expected = 1, actual = tokenRequestCount)
    }

    @Test
    fun `believes a refusal of a token it has just refreshed`() = runTest {
        var tokenRequestCount = 0
        val service = TestSyncService { request ->
            if (request.url.toString() == TEST_TOKEN_URL) {
                tokenRequestCount++
                respondTokens("""{"access_token":"renewed","expires_in":14400}""")
            } else {
                respond(content = "invalid", status = HttpStatusCode.Unauthorized)
            }
        }
        assertFailsWith<SyncAuthorizationException> { service.call() }
        assertEquals(expected = 1, actual = tokenRequestCount)
    }

    @Test
    fun `reports why the token endpoint refused a refresh`() = runTest {
        val service = TestSyncService { request ->
            if (request.url.toString() == TEST_TOKEN_URL) {
                respondTokens("""{"error":"invalid_grant","error_description":"refresh token is invalid or revoked"}""", HttpStatusCode.BadRequest)
            } else {
                respond(content = "expired", status = HttpStatusCode.Unauthorized)
            }
        }
        val exception = assertFailsWith<SyncAuthorizationException> { service.call() }
        assertEquals(
            expected = "Test service refused the authorization: 400 invalid_grant: refresh token is invalid or revoked",
            actual = exception.message,
        )
    }

    @Test
    fun `a refusal that names no OAuth error is reported in the service's own words`() = runTest {
        val service = TestSyncService(storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0))) { request ->
            if (request.url.toString() == TEST_TOKEN_URL) {
                respond(content = "no such app", status = HttpStatusCode.Unauthorized)
            } else {
                respondTokens("{}")
            }
        }
        val exception = assertFailsWith<SyncAuthorizationException> { service.call() }
        assertEquals(expected = "Test service refused the authorization: 401 no such app", actual = exception.message)
    }

    /** A proxy, a firewall, or a captive portal answering for the service says nothing about the credentials. */
    @Test
    fun `an answer from something between the device and the token endpoint is the network rather than a refusal`() = runTest {
        val answers = listOf(
            HttpStatusCode.Forbidden to headersOf(HttpHeaders.ContentType, "text/html"),
            HttpStatusCode.ProxyAuthenticationRequired to headersOf(HttpHeaders.ContentType, "text/html"),
            HttpStatusCode.RequestTimeout to headersOf(HttpHeaders.ContentType, "text/html"),
            HttpStatusCode.Found to headersOf(HttpHeaders.Location, "http://portal.example/login"),
        )
        for ((status, headers) in answers) {
            val storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0))
            val service = TestSyncService(storage = storage) { request ->
                if (request.url.toString() == TEST_TOKEN_URL) {
                    respond(content = "<html>Blocked</html>", status = status, headers = headers)
                } else {
                    respondTokens("{}")
                }
            }
            assertFailsWith<SyncNetworkException>("Answered ${status.value}") { service.call() }
            assertEquals("refresh", storage.document?.refreshToken)
        }
    }

    @Test
    fun `a refresh the token endpoint refuses as an unknown client is a refusal`() = runTest {
        val service = TestSyncService(storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0))) { request ->
            if (request.url.toString() == TEST_TOKEN_URL) {
                respondTokens("""{"error":"invalid_client","error_description":"Invalid client_id"}""", HttpStatusCode.Unauthorized)
            } else {
                respondTokens("{}")
            }
        }
        assertFailsWith<SyncAuthorizationException> { service.call() }
    }

    /** A renewal is the first request of most runs, and a cell handover meets it as often as any other. */
    @Test
    fun `a renewal that times out is tried again`() = runTest {
        var tokenRequestCount = 0
        val service = TestSyncService(storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0))) { request ->
            if (request.url.toString() == TEST_TOKEN_URL) {
                if (++tokenRequestCount == 1) throw SocketTimeoutException("Stalled")
                respondTokens("""{"access_token":"renewed","expires_in":14400}""")
            } else {
                respondTokens("done")
            }
        }
        assertEquals(expected = "done", actual = service.call())
    }

    @Test
    fun `the token request carries its own bound`() = runTest {
        val bounds = mutableListOf<Long?>()
        val service = TestSyncService(storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0))) { request ->
            if (request.url.toString() == TEST_TOKEN_URL) {
                bounds += request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis
                respondTokens("""{"access_token":"renewed","expires_in":14400,"refresh_token":"refresh"}""")
            } else {
                respondTokens("{}")
            }
        }
        service.call()
        service.tokens.exchange("grant_type=authorization_code&code=code")
        assertEquals(expected = listOf<Long?>(30_000L, 30_000L), actual = bounds)
    }

    /** The renewal holds the credentials lock, which every read of the connection waits for. */
    @Test
    fun `a renewal that never answers ends the run in bounded time`() = runTest {
        var tokenRequestCount = 0
        val service = TestSyncService(
            configure = { install(HttpTimeout) },
            storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0)),
            engineDispatcher = StandardTestDispatcher(testScheduler),
        ) { request ->
            if (request.url.toString() == TEST_TOKEN_URL) {
                tokenRequestCount++
                awaitCancellation()
            }
            respondTokens("{}")
        }
        assertFailsWith<SyncNetworkException> { service.call() }
        assertTrue(currentTime < 7 * 30_000L + 94_000L + 7 * 500L, "The renewal took $currentTime ms to fail.")
        assertEquals(expected = 7, actual = tokenRequestCount)
    }

    /** A refusal is the one answer that means connecting again, and asking again changes nothing. */
    @Test
    fun `a refused renewal is not tried again`() = runTest {
        var tokenRequestCount = 0
        val service = TestSyncService(storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0))) { request ->
            if (request.url.toString() == TEST_TOKEN_URL) {
                tokenRequestCount++
                respondTokens("""{"error":"invalid_grant","error_description":"refresh token is invalid or revoked"}""", HttpStatusCode.BadRequest)
            } else {
                respondTokens("{}")
            }
        }
        assertFailsWith<SyncAuthorizationException> { service.call() }
        assertEquals(expected = 1, actual = tokenRequestCount)
    }

    /** The callers of a run queue for the credentials lock, and each starting a renewal of its own would multiply the wait. */
    @Test
    fun `callers waiting for a renewal that never answers fail with it rather than each trying their own`() = runTest {
        var tokenRequestCount = 0
        val service = TestSyncService(
            configure = { install(HttpTimeout) },
            storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0)),
            engineDispatcher = StandardTestDispatcher(testScheduler),
        ) { request ->
            if (request.url.toString() == TEST_TOKEN_URL) {
                tokenRequestCount++
                awaitCancellation()
            }
            respondTokens("{}")
        }
        coroutineScope {
            repeat(6) { launch { assertFailsWith<SyncNetworkException> { service.call() } } }
        }
        assertTrue(currentTime < 7 * 30_000L + 94_000L + 7 * 500L, "The renewals took $currentTime ms to fail.")
        assertEquals(expected = 7, actual = tokenRequestCount)
    }

    @Test
    fun `a caller waiting for a renewal that was cancelled renews for itself`() = runTest {
        var tokenRequestCount = 0
        val service = TestSyncService(
            storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0)),
            engineDispatcher = StandardTestDispatcher(testScheduler),
        ) { request ->
            if (request.url.toString() == TEST_TOKEN_URL) {
                tokenRequestCount++
                if (tokenRequestCount == 1) awaitCancellation()
                respondTokens("""{"access_token":"renewed","expires_in":14400}""")
            } else {
                respondTokens("done")
            }
        }
        val first = launch { service.call() }
        runCurrent()
        assertEquals(expected = 1, actual = tokenRequestCount)
        val second = async { service.call() }
        runCurrent()
        first.cancel()
        assertEquals(expected = "done", actual = second.await())
        assertEquals(expected = 2, actual = tokenRequestCount)
    }

    @Test
    fun `every caller waiting for a refused renewal is told it is refused`() = runTest {
        val service = TestSyncService(storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0))) { request ->
            if (request.url.toString() == TEST_TOKEN_URL) {
                respondTokens("""{"error":"invalid_grant","error_description":"refresh token is invalid or revoked"}""", HttpStatusCode.BadRequest)
            } else {
                respondTokens("{}")
            }
        }
        coroutineScope {
            repeat(2) { launch { assertFailsWith<SyncAuthorizationException> { service.call() } } }
        }
    }

    /** Stored, an empty token would make every following call a refusal, and the connection one to make again. */
    @Test
    fun `a renewal answered without an access token is the network failing and keeps the stored token`() = runTest {
        val storage = StoredSyncCredentials(connectedCredentials(expiresAt = 0))
        val service = TestSyncService(storage = storage) { request ->
            if (request.url.toString() == TEST_TOKEN_URL) respondTokens("""{"expires_in":14400}""") else respondTokens("{}")
        }
        assertFailsWith<SyncNetworkException> { service.call() }
        assertEquals("access", storage.document?.accessToken)
    }

    @Test
    fun `credentials stored for another provider are no connection`() = runTest {
        var requestCount = 0
        val service = TestSyncService(
            storage = StoredSyncCredentials("""{"providerId":"other","accessToken":"access","refreshToken":"refresh","expiresAt":${Long.MAX_VALUE}}"""),
        ) {
            requestCount++
            respondTokens("{}")
        }
        assertFailsWith<SyncAuthorizationException> { service.call() }
        assertEquals(expected = 0, actual = requestCount)
    }
}
