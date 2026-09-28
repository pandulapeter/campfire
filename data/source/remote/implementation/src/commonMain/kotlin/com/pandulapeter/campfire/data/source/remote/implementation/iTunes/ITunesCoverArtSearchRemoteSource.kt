/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.iTunes

import com.pandulapeter.campfire.data.model.domain.CoverArtCandidate
import com.pandulapeter.campfire.data.model.domain.CoverArtQuery
import com.pandulapeter.campfire.data.model.domain.CoverArtService
import com.pandulapeter.campfire.data.source.remote.api.CoverArtSearchException
import com.pandulapeter.campfire.data.source.remote.api.CoverArtSearchRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementation.coverArt.coverArtSearchTransport
import com.pandulapeter.campfire.data.source.remote.implementation.coverArt.parseCoverArtSearchAnswer
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * The cover search on the iTunes Search API. It asks for no key and sends CORS headers, both on the search and on the
 * artwork, so the web build can use it as it is. It allows roughly twenty requests a minute, which one search per
 * press of a button never comes near, so it is asked without a pace of its own and a refusal is a failure like any
 * other rather than something to wait out: the other service's candidates are still there meanwhile.
 */
internal class ITunesCoverArtSearchRemoteSource(
    private val httpClient: HttpClient,
) : CoverArtSearchRemoteSource {

    override val service = CoverArtService.ITUNES

    override suspend fun searchCoverArt(query: CoverArtQuery, onBusy: () -> Unit): List<CoverArtCandidate> {
        val url = ITunesSearch.url(query) ?: return emptyList()
        val body = coverArtSearchTransport(SERVICE_NAME) {
            val response = httpClient.get(url)
            if (!response.status.isSuccess()) throw CoverArtSearchException("$SERVICE_NAME answered ${response.status.value}.")
            response.bodyAsText()
        }
        return parseCoverArtSearchAnswer(SERVICE_NAME) { ITunesSearch.candidates(body) }
    }

    private companion object {
        const val SERVICE_NAME = "iTunes"
    }
}
