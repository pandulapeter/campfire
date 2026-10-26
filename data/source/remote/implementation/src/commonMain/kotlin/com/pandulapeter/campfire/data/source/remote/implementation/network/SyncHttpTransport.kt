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

import com.pandulapeter.campfire.data.source.remote.api.sync.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncRunEndingException
import com.pandulapeter.campfire.data.source.remote.implementation.auth.OAuthTokens
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * How a call reaches a sync service: retried while the service asks it to slow down or does not answer, and sent once
 * more with a renewed token when the one it carried is refused, see [request]. What the service says beyond plain
 * HTTP is read by [errors].
 */
internal class SyncHttpTransport(
    private val tokens: OAuthTokens,
    private val errors: SyncHttpErrorMapper,
) {

    /**
     * One call, retried while the service asks it to slow down.
     *
     * A first sync of a whole library is a few hundred calls in quick succession, so being rate limited is the
     * expected answer rather than an exceptional one - and giving up on the run because of it would mean a library
     * that can never finish its first sync. A service may also ask for patience in an answer of its own, such as
     * Dropbox's 409 `too_many_write_operations`, which [SyncHttpErrorMapper.asksToSlowDown] recognizes. How long to
     * wait is taken from the body ([SyncHttpErrorMapper.requestedWaitSeconds]) or `Retry-After`, and waited as given.
     * Where the service says nothing - typically its own trouble, a 5xx - the wait doubles from two seconds up to half a
     * minute, so that an outage of a minute or so is sat out rather than ending the run. The jitter is there because
     * several transfers are in flight at once and would otherwise all come back at the same moment and be limited again
     * together.
     *
     * A 401 is answered once with a refresh before it is believed. Whether the stored token is still good is worked
     * out from the device's clock, and a clock that was wrong when the token was issued keeps saying "fresh" long after
     * the service has stopped accepting it - while the refresh token that would fix that is perfectly good. [block] is
     * handed the token it sends, so that the one refused is known and only that one is renewed: the transfers of a run
     * refused together share one renewal. A second 401 is the real refusal. A 401 that a renewal cannot cure
     * ([SyncHttpErrorMapper.refusalBeyondRenewal]) is neither, and is thrown at once.
     *
     * A failure without an answer - a timeout, a reset, a stream that ended early, or a fetch that failed - is retried
     * with the same doubling wait, a timeout up to [MAXIMUM_RETRIES] times and anything else up to
     * [MAXIMUM_TRANSPORT_FAILURE_RETRIES]: on a phone a cell handover or a moment in a tunnel shows up as any of them, and
     * one of them ending the run would leave a large library on a poor connection never finishing a sync. They are counted
     * apart from the waits the service asks for, so a call rate limited a few times still gets every retry of a reset, and
     * the reverse; neither count is reset by progress, so the longest one call can take is the sum of both budgets - six
     * requested waits of at most two minutes each, plus seven attempts bounded by the engine's timeouts. Only a call that
     * is safe to send again may come here.
     */
    suspend fun request(block: suspend (accessToken: String) -> HttpResponse): HttpResponse {
        var waits = 0
        var failures = 0
        var refusedToken: String? = null
        while (true) {
            var token = ""
            val response = syncTransport(errors.serviceName) {
                try {
                    // The token is asked for inside the transport, like the call: a renewal whose answer cannot be
                    // decoded must stay a network failure of the run rather than become one of this file. It is retried
                    // like the call, since it is the first request of most runs; the wait below is outside the
                    // credentials lock the renewal holds.
                    token = tokens.accessToken(refused = refusedToken)
                    block(token)
                } catch (exception: SyncAuthorizationException) {
                    throw exception
                } catch (throwable: Throwable) {
                    // Null asks for another attempt; the last one is left to the transport, which makes it the run's
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
                errors.refusalBeyondRenewal(response)?.let { throw it }
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
            errors.asksToSlowDown(this) -> {
            val requested = (errors.requestedWaitSeconds(this) ?: headers["Retry-After"]?.toLongOrNull())?.coerceAtLeast(0)
            if (requested != null && requested > MAXIMUM_REQUESTED_WAIT_SECONDS) {
                throw SyncNetworkException("${errors.serviceName} asked to wait $requested s.")
            }
            (requested ?: exponentialBackoffSeconds(attempt)) * 1000L
        }

        else -> null
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
         * A service's own rate limits ask for seconds to a few tens of seconds; anything far past the doubling's longest
         * wait comes from something in between, and no run sits silent on "syncing" for longer than this.
         */
        const val MAXIMUM_REQUESTED_WAIT_SECONDS = 120L

        const val RETRY_JITTER_MILLIS = 500L
    }
}

/**
 * A successful answer of the service's, decoded. One that is not in any of the service's documented shapes - a
 * TLS-intercepting proxy's block page, a captive portal's login page, a body cut mid-JSON - comes from something
 * between the device and the service rather than from the service, and is the network's failure, not the programming
 * error it would otherwise be.
 */
internal fun <T> decodeSyncAnswer(serviceName: String, deserializer: DeserializationStrategy<T>, text: String): T = try {
    syncAnswerJson.decodeFromString(deserializer, text)
} catch (exception: SerializationException) {
    throw SyncNetworkException("$serviceName's answer could not be read.", exception)
}

/**
 * Anything the transport throws - no route to the host, a dropped connection, a timeout - is one thing to the
 * user: the service could not be reached, and trying again later is worth doing. Only the call itself is
 * wrapped; an answer that cannot be decoded is taken for the network's failure by [decodeSyncAnswer]. The browser
 * engine reports a failed `fetch` as a `kotlin.Error`, not an `Exception`, so the last branch takes any `Throwable`:
 * everything `block` can throw that is not a cancellation is the call failing.
 *
 * A cancellation is the one exception that says nothing about the network. Stopping a run or giving up on a
 * consent page resumes every suspended request with one, and it has to leave here as what it is: the engine and
 * the repository both answer a stopped run differently from a failed one, and can only do so if they are told.
 */
internal suspend fun <T> syncTransport(serviceName: String, block: suspend () -> T): T = try {
    block()
} catch (exception: CancellationException) {
    // Thrown on again only while this coroutine really is cancelled. A cancellation that reaches a coroutine
    // nobody cancelled belongs to something underneath - a client that was closed, a timeout surfacing as one -
    // and passed on it would end a transfer of the engine's without a word, as though the user had stopped it.
    currentCoroutineContext().ensureActive()
    throw SyncNetworkException(exception.message ?: "$serviceName could not be reached.", exception)
} catch (exception: SyncRunEndingException) {
    throw exception
} catch (exception: SyncApiException) {
    throw exception
} catch (throwable: Throwable) {
    throw SyncNetworkException(throwable.message ?: "$serviceName could not be reached.", throwable)
}

/** Unknown keys ignored, so that a new field in a service's answer never breaks sync. */
private val syncAnswerJson = Json { ignoreUnknownKeys = true }
