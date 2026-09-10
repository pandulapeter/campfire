package com.pandulapeter.campfire.data.source.remote.implementation.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

internal actual fun createHttpClient() = HttpClient(Darwin) { configureForSync() }
