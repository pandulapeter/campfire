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

import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.model.sync.SyncAccount
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncRemoteStorageFullException
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteAuthorizationResponse
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteWriteResult
import com.pandulapeter.campfire.data.source.remote.implementation.auth.StoredSyncCredentials
import com.pandulapeter.campfire.data.source.remote.implementation.auth.connectedCredentials
import com.pandulapeter.campfire.data.source.remote.implementation.network.configureClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * How the provider answers what Dropbox says about pace. The service is a [MockEngine] and the waiting happens in
 * virtual time, so a test of five retries takes no longer than a test of none.
 */
class DropboxRequestTest {

    /** Dropbox's answer to writes landing in one folder at once, which the engine's own concurrency provokes. */
    @Test
    fun `retries a write that was refused for too many write operations`() = runTest {
        var requestCount = 0
        val provider = dropboxProvider {
            if (++requestCount <= 3) {
                respondJson("""{"error_summary":"path/too_many_write_operations/...","error":{}}""", HttpStatusCode.Conflict)
            } else {
                respondJson("""{"name":"song.cho","rev":"0123456789abcdef"}""")
            }
        }
        assertEquals(
            expected = RemoteWriteResult.Written("0123456789abcdef"),
            actual = provider.upload(LibraryFileKind.SONG, "song.cho", byteArrayOf(1, 2, 3), expectedRevision = null),
        )
    }

    @Test
    fun `waits as long as the body of a rate limited answer asks`() = runTest {
        var requestCount = 0
        val provider = dropboxProvider {
            requestCount++
            if (requestCount == 1) {
                respondJson(
                    """{"error_summary":"too_many_requests/...","error":{"reason":{".tag":"too_many_requests"},"retry_after":10}}""",
                    HttpStatusCode.TooManyRequests,
                )
            } else {
                respondJson(EMPTY_LISTING)
            }
        }
        provider.list()
        assertTrue(currentTime >= 10_000L, "Waited $currentTime ms rather than the 10 s that were asked for.")
    }

    /** A service having trouble of its own says nothing about when it will be over, so the wait grows instead. */
    @Test
    fun `waits longer every time the service is unavailable without saying for how long`() = runTest {
        var requestCount = 0
        val provider = dropboxProvider {
            requestCount++
            if (requestCount <= 6) {
                respond(content = "", status = HttpStatusCode.ServiceUnavailable)
            } else {
                respondJson(EMPTY_LISTING)
            }
        }
        provider.list()
        // 2 + 4 + 8 + 16 + 32 + 32 seconds, and less than a second of jitter on each.
        assertTrue(currentTime in 94_000L..<97_000L, "Waited $currentTime ms rather than about 94 s.")
    }

    /** Anything far past Dropbox's own rate limits comes from something in between, and "syncing" must not sit for a day. */
    @Test
    fun `a wait longer than the cap ends the run instead of being waited`() = runTest {
        val provider = dropboxProvider {
            respond(content = "", status = HttpStatusCode.ServiceUnavailable, headers = headersOf(HttpHeaders.RetryAfter, "86400"))
        }
        assertFailsWith<SyncNetworkException> { provider.list() }
        assertTrue(currentTime < 1_000L, "Waited $currentTime ms before giving up.")
        val rateLimitedProvider = dropboxProvider {
            respondJson("""{"error_summary":"too_many_requests/...","error":{"retry_after":86400}}""", HttpStatusCode.TooManyRequests)
        }
        assertFailsWith<SyncNetworkException> { rateLimitedProvider.list() }
        assertTrue(currentTime < 1_000L, "Waited $currentTime ms before giving up.")
    }

    @Test
    fun `a wait under the cap is waited`() = runTest {
        var requestCount = 0
        val provider = dropboxProvider {
            if (++requestCount == 1) {
                respond(content = "", status = HttpStatusCode.ServiceUnavailable, headers = headersOf(HttpHeaders.RetryAfter, "60"))
            } else {
                respondJson(EMPTY_LISTING)
            }
        }
        provider.list()
        assertTrue(currentTime >= 60_000L, "Waited $currentTime ms rather than the 60 s that were asked for.")
    }

    /** What a proxy or a load balancer answers for a request it gave up on. */
    @Test
    fun `a request a proxy gave up on is sent again`() = runTest {
        var requestCount = 0
        val provider = dropboxProvider {
            if (++requestCount == 1) respond(content = "", status = HttpStatusCode.RequestTimeout) else respondJson(EMPTY_LISTING)
        }
        assertEquals(expected = emptyList(), actual = provider.list().files)
    }

