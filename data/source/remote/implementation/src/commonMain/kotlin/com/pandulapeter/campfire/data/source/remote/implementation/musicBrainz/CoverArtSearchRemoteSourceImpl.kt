/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.musicBrainz

import com.pandulapeter.campfire.data.model.domain.CoverArtCandidate
import com.pandulapeter.campfire.data.model.domain.CoverArtQuery
import com.pandulapeter.campfire.data.source.remote.api.CoverArtSearchException
import com.pandulapeter.campfire.data.source.remote.api.CoverArtSearchRemoteSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import kotlin.math.min

/**
 * The cover search on MusicBrainz. Every request waits its turn at [rateLimiter], and a refusal — 503, which is how
 * MusicBrainz answers a client going faster than it allows, or 429 — is waited out and asked again: for as long as
 * its `Retry-After` says, or where it says nothing, for a wait that doubles from 2 s to 32 s, over at most
 * [MAXIMUM_RETRIES] attempts, the way the Dropbox provider sits out being rate limited. The caller hears of every such
 * wait through `onBusy`, since a search that takes ten seconds without saying why looks broken.
 */
internal class CoverArtSearchRemoteSourceImpl(
    private val httpClient: HttpClient,
    private val rateLimiter: MusicBrainzRateLimiter,
) : CoverArtSearchRemoteSource {

    override suspend fun searchCoverArt(query: CoverArtQuery, onBusy: () -> Unit): List<CoverArtCandidate> {
        val request = MusicBrainzSearch.request(query) ?: return emptyList()
        val body = fetch(request.url, onBusy)
        return try {
            MusicBrainzSearch.candidates(request, body)
        } catch (exception: SerializationException) {
            throw CoverArtSearchException("MusicBrainz answered with something that is not a search result.", exception)
        } catch (exception: IllegalArgumentException) {
            throw CoverArtSearchException("MusicBrainz answered with something that is not a search result.", exception)
        }
    }

    private suspend fun fetch(url: String, onBusy: () -> Unit): String {
        var attempt = 0
        while (true) {
            val (status, retryAfterSeconds, body) = transport {
                rateLimiter.awaitTurn()
                val response = httpClient.get(url)
                Triple(response.status, response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull(), response.bodyAsText())
            }
            when {
                status.isSuccess() -> return body
                status == HttpStatusCode.ServiceUnavailable || status == HttpStatusCode.TooManyRequests -> {
                    if (attempt >= MAXIMUM_RETRIES) throw CoverArtSearchException("MusicBrainz is busy.")
                    onBusy()
                    delay((retryAfterSeconds ?: min(DEFAULT_RETRY_SECONDS shl attempt, MAXIMUM_RETRY_SECONDS)) * 1000L)
                    attempt++
                }

                else -> throw CoverArtSearchException("MusicBrainz answered ${status.value}.")
            }
        }
    }

    /**
     * Anything the transport throws is the service not being reached, the way the Dropbox provider reads it: the
     * browser engine reports a failed `fetch` as a `kotlin.Error`, so every `Throwable` that is not a cancellation of
     * this coroutine counts.
     */
    private suspend fun <T> transport(block: suspend () -> T): T = try {
        block()
    } catch (exception: CancellationException) {
        currentCoroutineContext().ensureActive()
        throw CoverArtSearchException(exception.message ?: "MusicBrainz could not be reached.", exception)
    } catch (throwable: Throwable) {
        throw CoverArtSearchException(throwable.message ?: "MusicBrainz could not be reached.", throwable)
    }

    private companion object {
        const val MAXIMUM_RETRIES = 5
        const val DEFAULT_RETRY_SECONDS = 2L
        const val MAXIMUM_RETRY_SECONDS = 32L
    }
}
