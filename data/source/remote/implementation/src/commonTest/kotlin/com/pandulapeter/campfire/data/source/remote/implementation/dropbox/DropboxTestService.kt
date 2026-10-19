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

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.source.local.api.SyncCredentialsLocalSource
import com.pandulapeter.campfire.data.source.remote.implementation.auth.StoredSyncCredentials
import com.pandulapeter.campfire.data.source.remote.implementation.auth.SyncCredentialsStore
import com.pandulapeter.campfire.data.source.remote.implementation.auth.connectedCredentials
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * A [DropboxSyncProvider] whose service is [handler], connected through [storage].
 *
 * @param engineDispatcher Where the engine runs and the client is built, and with them the client's request timeout: a
 *   test whose bound has to expire passes the test's own dispatcher, or the bound is waited in real time, and so does
 *   one whose request runs under a virtual time bound, which would otherwise expire before the request was answered.
 */
internal fun dropboxProvider(
    configure: HttpClientConfig<*>.() -> Unit = {},
    storage: SyncCredentialsLocalSource = StoredSyncCredentials(connectedCredentials()),
    engineDispatcher: CoroutineDispatcher? = null,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) = DropboxSyncProvider(
    httpClientHolder = HttpClientHolder(dispatcher = engineDispatcher ?: Dispatchers.Default) {
        val engine = MockEngineConfig().apply {
            if (engineDispatcher != null) dispatcher = engineDispatcher
            addHandler(handler)
        }
        HttpClient(MockEngine(engine), configure)
    },
    credentialsStore = SyncCredentialsStore(storage, Logger.Standard),
    appKey = "test-app-key",
    logger = Logger.Standard,
)

internal fun MockRequestHandleScope.respondJson(content: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(
    content = content,
    status = status,
    headers = headersOf("Content-Type", "application/json"),
)

/** An empty last page of a listing, the answer of a request whose own content does not matter. */
internal const val EMPTY_LISTING = """{"entries":[],"cursor":"","has_more":false}"""

internal const val TOKEN_URL = "https://api.dropboxapi.com/oauth2/token"