    @Test
    fun `a request a proxy keeps giving up on is the service not being reached`() = runTest {
        val provider = dropboxProvider { respond(content = "", status = HttpStatusCode.RequestTimeout) }
        assertFailsWith<SyncNetworkException> { provider.list() }
    }

    @Test
    fun `gives up on a service that stays unavailable`() = runTest {
        val provider = dropboxProvider { respond(content = "", status = HttpStatusCode.ServiceUnavailable) }
        assertFailsWith<SyncNetworkException> { provider.list() }
    }

    @Test
    fun `an upload whose receipt cannot be read is one that may have landed`() = runTest {
        val provider = dropboxProvider { respond(content = "<html>", status = HttpStatusCode.OK) }
        assertFailsWith<SyncNetworkException> { provider.upload(LibraryFileKind.SONG, "song.cho", ByteArray(1), null) }
    }

    @Test
    fun `reports a full account as that rather than as one refused file`() = runTest {
        val provider = dropboxProvider { respondJson("""{"error_summary":"path/insufficient_space/..","error":{}}""", HttpStatusCode.Conflict) }
        assertFailsWith<SyncRemoteStorageFullException> { provider.upload(LibraryFileKind.SONG, "song.cho", ByteArray(1), null) }
    }

    @Test
    fun `reports a refused name as the refusal of that one file`() = runTest {
        val provider = dropboxProvider { respondJson("""{"error_summary":"path/malformed_path/..","error":{}}""", HttpStatusCode.Conflict) }
        assertFailsWith<DropboxApiException> { provider.upload(LibraryFileKind.SONG, "song.cho", ByteArray(1), null) }
    }

    /** A stopped run resumes every request in flight with a cancellation, and the engine has to be told that it is one. */
    @Test
    fun `a request that is cancelled stays a cancellation`() = runTest {
        val hasStarted = CompletableDeferred<Unit>()
        val provider = dropboxProvider {
            hasStarted.complete(Unit)
            awaitCancellation()
        }
        var failure: Throwable? = null
        val job = launch { failure = runCatching { provider.list() }.exceptionOrNull() }
        hasStarted.await()
        job.cancelAndJoin()
        assertIs<CancellationException>(failure)
    }

    /**
     * What the client does with its own request timeout: it cancels the call, and reports the reason instead. Retried
     * like any other timeout, and the service not being reached once the attempts run out.
     */
    @Test
    fun `a request that keeps timing out is the service not being reached`() = runTest {
        val provider = dropboxProvider(
            configure = { install(HttpTimeout) { requestTimeoutMillis = 50 } },
        ) { awaitCancellation() }
        val exception = assertFailsWith<SyncNetworkException> { provider.list() }
        assertIs<HttpRequestTimeoutException>(exception.cause)
    }

    @Test
    fun `a cancellation nobody asked for is the service not being reached`() = runTest {
        val provider = dropboxProvider { throw CancellationException("The engine gave up.") }
        assertFailsWith<SyncNetworkException> { provider.list() }
    }

    /** What the browser engine throws for a `fetch` that failed: a `kotlin.Error`, not an `Exception`. */
    @Test
    fun `a request the browser could not send is the service not being reached`() = runTest {
        val provider = dropboxProvider { throw Error("Fail to fetch") }
        assertFailsWith<SyncNetworkException> { provider.list() }
        assertFailsWith<SyncNetworkException> { provider.download(LibraryFileKind.SONG, "song.cho") }
        assertFailsWith<SyncNetworkException> { provider.upload(LibraryFileKind.SONG, "song.cho", ByteArray(1), null) }
    }

    @Test
    fun `disconnecting while the browser cannot send the revocation still disconnects`() = runTest {
        val storage = StoredSyncCredentials(connectedCredentials())
        val provider = dropboxProvider(storage = storage) { request ->
            if (request.url.toString() == REVOKE_URL) throw Error("Fail to fetch")
            error("No other request was expected.")
        }
        provider.disconnect()
        assertNull(storage.credentials)
    }

