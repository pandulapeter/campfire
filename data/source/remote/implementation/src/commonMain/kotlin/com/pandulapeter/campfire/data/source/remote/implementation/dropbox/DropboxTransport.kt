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

import com.pandulapeter.campfire.data.source.remote.api.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.SyncRemoteStorageFullException
import com.pandulapeter.campfire.data.source.remote.api.SyncRunEndingException
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

/**
 * How a call reaches Dropbox: retried while the service asks it to slow down or does not answer, and sent once more
 * with a renewed token when the one it carried is refused, see [request].
 */
internal class DropboxTransport(
    private val httpClientHolder: HttpClientHolder,
    private val tokens: DropboxTokens,
) {

    /** A Dropbox "RPC" call: JSON in, JSON out, everything above the transport reported in the body. */
    suspend fun rpc(url: String, body: String?): String {
        val response = request { accessToken ->
            httpClientHolder.client().post(url) {
                header("Authorization", "Bearer $accessToken")
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        }
        response.ensureSuccessful()
        return transport { response.bodyAsText() }
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
     * refused together share one renewal. A second 401 is the real refusal.
     *
     * A timeout produces no answer at all, and is retried with the same doubling wait: on a phone it is more often a
     * cell handover or a moment in a tunnel than a service that is gone, and one of them ending the run would leave a
     * large library on a poor connection never finishing a sync. Anything else the transport throws is not retried.
     */
    suspend fun request(block: suspend (accessToken: String) -> HttpResponse): HttpResponse {
        var attempt = 0
        var refusedToken: String? = null
        while (true) {
            var token = ""
            // The token is asked for inside transport, like the call: a renewal whose answer cannot be decoded must
            // stay a network failure of the run rather than become one of this file.
            val response = transport {
                token = tokens.accessToken(refused = refusedToken)
                try {
                    block(token)
                } catch (exception: Exception) {
                    // Null asks for another attempt; the last one is left to transport, which makes it the run's
                    // network failure as before.
                    currentCoroutineContext().ensureActive()
                    if (attempt >= MAXIMUM_RETRIES || !exception.isTimeout()) throw exception
                    null
                }
            }
            if (response == null) {
                delay(exponentialBackoffSeconds(attempt) * 1000L + Random.nextLong(RETRY_JITTER_MILLIS))
                attempt++
                continue
            }
            if (response.status == HttpStatusCode.Unauthorized && refusedToken == null) {
                refusedToken = token
                continue
            }
            val retryAfterMillis = response.retryAfterMillis(attempt)
            if (retryAfterMillis == null || attempt >= MAXIMUM_RETRIES) return response
            attempt++
            delay(retryAfterMillis + Random.nextLong(RETRY_JITTER_MILLIS))
        }
    }

    private fun Exception.isTimeout() = this is HttpRequestTimeoutException || this is ConnectTimeoutException || this is SocketTimeoutException

    /** Null when the answer is one to act on rather than to wait out. */
    private suspend fun HttpResponse.retryAfterMillis(attempt: Int): Long? = when {
        status == HttpStatusCode.TooManyRequests ||
            status.value >= 500 ||
            (status == HttpStatusCode.Conflict && errorSummary().contains("too_many_write_operations")) ->
            (retryAfterSecondsInBody() ?: headers["Retry-After"]?.toLongOrNull()
                ?: exponentialBackoffSeconds(attempt)) * 1000L

        else -> null
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

        const val RETRY_JITTER_MILLIS = 500L
    }
}

/**
 * Anything the transport throws - no route to the host, a dropped connection, a timeout - is one thing to the
 * user: the service could not be reached, and trying again later is worth doing. Only the call itself is
 * wrapped, so that a body Campfire cannot make sense of stays the programming error it is. The browser engine
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
        // Dropbox says 429 for rate limiting and 5xx for its own trouble; both are worth trying again later.
        status == HttpStatusCode.TooManyRequests || status.value >= 500 -> SyncNetworkException("Dropbox is busy: ${status.value}")
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
