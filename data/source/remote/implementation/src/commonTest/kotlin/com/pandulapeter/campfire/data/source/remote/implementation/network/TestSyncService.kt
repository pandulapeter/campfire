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

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.source.local.api.SyncCredentialsLocalSource
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.implementation.auth.OAuthTokens
import com.pandulapeter.campfire.data.source.remote.implementation.auth.StoredSyncCredentials
import com.pandulapeter.campfire.data.source.remote.implementation.auth.SyncCredentialsStore
import com.pandulapeter.campfire.data.source.remote.implementation.auth.connectedCredentials
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * An OAuth service of no provider's, reached through [OAuthTokens] and [SyncHttpTransport] the way a provider wires
 * them, over a [MockEngine] answering with [handler]. The stored connection is the one [connectedCredentials] writes.
 */
internal class TestSyncService(
    configure: HttpClientConfig<*>.() -> Unit = {},
    storage: SyncCredentialsLocalSource = StoredSyncCredentials(connectedCredentials()),
    engineDispatcher: CoroutineDispatcher? = null,
    clientSecret: String? = null,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    private val httpClientHolder = HttpClientHolder(dispatcher = engineDispatcher ?: Dispatchers.Default) {
        val engine = MockEngineConfig().apply {
            if (engineDispatcher != null) dispatcher = engineDispatcher
            addHandler(handler)
        }
        HttpClient(MockEngine(engine), configure)
    }

    val tokens = OAuthTokens(
        httpClientHolder = httpClientHolder,
        credentialsStore = SyncCredentialsStore(storage, Logger.Standard),
        providerId = SyncProviderId.DROPBOX,
        tokenUrl = TEST_TOKEN_URL,
        clientId = TEST_CLIENT_ID,
        clientSecret = clientSecret,
        errors = TestErrors,
    )

    val transport = SyncHttpTransport(tokens = tokens, errors = TestErrors)

    /** One call through [SyncHttpTransport.request], answered as it came back from the retries. */
    suspend fun request(): HttpResponse = transport.request { accessToken ->
        httpClientHolder.client().post(TEST_API_URL) { header("Authorization", "Bearer $accessToken") }
    }

    /** One call whose answer is read the way a provider reads it: a 401 is a refusal, any other failure the network. */
    suspend fun call(): String {
        val response = request()
        if (response.status == HttpStatusCode.Unauthorized) throw SyncAuthorizationException("Refused: ${response.bodyAsText()}")
        if (!response.status.isSuccess()) throw SyncNetworkException("Answered ${response.status.value}")
        return response.bodyAsText()
    }
}

/** A 401 that a renewal cannot cure, as [TestErrors] reads it. */
internal class BeyondRenewalException : Exception("No renewal helps.")

/**
 * The service's own words: a body of `beyond_renewal` is a refusal no renewal cures, a 409 of `slow_down` asks to be
 * sent again, `wait=<seconds>` is a requested wait, and the body is its own summary.
 */
internal object TestErrors : SyncHttpErrorMapper {

    override val serviceName = "Test service"

    override suspend fun refusalBeyondRenewal(response: HttpResponse) =
        if (response.bodyAsText() == "beyond_renewal") BeyondRenewalException() else null

    override suspend fun asksToSlowDown(response: HttpResponse) =
        response.status == HttpStatusCode.Conflict && response.bodyAsText().startsWith("slow_down")

    override suspend fun requestedWaitSeconds(response: HttpResponse) =
        response.bodyAsText().substringAfter("wait=", missingDelimiterValue = "").toLongOrNull()

    override suspend fun summaryOf(response: HttpResponse) = response.bodyAsText()
}

internal fun MockRequestHandleScope.respondTokens(content: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(
    content = content,
    status = status,
    headers = headersOf("Content-Type", "application/json"),
)

internal const val TEST_TOKEN_URL = "https://auth.example.com/token"
internal const val TEST_API_URL = "https://api.example.com/call"
internal const val TEST_CLIENT_ID = "test-client"
