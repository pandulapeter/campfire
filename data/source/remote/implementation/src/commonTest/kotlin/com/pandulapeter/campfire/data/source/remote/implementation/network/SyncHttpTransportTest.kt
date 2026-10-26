/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.network

import com.pandulapeter.campfire.data.source.remote.api.sync.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.implementation.auth.OAuthTokenResponse
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * How a call reaches a sync service whatever the provider: the waits it is asked for, the retries of a call that got
 * no answer, and the one renewal a refusal gets. The waiting happens in virtual time.
 */
class SyncHttpTransportTest {

    @Test
    fun `a plain refusal renews the token once and then is a refusal`() = runTest {
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
    fun `a refusal no renewal cures is thrown at once without renewing the token`() = runTest {
        var tokenRequestCount = 0
        val service = TestSyncService { request ->
            if (request.url.toString() == TEST_TOKEN_URL) {
                tokenRequestCount++
                respondTokens("""{"access_token":"renewed","expires_in":14400}""")
            } else {
                respond(content = "beyond_renewal", status = HttpStatusCode.Unauthorized)
            }
        }
        assertFailsWith<BeyondRenewalException> { service.request() }
        assertEquals(expected = 0, actual = tokenRequestCount)
    }

    @Test
    fun `a rate limited call waits as long as Retry-After asks`() = runTest {
        var requestCount = 0
        val service = TestSyncService {
            if (++requestCount == 1) {
                respond(content = "", status = HttpStatusCode.TooManyRequests, headers = headersOf(HttpHeaders.RetryAfter, "60"))
            } else {
                respondTokens("done")
            }
        }
        assertEquals(expected = "done", actual = service.call())
        assertTrue(currentTime in 60_000L..<60_500L, "Waited $currentTime ms rather than the 60 s that were asked for.")
    }

    @Test
    fun `a wait the body asks for is taken over Retry-After`() = runTest {
        var requestCount = 0
        val service = TestSyncService {
            if (++requestCount == 1) {
                respond(content = "wait=10", status = HttpStatusCode.TooManyRequests, headers = headersOf(HttpHeaders.RetryAfter, "60"))
            } else {
                respondTokens("done")
            }
        }
        service.call()
        assertTrue(currentTime in 10_000L..<10_500L, "Waited $currentTime ms rather than the 10 s the body asked for.")
    }

    @Test
    fun `an answer the service marks as asking to slow down is sent again`() = runTest {
        var requestCount = 0
        val service = TestSyncService {
            if (++requestCount <= 3) respond(content = "slow_down", status = HttpStatusCode.Conflict) else respondTokens("done")
        }
        assertEquals(expected = "done", actual = service.call())
        assertEquals(expected = 4, actual = requestCount)
    }

    @Test
    fun `any other answer is handed back at once`() = runTest {
        var requestCount = 0
        val service = TestSyncService {
            requestCount++
            respond(content = "conflict", status = HttpStatusCode.Conflict)
        }
        assertEquals(expected = HttpStatusCode.Conflict, actual = service.request().status)
        assertEquals(expected = 1, actual = requestCount)
        assertEquals(expected = 0L, actual = currentTime)
    }

    /** A service having trouble of its own says nothing about when it will be over, so the wait grows instead. */
    @Test
    fun `an unavailable service is waited for longer every time and handed back after six waits`() = runTest {
        var requestCount = 0
        val service = TestSyncService {
            requestCount++
            respond(content = "", status = HttpStatusCode.ServiceUnavailable)
        }
        assertEquals(expected = HttpStatusCode.ServiceUnavailable, actual = service.request().status)
        assertEquals(expected = 7, actual = requestCount)
        // 2 + 4 + 8 + 16 + 32 + 32 seconds, and less than a second of jitter on each.
        assertTrue(currentTime in 94_000L..<97_000L, "Waited $currentTime ms rather than about 94 s.")
    }

    @Test
    fun `a request a proxy gave up on is sent again`() = runTest {
        var requestCount = 0
        val service = TestSyncService {
            if (++requestCount == 1) respond(content = "", status = HttpStatusCode.RequestTimeout) else respondTokens("done")
        }
        assertEquals(expected = "done", actual = service.call())
    }

    /** Anything far past a service's own rate limits comes from something in between, and "syncing" must not sit for a day. */
    @Test
    fun `a wait longer than the cap ends the run instead of being waited`() = runTest {
        val service = TestSyncService {
            respond(content = "", status = HttpStatusCode.ServiceUnavailable, headers = headersOf(HttpHeaders.RetryAfter, "121"))
        }
        val exception = assertFailsWith<SyncNetworkException> { service.request() }
        assertEquals(expected = "Test service asked to wait 121 s.", actual = exception.message)
        assertTrue(currentTime < 1_000L, "Waited $currentTime ms before giving up.")
    }

    @Test
    fun `a wait at the cap is waited`() = runTest {
        var requestCount = 0
        val service = TestSyncService {
            if (++requestCount == 1) {
                respond(content = "wait=120", status = HttpStatusCode.ServiceUnavailable)
            } else {
                respondTokens("done")
            }
        }
        service.call()
        assertTrue(currentTime >= 120_000L, "Waited $currentTime ms rather than the 120 s that were asked for.")
    }

    @Test
    fun `a timeout on every attempt is tried seven times and is then the service not being reached`() = runTest {
        var attempts = 0
        val service = TestSyncService {
            attempts++
            throw SocketTimeoutException("Stalled")
        }
        assertIs<SocketTimeoutException>(assertFailsWith<SyncNetworkException> { service.request() }.cause)
        assertEquals(expected = 7, actual = attempts)
    }

    /** A device that is plainly offline hears so within seconds, not after the minute and a half a timeout is given. */
    @Test
    fun `a failure that is not a timeout is given up on after a few attempts`() = runTest {
        var attempts = 0
        val service = TestSyncService {
            attempts++
            throw IOException("Connection reset")
        }
        assertFailsWith<SyncNetworkException> { service.request() }
        assertEquals(expected = 4, actual = attempts)
        assertTrue(currentTime < 20_000L, "Waited $currentTime ms before giving up.")
    }

    /** A first sync is expected to be rate limited, and that must not use up the retries of a cell handover. */
    @Test
    fun `the waits asked for and the failures without an answer are counted apart`() = runTest {
        var requests = 0
        val service = TestSyncService {
            when (++requests) {
                in 1..3 -> throw IOException("Connection reset")
                in 4..9 -> respond(content = "", status = HttpStatusCode.TooManyRequests)
                else -> respondTokens("done")
            }
        }
        assertEquals(expected = "done", actual = service.call())
        assertEquals(expected = 10, actual = requests)
    }

    /** What the browser engine throws for a `fetch` that failed: a `kotlin.Error`, not an `Exception`. */
    @Test
    fun `a request the browser could not send is the service not being reached`() = runTest {
        val service = TestSyncService { throw Error("Fail to fetch") }
        assertFailsWith<SyncNetworkException> { service.request() }
    }

    @Test
    fun `a cancellation nobody asked for is the service not being reached`() = runTest {
        val service = TestSyncService { throw CancellationException("The engine gave up.") }
        assertFailsWith<SyncNetworkException> { service.request() }
    }

    @Test
    fun `a request that is cancelled stays a cancellation`() = runTest {
        val hasStarted = CompletableDeferred<Unit>()
        val service = TestSyncService {
            hasStarted.complete(Unit)
            awaitCancellation()
        }
        var failure: Throwable? = null
        val job = launch { failure = runCatching { service.request() }.exceptionOrNull() }
        hasStarted.await()
        job.cancelAndJoin()
        assertIs<CancellationException>(failure)
    }

    @Test
    fun `an answer that cannot be decoded is the network failing`() {
        val exception = assertFailsWith<SyncNetworkException> {
            decodeSyncAnswer("Test service", OAuthTokenResponse.serializer(), "<html>Portal</html>")
        }
        assertEquals(expected = "Test service's answer could not be read.", actual = exception.message)
    }

    @Test
    fun `an answer of the service's passes through the transport as it is`() = runTest {
        assertFailsWith<SyncApiException> { syncTransport("Test service") { throw SyncApiException("Refused") } }
    }
}
