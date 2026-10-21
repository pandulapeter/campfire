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
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncRunEndingException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncScopeMissingException
import com.pandulapeter.campfire.data.source.remote.implementation.network.BrowserCallTimeout
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import com.pandulapeter.campfire.data.source.remote.implementation.network.exponentialBackoffSeconds
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
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
import kotlin.random.Random
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException

/**
 * How a call reaches Dropbox: retried while the service asks it to slow down or does not answer, and sent once more
 * with a renewed token when the one it carried is refused, see [request].
 */
internal class DropboxTransport(
    private val httpClientHolder: HttpClientHolder,
    private val tokens: DropboxTokens,
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
     * One call, retried while Dropbox asks it to slow down.
     *
     * A first sync of a whole library is a few hundred calls in quick succession, so being rate limited is the
     * expected answer rather than an exceptional one - and giving up on the run because of it would mean a library
     * that can never finish its first sync. Writes are limited separately: several of them landing in one folder at
     * once are answered with a 409 whose summary says `too_many_write_operations`, which Dropbox documents as "back off
     * and retry" rather than as a failure of that file. Dropbox says how long to wait in a `retry_after` field of the
     * body or in `Retry-After`, and that is waited as given. Where it says nothing - typically its own trouble, a 5xx -
     * the wait doubles from two seconds up to half a minute, so that an outage of a minute or so is sat out rather than
     * ending the run. The jitter is there because several transfers are in flight at once and would otherwise all
     * come back at the same moment and be limited again together.
     *
     * A 401 is answered once with a refresh before it is believed. Whether the stored token is still good is worked
     * out from the device's clock, and a clock that was wrong when the token was issued keeps saying "fresh" long after
     * Dropbox has stopped accepting it - while the refresh token that would fix that is perfectly good. [block] is
     * handed the token it sends, so that the one refused is known and only that one is renewed: the transfers of a run
     * refused together share one renewal. A second 401 is the real refusal. A 401 for a permission the connection was
     * never granted (`missing_scope`) is neither: a renewed token carries the same permissions, and the credentials are
     * good for everything else, so it is thrown as [SyncScopeMissingException] at once, never as a refusal that would end
     * a run and show the connection as failed.
     *
     * A failure without an answer - a timeout, a reset, a stream that ended early, or a fetch that failed - is retried
     * with the same doubling wait, a timeout up to [MAXIMUM_RETRIES] times and anything else up to
     * [MAXIMUM_TRANSPORT_FAILURE_RETRIES]: on a phone a cell handover or a moment in a tunnel shows up as any of them, and
     * one of them ending the run would leave a large library on a poor connection never finishing a sync. They are counted
     * apart from the waits Dropbox asks for, so a call rate limited a few times still gets every retry of a reset, and the
     * reverse; neither count is reset by progress, so the longest one call can take is the sum of both budgets - six
     * requested waits of at most two minutes each, plus seven attempts bounded by the engine's timeouts. Every call sent
     * here is safe to send again: Dropbox writes nothing for an upload whose bytes are already the file's and answers its
     * metadata, so a resent write that had landed reads as written, one that meets a file changed in between is a
     * `Conflict` the engine resolves as usual, and a resent deletion finds the file gone.
     */
    suspend fun request(block: suspend (accessToken: String) -> HttpResponse): HttpResponse {
        var waits = 0
        var failures = 0
        var refusedToken: String? = null
        while (true) {
            var token = ""
            val response = transport {
                try {
                    // The token is asked for inside transport, like the call: a renewal whose answer cannot be decoded
                    // must stay a network failure of the run rather than become one of this file. It is retried like
                    // the call, since it is the first request of most runs; the wait below is outside the
                    // credentials lock the renewal holds.
                    token = tokens.accessToken(refused = refusedToken)
                    block(token)
                } catch (exception: SyncAuthorizationException) {
                    throw exception
                } catch (throwable: Throwable) {
                    // Null asks for another attempt; the last one is left to transport, which makes it the run's
                    // network failure. Throwable, because the browser engine reports a failed fetch as a kotlin.Error.
                    currentCoroutineContext().ensureActive()
                    val cause = (throwable as? SyncNetworkException)?.cause ?: throwable
                    val limit = if (cause.isTimeout()) MAXIMUM_RETRIES else MAXIMUM_TRANSPORT_FAILURE_RETRIES
                    if (failures >= limit) throw throwable
                    null
                }
            }
            if (response == null) {
                delay(exponentialBackoffSeconds(failures) * 1000L + Random.nextLong(RETRY_JITTER_MILLIS))
                failures++
                continue
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                response.scopeMissing()?.let { throw it }
                if (refusedToken == null) {
                    refusedToken = token
                    continue
                }
            }
            val retryAfterMillis = response.retryAfterMillis(waits)
            if (retryAfterMillis == null || waits >= MAXIMUM_RETRIES) return response
            waits++
            delay(retryAfterMillis + Random.nextLong(RETRY_JITTER_MILLIS))
        }
    }

    private fun Throwable.isTimeout() = this is HttpRequestTimeoutException || this is ConnectTimeoutException || this is SocketTimeoutException

    /**
     * Null when the answer is one to act on rather than to wait out. A requested wait longer than
     * [MAXIMUM_REQUESTED_WAIT_SECONDS] ends the run instead, as the service being busy, whatever the call.
     */
    private suspend fun HttpResponse.retryAfterMillis(attempt: Int): Long? = when {
        status == HttpStatusCode.TooManyRequests ||
            status == HttpStatusCode.RequestTimeout ||
            status.value >= 500 ||
            (status == HttpStatusCode.Conflict && errorSummary().contains("too_many_write_operations")) -> {
            val requested = (retryAfterSecondsInBody() ?: headers["Retry-After"]?.toLongOrNull())?.coerceAtLeast(0)
            if (requested != null && requested > MAXIMUM_REQUESTED_WAIT_SECONDS) {
                throw SyncNetworkException("Dropbox asked to wait $requested s.")
            }
            (requested ?: exponentialBackoffSeconds(attempt)) * 1000L
        }

        else -> null
    }

    private suspend fun HttpResponse.scopeMissing() = try {
        json.decodeFromString<DropboxScopeErrorResponse>(bodyAsText())
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        null
    }?.takeIf { it.errorSummary.startsWith("missing_scope") }?.let { answer ->
        SyncScopeMissingException("Dropbox needs a permission the connection lacks: ${answer.error.requiredScope}", answer.error.requiredScope)
    }

    private suspend fun HttpResponse.retryAfterSecondsInBody() = try {
        json.decodeFromString<DropboxRateLimitResponse>(bodyAsText()).error.retryAfter
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        null
    }

    companion object {
        /** Six waits of 2, 4, 8, 16, 32 and 32 seconds: about a minute and a half of patience. */
        const val MAXIMUM_RETRIES = 6

        /**
         * Three waits of 2, 4 and 8 seconds for a failure that is not a timeout: enough to outlast a cell handover, while
         * a device that is plainly offline - a lookup failing at once, or the browser's failed fetch - still hears within
         * seconds rather than after the minute and a half a timeout is given.
         */
        const val MAXIMUM_TRANSPORT_FAILURE_RETRIES = 3

        /**
         * Dropbox's own rate limits ask for seconds to a few tens of seconds; anything far past the doubling's longest
         * wait comes from something in between, and no run sits silent on "syncing" for longer than this.
         */
        const val MAXIMUM_REQUESTED_WAIT_SECONDS = 120L

        const val RETRY_JITTER_MILLIS = 500L

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

/**
 * A successful answer of Dropbox's, decoded. One that is not in any of Dropbox's documented shapes - a TLS-intercepting
 * proxy's block page, a captive portal's login page, a body cut mid-JSON - comes from something between the device
 * and Dropbox rather than from Dropbox, and is the network's failure, not the programming error it would otherwise be.
 */
internal fun <T> decodeAnswer(deserializer: DeserializationStrategy<T>, text: String): T = try {
    json.decodeFromString(deserializer, text)
} catch (exception: SerializationException) {
    throw SyncNetworkException("Dropbox's answer could not be read.", exception)
}

/**
 * Anything the transport throws - no route to the host, a dropped connection, a timeout - is one thing to the
 * user: the service could not be reached, and trying again later is worth doing. Only the call itself is
 * wrapped; an answer that cannot be decoded is taken for the network's failure by [decodeAnswer]. The browser engine
 * reports a failed `fetch` as a `kotlin.Error`, not an `Exception`, so the last branch takes any `Throwable`:
 * everything `block` can throw that is not a cancellation is the call failing.
 *
 * A cancellation is the one exception that says nothing about the network. Stopping a run or giving up on a
 * consent page resumes every suspended request with one, and it has to leave here as what it is: the engine and
 * the repository both answer a stopped run differently from a failed one, and can only do so if they are told.
 */
internal suspend fun <T> transport(block: suspend () -> T): T = try {
    block()
} catch (exception: CancellationException) {
    // Thrown on again only while this coroutine really is cancelled. A cancellation that reaches a coroutine
    // nobody cancelled belongs to something underneath - a client that was closed, a timeout surfacing as one -
    // and passed on it would end a transfer of the engine's without a word, as though the user had stopped it.
    currentCoroutineContext().ensureActive()
    throw SyncNetworkException(exception.message ?: "Dropbox could not be reached.", exception)
} catch (exception: SyncRunEndingException) {
    throw exception
} catch (exception: DropboxApiException) {
    throw exception
} catch (throwable: Throwable) {
    throw SyncNetworkException(throwable.message ?: "Dropbox could not be reached.", throwable)
}

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
    Exception("Dropbox answered $statusCode: $errorSummary")
