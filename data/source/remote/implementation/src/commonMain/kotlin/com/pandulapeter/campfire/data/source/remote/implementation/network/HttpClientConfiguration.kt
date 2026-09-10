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
