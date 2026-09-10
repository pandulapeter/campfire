package com.pandulapeter.campfire.data.source.remote.implementation.network

import io.ktor.client.HttpClient

/**
 * Every target picks the engine that is native to it: OkHttp on Android, CIO on the desktop, `NSURLSession` on iOS
 * and `fetch` in the browser. Nothing above this knows which, and the shared code stays free of JVM types.
 */
internal expect fun createHttpClient(): HttpClient