    @Test
    fun `the stored account is answered without a request`() = runTest {
        val provider = dropboxProvider(
            storage = StoredSyncCredentials(connectedCredentials(extraFields = ""","displayName":"Jane","email":"jane@example.com"""")),
        ) { error("No request was expected.") }
        assertEquals(
            expected = SyncAccount(SyncProviderId.DROPBOX, id = "", displayName = "Jane", email = "jane@example.com"),
            actual = provider.storedAccount(),
        )
    }

    @Test
    fun `a stored account whose name was never read goes by its id`() = runTest {
        val provider = dropboxProvider(storage = StoredSyncCredentials(connectedCredentials(extraFields = ""","accountId":"dbid:1""""))) {
            error("No request was expected.")
        }
        assertEquals(
            expected = SyncAccount(SyncProviderId.DROPBOX, id = "dbid:1", displayName = "dbid:1", email = null),
            actual = provider.storedAccount(),
        )
    }

    @Test
    fun `a connection nothing was stored about has no stored account`() = runTest {
        val provider = dropboxProvider { error("No request was expected.") }
        assertNull(provider.storedAccount())
    }

    /** Only a refusal means the credentials are no longer good for anything; the account is then no account. */
    @Test
    fun `an account read the service refuses is no account`() = runTest {
        val provider = dropboxProvider(storage = StoredSyncCredentials(connectedCredentials(extraFields = STORED_NAME))) { request ->
            if (request.url.toString() == TOKEN_URL) {
                respondJson("""{"access_token":"renewed","expires_in":14400}""")
            } else {
                respondJson("""{"error_summary":"invalid_access_token/..."}""", HttpStatusCode.Unauthorized)
            }
        }
        assertNull(provider.loadAccount())
    }

    @Test
    fun `an account read that cannot reach the service is the account stored last time`() = runTest {
        val provider = dropboxProvider(storage = StoredSyncCredentials(connectedCredentials(extraFields = STORED_NAME))) {
            throw Error("Fail to fetch")
        }
        assertEquals(
            expected = SyncAccount(SyncProviderId.DROPBOX, id = "dbid:1", displayName = "Jane", email = "jane@example.com"),
            actual = provider.loadAccount(),
        )
    }

    @Test
    fun `an account read stores the name it got`() = runTest {
        val storage = StoredSyncCredentials(connectedCredentials())
        val provider = dropboxProvider(storage = storage) {
            respondJson("""{"account_id":"dbid:2","name":{"display_name":"Joe"},"email":"joe@example.com"}""")
        }
        val account = SyncAccount(SyncProviderId.DROPBOX, id = "dbid:2", displayName = "Joe", email = "joe@example.com")
        assertEquals(expected = account, actual = provider.loadAccount())
        assertEquals(expected = account, actual = provider.storedAccount())
    }

    /** Without a refresh token the connection would end in four hours with no way to renew it. */
    @Test
    fun `an authorization answered without a refresh token is refused and stores nothing`() = runTest {
        val storage = StoredSyncCredentials()
        val provider = dropboxProvider(storage = storage) { request ->
            if (request.url.toString() == TOKEN_URL) {
                respondJson("""{"access_token":"access","expires_in":14400,"account_id":"dbid:1"}""")
            } else {
                error("No request but the token exchange was expected.")
            }
        }
        assertFailsWith<SyncAuthorizationException> {
            provider.completeAuthorization(RemoteAuthorizationResponse(code = "code", state = "state"), verifier = "verifier", redirectUri = null)
        }
        assertNull(storage.credentials)
    }

    /** A cell handover or a tunnel, which the next attempt gets through. */
    @Test
    fun `a socket timeout is retried`() = runTest {
        var attempts = 0
        val provider = dropboxProvider {
            if (++attempts <= 2) throw SocketTimeoutException("Stalled")
            respondJson("""{"entries":[{".tag":"file","name":"song.cho","path_lower":"/songs/song.cho","rev":"r1"}],"cursor":"","has_more":false}""")
        }
        assertEquals(expected = listOf("song.cho"), actual = provider.list().files.map { it.name })
    }

    @Test
    fun `a socket timeout on every attempt is the service not being reached`() = runTest {
        var attempts = 0
        val provider = dropboxProvider {
            attempts++
            throw SocketTimeoutException("Stalled")
        }
        assertIs<SocketTimeoutException>(assertFailsWith<SyncNetworkException> { provider.list() }.cause)
        assertEquals(7, attempts)
    }

    /** What a cell handover shows up as at least as often as a timeout. */
    @Test
    fun `a connection reset is retried`() = runTest {
        var attempts = 0
        val provider = dropboxProvider {
            if (++attempts <= 2) throw IOException("Connection reset")
            respondJson("""{"entries":[{".tag":"file","name":"song.cho","path_lower":"/songs/song.cho","rev":"r1"}],"cursor":"","has_more":false}""")
        }
        assertEquals(expected = listOf("song.cho"), actual = provider.list().files.map { it.name })
    }

    /** A device that is plainly offline hears so within seconds, not after the minute and a half a timeout is given. */
    @Test
    fun `a failure that is not a timeout is given up on after a few attempts`() = runTest {
        var attempts = 0
        val provider = dropboxProvider {
            attempts++
            throw Error("Fail to fetch")
        }
        assertFailsWith<SyncNetworkException> { provider.list() }
        assertEquals(4, attempts)
        assertTrue(currentTime < 20_000L, "Waited $currentTime ms before giving up.")
    }

    @Test
    fun `an upload whose first attempt was cut short is sent again`() = runTest {
        var attempts = 0
        val provider = dropboxProvider {
            if (++attempts == 1) throw IOException("unexpected end of stream")
            respondJson("""{"name":"song.cho","rev":"0123456789abcdef"}""")
        }
        assertEquals(
            expected = RemoteWriteResult.Written("0123456789abcdef"),
            actual = provider.upload(LibraryFileKind.SONG, "song.cho", byteArrayOf(1, 2, 3), expectedRevision = null),
        )
    }

    /** `fetch` has no timeout between packets, so in the browser each call is bounded by what it carries. */
    @Test
    fun `each Dropbox call on the browser carries its own bound`() = runTest {
        val uploadSize = 3 * 16_384
        assertEquals(
            expected = mapOf(
                "get_current_account" to 60_000L,
                "list_folder" to 120_000L,
                "download" to 120_000L,
                "upload" to 63_000L,
            ),
            actual = recordedBounds(hasSocketTimeout = false, uploadSize = uploadSize),
        )
        assertEquals(
            expected = setOf(HttpTimeoutConfig.INFINITE_TIMEOUT_MS),
            actual = recordedBounds(hasSocketTimeout = true, uploadSize = uploadSize).values.toSet(),
        )
    }

    @Test
    fun `a stalled call on the browser fails within its own bound`() = runTest {
        val provider = dropboxProvider(
            configure = { configureClient(sendsUserAgent = false, hasSocketTimeout = false) },
            storage = StoredSyncCredentials(connectedCredentials(extraFields = STORED_NAME)),
            engineDispatcher = StandardTestDispatcher(testScheduler),
        ) { awaitCancellation() }
        assertEquals(
            expected = SyncAccount(SyncProviderId.DROPBOX, id = "dbid:1", displayName = "Jane", email = "jane@example.com"),
            actual = provider.loadAccount(),
        )
        assertTrue(currentTime < 10.minutes.inWholeMilliseconds, "The account read took $currentTime ms to fail.")
        val listingStart = currentTime
        assertFailsWith<SyncNetworkException> { provider.list() }
        assertTrue(currentTime - listingStart < 17.minutes.inWholeMilliseconds, "The listing took ${currentTime - listingStart} ms to fail.")
    }

    /** A native engine bounds the silence between packets instead, so a slow transfer may take as long as it needs. */
    @Test
    fun `a slow call on a native engine is not cut short`() = runTest {
        val provider = dropboxProvider(
            configure = { configureClient(sendsUserAgent = false, hasSocketTimeout = true) },
            engineDispatcher = StandardTestDispatcher(testScheduler),
        ) {
            delay(5.minutes)
            respondJson(EMPTY_LISTING)
        }
        assertEquals(expected = emptyList(), actual = provider.list().files)
    }

    /** The request bound each kind of call carried, by the last segment of its address. */
    private suspend fun recordedBounds(hasSocketTimeout: Boolean, uploadSize: Int): Map<String, Long?> {
        val bounds = mutableMapOf<String, Long?>()
        val provider = dropboxProvider(configure = { configureClient(sendsUserAgent = false, hasSocketTimeout = hasSocketTimeout) }) { request ->
            val name = request.url.encodedPath.substringAfterLast('/')
            bounds[name] = request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis
            when (name) {
                "get_current_account" -> respondJson("""{"account_id":"dbid:2","name":{"display_name":"Joe"}}""")
                "download" -> respond(content = "song", status = HttpStatusCode.OK, headers = headersOf("Dropbox-API-Result", """{"rev":"r7"}"""))
                "upload" -> respondJson("""{"name":"song.cho","rev":"0123456789abcdef"}""")
                else -> respondJson(EMPTY_LISTING)
            }
        }
        provider.loadAccount()
        provider.list()
        provider.download(LibraryFileKind.SONG, "song.cho")
        provider.upload(LibraryFileKind.SONG, "song.cho", ByteArray(uploadSize), expectedRevision = null)
        return bounds
    }

    private companion object {
        const val REVOKE_URL = "https://api.dropboxapi.com/2/auth/token/revoke"
        const val STORED_NAME = ""","accountId":"dbid:1","displayName":"Jane","email":"jane@example.com""""
    }
}
