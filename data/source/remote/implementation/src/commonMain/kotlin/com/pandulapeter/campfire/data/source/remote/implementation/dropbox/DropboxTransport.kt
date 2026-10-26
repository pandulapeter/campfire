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

import com.pandulapeter.campfire.data.source.remote.api.sync.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncRemoteStorageFullException
import com.pandulapeter.campfire.data.source.remote.implementation.network.BrowserCallTimeout
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import com.pandulapeter.campfire.data.source.remote.implementation.network.SyncApiException
import com.pandulapeter.campfire.data.source.remote.implementation.network.SyncHttpTransport
import com.pandulapeter.campfire.data.source.remote.implementation.network.decodeSyncAnswer
import com.pandulapeter.campfire.data.source.remote.implementation.network.syncTransport
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy

/**
 * How a call reaches Dropbox: through [SyncHttpTransport], which retries and renews, with Dropbox's own bounds on a call
 * in the browser and its own reading of a failed answer ([ensureSuccessful]).
 */
internal class DropboxTransport(
    private val httpClientHolder: HttpClientHolder,
    private val http: SyncHttpTransport,
) {

    /**
     * A Dropbox "RPC" call: JSON in, JSON out, everything above the transport reported in the body, which is decoded
     * with [deserializer] (see [decodeAnswer]).
     *
     * @param browserTimeoutMillis The bound on the whole exchange in the browser, see [BrowserCallTimeout].
     */
    suspend fun <T> rpc(
        url: String,
        body: String?,
        deserializer: DeserializationStrategy<T>,
        browserTimeoutMillis: Long = RPC_BROWSER_TIMEOUT_MILLIS,
    ): T {
        val response = request { accessToken ->
            httpClientHolder.client().post(url) {
                attributes.put(BrowserCallTimeout, browserTimeoutMillis)
                header("Authorization", "Bearer $accessToken")
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        }
        response.ensureSuccessful()
        return decodeAnswer(deserializer, transport { response.bodyAsText() })
    }

    /**
     * One call, retried and renewed as [SyncHttpTransport.request] does. Every call sent here is safe to send again:
     * Dropbox writes nothing for an upload whose bytes are already the file's and answers its metadata, so a resent
     * write that had landed reads as written, one that meets a file changed in between is a `Conflict` the engine
     * resolves as usual, and a resent deletion finds the file gone.
     */
    suspend fun request(block: suspend (accessToken: String) -> HttpResponse) = http.request(block)

    companion object {
        /**
         * The browser's bound on an account read or a deletion, a few hundred bytes each way.
         *
         * These bounds on one call's whole exchange apply in the browser only, where `fetch` has no timeout between
         * packets. They keep a stalled web run from sitting on "syncing" for over an hour, at the price of a large file
         * on a very slow browser link timing out where it would have finished; the native engines, which bound the
         * silence instead, are unaffected.
         */
        const val RPC_BROWSER_TIMEOUT_MILLIS = 60_000L

        /** A gzipped listing page of up to about 200 KB, at about 2 KB/s. */
        const val LISTING_BROWSER_TIMEOUT_MILLIS = 120_000L

        /** A download, whose size is not known when it is asked for: a song at any rate, 8 MB at about 70 KB/s. */
        const val DOWNLOAD_BROWSER_TIMEOUT_MILLIS = 120_000L

        /** An upload's bound before its size is counted: a song of a few KB gets this. */
        const val UPLOAD_BROWSER_TIMEOUT_MILLIS = 60_000L

        /** The rate an upload's size is given time at on top of [UPLOAD_BROWSER_TIMEOUT_MILLIS]: 8 MB gets about 9 minutes. */
        const val UPLOAD_BROWSER_BYTES_PER_SECOND = 16_384L
    }
}

/** A successful answer of Dropbox's, decoded, see [decodeSyncAnswer]. */
internal fun <T> decodeAnswer(deserializer: DeserializationStrategy<T>, text: String): T =
    decodeSyncAnswer(DropboxErrors.serviceName, deserializer, text)

/** One call to Dropbox, whose failure without an answer is the network's, see [syncTransport]. */
internal suspend fun <T> transport(block: suspend () -> T): T = syncTransport(DropboxErrors.serviceName, block)

internal suspend fun HttpResponse.ensureSuccessful() {
    if (status.isSuccess()) return
    val summary = errorSummary()
    throw when {
        status == HttpStatusCode.Unauthorized -> SyncAuthorizationException("Dropbox refused the token: $summary")
        // Dropbox says 429 for rate limiting and 5xx for its own trouble, and a proxy that gave up on a request says 408;
        // all are worth trying again later.
        status == HttpStatusCode.TooManyRequests || status == HttpStatusCode.RequestTimeout || status.value >= 500 ->
            SyncNetworkException("Dropbox is busy: ${status.value}")
        // A full account, which Dropbox reports per write: "path/insufficient_space/..".
        status == HttpStatusCode.Conflict && summary.contains("insufficient_space") ->
            SyncRemoteStorageFullException("Dropbox is full: $summary")
        else -> DropboxApiException(status.value, summary)
    }
}

internal suspend fun HttpResponse.errorSummary() = try {
    json.decodeFromString<DropboxErrorResponse>(bodyAsText()).errorSummary
} catch (exception: CancellationException) {
    throw exception
} catch (exception: Exception) {
    ""
}

/** A call Dropbox answered, with something other than success. */
internal class DropboxApiException(val statusCode: Int, val errorSummary: String) :
    SyncApiException("Dropbox answered $statusCode: $errorSummary")
