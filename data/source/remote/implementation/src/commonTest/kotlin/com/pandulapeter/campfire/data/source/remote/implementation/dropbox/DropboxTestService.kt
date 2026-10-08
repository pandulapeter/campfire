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

import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.source.local.api.SyncCredentialsLocalSource
import com.pandulapeter.campfire.data.source.remote.implementation.auth.SyncCredentialsDocument
import com.pandulapeter.campfire.data.source.remote.implementation.auth.SyncCredentialsStore
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/** A [DropboxSyncProvider] whose service is [handler], connected through [storage]. */
internal fun dropboxProvider(
    configure: HttpClientConfig<*>.() -> Unit = {},
    storage: SyncCredentialsLocalSource = ConnectedStorage(),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) = DropboxSyncProvider(
    httpClientHolder = HttpClientHolder { HttpClient(MockEngine(handler), configure) },
    credentialsStore = SyncCredentialsStore(storage, Logger.Standard),
    appKey = "test-app-key",
    logger = Logger.Standard,
)

internal fun MockRequestHandleScope.respondJson(content: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(
    content = content,
    status = status,
    headers = headersOf("Content-Type", "application/json"),
)

/**
 * A connection whose access token is good for as long as any test runs unless [expiresAt] says otherwise, so no
 * request needs a refresh. [credentials] is what the provider last stored.
 */
internal class ConnectedStorage(names: String = "", expiresAt: Long = Long.MAX_VALUE) : SyncCredentialsLocalSource {
    var credentials: String? =
        """{"providerId":"dropbox","accessToken":"access","refreshToken":"refresh","expiresAt":$expiresAt$names}"""

    /** [credentials] read back the way the provider reads them. */
    val document get() = credentials?.let { json.decodeFromString<SyncCredentialsDocument>(it) }

    override suspend fun loadSyncCredentials() = credentials

    override suspend fun saveSyncCredentials(document: String?) {
        credentials = document
    }
}

/** An empty last page of a listing, the answer of a request whose own content does not matter. */
internal const val EMPTY_LISTING = """{"entries":[],"cursor":"","has_more":false}"""

internal const val TOKEN_URL = "https://api.dropboxapi.com/oauth2/token"
