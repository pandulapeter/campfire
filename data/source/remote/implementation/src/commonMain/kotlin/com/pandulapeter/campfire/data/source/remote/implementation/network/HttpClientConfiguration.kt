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

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.timeout
import io.ktor.util.AttributeKey

/**
 * The settings every engine shares. Redirects are followed (the token endpoints do, and so does the Cover Art Archive,
 * twice).
 *
 * A stalled connection has to end in a failure the user can retry rather than in a sync that never finishes, but a
 * slow one has to be allowed to finish: a `list_folder` page of a few thousand songs, or one long song, takes minutes
 * on a 2G or congested link while data is arriving the whole time. So what is bounded is the time to connect and the
 * silence between two packets, and not the exchange as a whole.
 *
 * @param sendsUserAgent Whether every request names Campfire in its `User-Agent`, see [USER_AGENT]. False only in the
 *   browser, where the header is one a script is not allowed to set: `fetch` either drops it or, where it does send
 *   it, makes every request to a host that does not expect it a CORS preflight that host may well refuse.
 * @param hasSocketTimeout False in the browser, where `fetch` offers no timeout between packets and a dead connection
 *   could only be told apart from a slow one by a bound on the whole exchange, which is therefore kept there, at
 *   [BROWSER_REQUEST_TIMEOUT_MILLIS] by default. A request carrying [BrowserCallTimeout] takes that bound instead, since
 *   a call that knows how much it carries can tell a stall much sooner; the native engines ignore the attribute.
 */
internal fun HttpClientConfig<*>.configureClient(sendsUserAgent: Boolean, hasSocketTimeout: Boolean) {
    expectSuccess = false
    if (sendsUserAgent) {
        install(UserAgent) { agent = USER_AGENT }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = if (hasSocketTimeout) HttpTimeoutConfig.INFINITE_TIMEOUT_MS else BROWSER_REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
    }
    if (!hasSocketTimeout) {
        install(BrowserCallTimeouts)
    }
}

/**
 * The bound, in milliseconds, on the whole exchange of one request in the browser, where it replaces
 * [BROWSER_REQUEST_TIMEOUT_MILLIS]. Ignored by the native engines, whose socket timeout already tells a stall apart.
 */
internal val BrowserCallTimeout = AttributeKey<Long>("BrowserCallTimeout")

/** Runs before [HttpTimeout] fills in the client's defaults, so only the total of a request is replaced. */
private val BrowserCallTimeouts = createClientPlugin("BrowserCallTimeouts") {
    onRequest { request, _ ->
        request.attributes.getOrNull(BrowserCallTimeout)?.let { bound -> request.timeout { requestTimeoutMillis = bound } }
    }
}

private const val SOCKET_TIMEOUT_MILLIS = 60_000L
private const val BROWSER_REQUEST_TIMEOUT_MILLIS = 10 * 60_000L
private const val CONNECT_TIMEOUT_MILLIS = 20_000L
