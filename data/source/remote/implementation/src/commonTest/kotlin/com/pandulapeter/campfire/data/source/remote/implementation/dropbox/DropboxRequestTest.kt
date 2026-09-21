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

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.model.domain.SyncAccount
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.source.local.api.SyncStateLocalSource
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.SyncRemoteStorageFullException
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteWriteResult
import com.pandulapeter.campfire.data.source.remote.implementation.auth.SyncCredentialsStore
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How the provider answers what Dropbox says about pace. The service is a [MockEngine] and the waiting happens in
 * virtual time, so a test of five retries takes no longer than a test of none.
 */
class DropboxRequestTest {

    /** Dropbox's answer to writes landing in one folder at once, which the engine's own concurrency provokes. */
    @Test
    fun `retries a write that was refused for too many write operations`() = runTest {
        val requests = mutableListOf<String>()
        val provider = provider { request ->
            requests += request.url.toString()
            if (requests.size <= 3) {
                respondJson("""{"error_summary":"path/too_many_write_operations/...","error":{}}""", HttpStatusCode.Conflict)
            } else {
                respondJson("""{"name":"song.cho","rev":"0123456789abcdef"}""")
            }
        }
        assertEquals(
            expected = RemoteWriteResult.Written("0123456789abcdef"),
            actual = provider.upload(LibraryFileKind.SONG, "song.cho", byteArrayOf(1, 2, 3), expectedRevision = null),
        )
        assertEquals(expected = 4, actual = requests.size)
    }

    @Test
    fun `waits as long as the body of a rate limited answer asks`() = runTest {
        var requestCount = 0
        val provider = provider {
            requestCount++
            if (requestCount == 1) {
                respondJson(
                    """{"error_summary":"too_many_requests/...","error":{"reason":{".tag":"too_many_requests"},"retry_after":10}}""",
                    HttpStatusCode.TooManyRequests,
                )
            } else {
                respondJson("""{"entries":[],"cursor":"","has_more":false}""")
            }
        }
        provider.list()
        assertTrue(currentTime >= 10_000L, "Waited $currentTime ms rather than the 10 s that were asked for.")
    }

    /** A service having trouble of its own says nothing about when it will be over, so the wait grows instead. */
    @Test
    fun `waits longer every time the service is unavailable without saying for how long`() = runTest {
        var requestCount = 0
        val provider = provider {
            requestCount++
            if (requestCount <= 6) {
                respond(content = "", status = HttpStatusCode.ServiceUnavailable)
            } else {
                respondJson("""{"entries":[],"cursor":"","has_more":false}""")
            }
        }
        provider.list()
        // 2 + 4 + 8 + 16 + 32 + 32 seconds, and less than a second of jitter on each.
        assertTrue(currentTime in 94_000L..<97_000L, "Waited $currentTime ms rather than about 94 s.")
    }

    @Test
    fun `gives up on a service that stays unavailable`() = runTest {
        val provider = provider { respond(content = "", status = HttpStatusCode.ServiceUnavailable) }
        assertFailsWith<SyncNetworkException> { provider.list() }
    }

    @Test
    fun `an upload whose receipt cannot be read is one that may have landed`() = runTest {
        val provider = provider { respond(content = "<html>", status = HttpStatusCode.OK) }
        assertFailsWith<SyncNetworkException> { provider.upload(LibraryFileKind.SONG, "song.cho", ByteArray(1), null) }
    }

    @Test
    fun `reports a full account as that rather than as one refused file`() = runTest {
        val provider = provider { respondJson("""{"error_summary":"path/insufficient_space/..","error":{}}""", HttpStatusCode.Conflict) }
        assertFailsWith<SyncRemoteStorageFullException> { provider.upload(LibraryFileKind.SONG, "song.cho", ByteArray(1), null) }
    }

    @Test
    fun `reports a refused name as the refusal of that one file`() = runTest {
        val provider = provider { respondJson("""{"error_summary":"path/malformed_path/..","error":{}}""", HttpStatusCode.Conflict) }
        assertFailsWith<DropboxApiException> { provider.upload(LibraryFileKind.SONG, "song.cho", ByteArray(1), null) }
    }

    /** A stopped run resumes every request in flight with a cancellation, and the engine has to be told that it is one. */
    @Test
    fun `a request that is cancelled stays a cancellation`() = runTest {
        val hasStarted = CompletableDeferred<Unit>()
        val provider = provider {
            hasStarted.complete(Unit)
            awaitCancellation()
        }
        var failure: Throwable? = null
        val job = launch { failure = runCatching { provider.list() }.exceptionOrNull() }
        hasStarted.await()
        job.cancelAndJoin()
        assertIs<CancellationException>(failure)
    }

