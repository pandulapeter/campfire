package com.pandulapeter.campfire.data.source.remote.implementation.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

/**
 * The browser engine goes through `fetch`, so every request Campfire makes is subject to the origin's CORS rules.
 * Dropbox answers the calls used here with the headers that allows; a provider that does not would need a proxy,
 * which is exactly the sort of thing Campfire refuses to run.
 */
internal actual fun createHttpClient() = HttpClient(Js) { configureForSync() }
