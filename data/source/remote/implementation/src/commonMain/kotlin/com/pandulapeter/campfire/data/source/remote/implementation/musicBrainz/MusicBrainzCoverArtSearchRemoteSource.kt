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
import com.pandulapeter.campfire.data.model.domain.CoverArtService
import com.pandulapeter.campfire.data.source.remote.api.CoverArtSearchException
import com.pandulapeter.campfire.data.source.remote.api.CoverArtSearchRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementation.coverArt.coverArtSearchTransport
import com.pandulapeter.campfire.data.source.remote.implementation.coverArt.parseCoverArtSearchAnswer
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import com.pandulapeter.campfire.data.source.remote.implementation.network.exponentialBackoffSeconds
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay

/**
 * The cover search on MusicBrainz. Every request waits its turn at [rateLimiter], and a refusal — 503, which is how
 * MusicBrainz answers a client going faster than it allows, or 429 — is waited out and asked again: for as long as
 * its `Retry-After` says, or where it says nothing, for a wait that doubles from 2 s to 32 s, over at most
 * [MAXIMUM_RETRIES] attempts, the way the Dropbox provider sits out being rate limited. The caller hears of every such
 * wait through `onBusy`, since a search that takes ten seconds without saying why looks broken.
 */
internal class MusicBrainzCoverArtSearchRemoteSource(
    private val httpClientHolder: HttpClientHolder,
    private val rateLimiter: MusicBrainzRateLimiter,
) : CoverArtSearchRemoteSource {

    override val service = CoverArtService.MUSIC_BRAINZ

    override suspend fun searchCoverArt(query: CoverArtQuery, onBusy: () -> Unit): List<CoverArtCandidate> {
        val request = MusicBrainzSearch.request(query) ?: return emptyList()
        val body = fetch(request.url, onBusy)
        return parseCoverArtSearchAnswer(SERVICE_NAME) { MusicBrainzSearch.candidates(request, body) }
    }

    private suspend fun fetch(url: String, onBusy: () -> Unit): String {
        var attempt = 0
        while (true) {
            val (status, retryAfterSeconds, body) = coverArtSearchTransport(SERVICE_NAME) {
                rateLimiter.awaitTurn()
                val response = httpClientHolder.client().get(url)
                Triple(response.status, response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull(), response.bodyAsText())
            }
            when {
                status.isSuccess() -> return body
                status == HttpStatusCode.ServiceUnavailable || status == HttpStatusCode.TooManyRequests -> {
                    if (attempt >= MAXIMUM_RETRIES) throw CoverArtSearchException("$SERVICE_NAME is busy.")
                    onBusy()
                    delay((retryAfterSeconds ?: exponentialBackoffSeconds(attempt)) * 1000L)
                    attempt++
                }

                else -> throw CoverArtSearchException("$SERVICE_NAME answered ${status.value}.")
            }
        }
    }

    private companion object {
        const val SERVICE_NAME = "MusicBrainz"
        const val MAXIMUM_RETRIES = 5
    }
}
