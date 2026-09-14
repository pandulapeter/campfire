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
import com.pandulapeter.campfire.data.source.local.api.SyncStateLocalSource
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteWriteResult
import com.pandulapeter.campfire.data.source.remote.implementation.auth.SyncCredentialsStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private fun provider(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = DropboxSyncProvider(
        httpClient = HttpClient(MockEngine(handler)),
        credentialsStore = SyncCredentialsStore(ConnectedStorage),
        appKey = APP_KEY,
    )

    private fun MockRequestHandleScope.respondJson(content: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(
        content = content,
        status = status,
        headers = headersOf("Content-Type", "application/json"),
    )

    /** A connection whose access token is good for as long as any test runs, so no request needs a refresh. */
    private object ConnectedStorage : SyncStateLocalSource {
        override suspend fun loadSyncCredentials() =
            """{"providerId":"dropbox","accessToken":"access","refreshToken":"refresh","expiresAt":${Long.MAX_VALUE}}"""

        override suspend fun saveSyncCredentials(document: String?) = Unit
        override suspend fun loadSyncIndex(): String? = null
        override suspend fun saveSyncIndex(document: String?) = Unit
    }

    private companion object {
        const val APP_KEY = "test-app-key"
    }
}