    /** What the client does with its own request timeout: it cancels the call, and reports the reason instead. */
    @Test
    fun `a request that times out is the service not being reached`() = runTest {
        val provider = provider(
            configure = { install(HttpTimeout) { requestTimeoutMillis = 50 } },
        ) { awaitCancellation() }
        val exception = assertFailsWith<SyncNetworkException> { provider.list() }
        assertIs<HttpRequestTimeoutException>(exception.cause)
    }

    @Test
    fun `a cancellation nobody asked for is the service not being reached`() = runTest {
        val provider = provider { throw CancellationException("The engine gave up.") }
        assertFailsWith<SyncNetworkException> { provider.list() }
    }

    @Test
    fun `the stored account is answered without a request`() = runTest {
        val provider = provider(
            storage = ConnectedStorage(names = ""","displayName":"Jane","email":"jane@example.com""""),
        ) { error("No request was expected.") }
        assertEquals(
            expected = SyncAccount(SyncProviderId.DROPBOX, id = "", displayName = "Jane", email = "jane@example.com"),
            actual = provider.storedAccount(),
        )
    }

    @Test
    fun `a stored account whose name was never read goes by its id`() = runTest {
        val provider = provider(storage = ConnectedStorage(names = ""","accountId":"dbid:1"""")) {
            error("No request was expected.")
        }
        assertEquals(
            expected = SyncAccount(SyncProviderId.DROPBOX, id = "dbid:1", displayName = "dbid:1", email = null),
            actual = provider.storedAccount(),
        )
    }

    @Test
    fun `a connection nothing was stored about has no stored account`() = runTest {
        val provider = provider { error("No request was expected.") }
        assertNull(provider.storedAccount())
    }

    /** A token the device's clock still believes in can be one Dropbox has stopped accepting. */
    @Test
    fun `refreshes the token once when dropbox refuses it`() = runTest {
        val authorizations = mutableListOf<String?>()
        var tokenRequestCount = 0
        val provider = provider { request ->
            if (request.url.toString() == TOKEN_URL) {
                tokenRequestCount++
                respondJson("""{"access_token":"renewed","expires_in":14400}""")
            } else {
                authorizations += request.headers["Authorization"]
                if (authorizations.size == 1) {
                    respondJson("""{"error_summary":"expired_access_token/..."}""", HttpStatusCode.Unauthorized)
                } else {
                    respondJson("""{"entries":[],"cursor":"","has_more":false}""")
                }
            }
        }
        provider.list()
        assertEquals(expected = 1, actual = tokenRequestCount)
        assertEquals(expected = listOf<String?>("Bearer access", "Bearer renewed"), actual = authorizations)
    }

    @Test
    fun `believes a refusal of a token it has just refreshed`() = runTest {
        var tokenRequestCount = 0
        val provider = provider { request ->
            if (request.url.toString() == TOKEN_URL) {
                tokenRequestCount++
                respondJson("""{"access_token":"renewed","expires_in":14400}""")
            } else {
                respondJson("""{"error_summary":"invalid_access_token/..."}""", HttpStatusCode.Unauthorized)
            }
        }
        assertFailsWith<SyncAuthorizationException> { provider.list() }
        assertEquals(expected = 1, actual = tokenRequestCount)
    }

    @Test
    fun `reports why the token endpoint refused a refresh`() = runTest {
        val provider = provider { request ->
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

    private fun provider(
        configure: HttpClientConfig<*>.() -> Unit = {},
        storage: SyncStateLocalSource = ConnectedStorage(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = DropboxSyncProvider(
        httpClient = HttpClient(MockEngine(handler), configure),
        credentialsStore = SyncCredentialsStore(storage),
        appKey = APP_KEY,
    )

    private fun MockRequestHandleScope.respondJson(content: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(
        content = content,
        status = status,
        headers = headersOf("Content-Type", "application/json"),
    )

    /** A connection whose access token is good for as long as any test runs, so no request needs a refresh. */
    private class ConnectedStorage(private val names: String = "") : SyncStateLocalSource {
        override suspend fun loadSyncCredentials() =
            """{"providerId":"dropbox","accessToken":"access","refreshToken":"refresh","expiresAt":${Long.MAX_VALUE}$names}"""

        override suspend fun saveSyncCredentials(document: String?) = Unit
        override suspend fun loadSyncIndex(): String? = null
        override suspend fun saveSyncIndex(document: String?) = Unit
    }

    private companion object {
        const val APP_KEY = "test-app-key"
        const val TOKEN_URL = "https://api.dropboxapi.com/oauth2/token"
    }
}
