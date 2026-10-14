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

import com.pandulapeter.campfire.data.model.catalogue.Catalogue
import com.pandulapeter.campfire.data.model.catalogue.CoverArtCandidate
import com.pandulapeter.campfire.data.model.catalogue.CoverArtQuery
import com.pandulapeter.campfire.data.model.catalogue.SongDetailsCandidate
import com.pandulapeter.campfire.data.model.catalogue.SongDetailsQuery
import com.pandulapeter.campfire.data.source.remote.api.catalogue.CatalogueSearchException
import com.pandulapeter.campfire.data.source.remote.api.catalogue.CoverArtSearchRemoteSource
import com.pandulapeter.campfire.data.source.remote.api.catalogue.SongDetailsSearchRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementation.network.catalogueTransport
import com.pandulapeter.campfire.data.source.remote.implementation.network.parseCatalogueAnswer
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * The cover search and the song details lookup on the iTunes Search API. It asks for no key and sends CORS headers,
 * both on the search and on the artwork, so the web build can use it as it is. It allows roughly twenty requests a
 * minute, which one search per press of a button never comes near, so it is asked without a pace of its own and a
 * refusal is a failure like any other rather than something to wait out: the other catalogue's candidates are still
 * there meanwhile.
 */
internal class ITunesRemoteSource(
    private val httpClientHolder: HttpClientHolder,
) : CoverArtSearchRemoteSource,
    SongDetailsSearchRemoteSource {

    override val catalogue = Catalogue.ITUNES

    override suspend fun searchCoverArt(query: CoverArtQuery, onBusy: () -> Unit): List<CoverArtCandidate> {
        val url = ITunesSearch.url(query) ?: return emptyList()
        val body = fetch(url)
        return parseCatalogueAnswer(SERVICE_NAME) { ITunesSearch.candidates(body) }
    }

    override suspend fun searchSongDetails(query: SongDetailsQuery, onBusy: () -> Unit): List<SongDetailsCandidate> {
        val url = ITunesSearch.songDetailsUrl(query) ?: return emptyList()
        val body = fetch(url)
        return parseCatalogueAnswer(SERVICE_NAME) { ITunesSearch.songDetailsCandidates(body) }
    }

    /** The Search API names no songwriter, so there is nothing to ask. */
    override suspend fun loadSongCredits(candidate: SongDetailsCandidate, onBusy: () -> Unit) = null

    private suspend fun fetch(url: String) = catalogueTransport(SERVICE_NAME) {
        val response = httpClientHolder.client().get(url)
        if (!response.status.isSuccess()) throw CatalogueSearchException("$SERVICE_NAME answered ${response.status.value}.")
        response.bodyAsText()
    }

    private companion object {
        const val SERVICE_NAME = "iTunes"
    }
}
