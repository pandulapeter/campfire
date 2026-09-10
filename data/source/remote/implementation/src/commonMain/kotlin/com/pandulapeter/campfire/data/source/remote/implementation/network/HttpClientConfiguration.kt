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

/**
 * The settings every engine shares. Redirects are followed (the token endpoints do), and every request is given a
 * generous but finite timeout so that a stalled connection ends in a failure the user can retry rather than in a
 * sync that never finishes.
 */
internal fun HttpClientConfig<*>.configureForSync() {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
    }
}

private const val REQUEST_TIMEOUT_MILLIS = 60_000L
private const val CONNECT_TIMEOUT_MILLIS = 20_000L
