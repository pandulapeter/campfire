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
import io.ktor.client.plugins.UserAgent

/**
 * The settings every engine shares. Redirects are followed (the token endpoints do, and so does the Cover Art Archive,
 * twice), and every request is given a generous but finite timeout so that a stalled connection ends in a failure the
 * user can retry rather than in a sync that never finishes.
 *
 * @param sendsUserAgent Whether every request names Campfire in its `User-Agent`, see [USER_AGENT]. False only in the
 *   browser, where the header is one a script is not allowed to set: `fetch` either drops it or, where it does send
 *   it, makes every request to a host that does not expect it a CORS preflight that host may well refuse.
 */
internal fun HttpClientConfig<*>.configureClient(sendsUserAgent: Boolean) {
    expectSuccess = false
    if (sendsUserAgent) {
        install(UserAgent) { agent = USER_AGENT }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
    }
}

private const val REQUEST_TIMEOUT_MILLIS = 60_000L
private const val CONNECT_TIMEOUT_MILLIS = 20_000L
