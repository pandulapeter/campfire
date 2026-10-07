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
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ITunesSearchTest {

    @Test
    fun `the artist and the album are searched for among the songs`() {
        val url = Url(ITunesSearch.url(CoverArtQuery(artist = " Green Day ", album = "Nimrod", title = "Good Riddance"))!!)

        assertEquals("/search", url.encodedPath)
        assertEquals("Green Day Nimrod", url.parameters["term"])
        assertEquals("song", url.parameters["entity"])
        assertEquals("music", url.parameters["media"])
    }

    @Test
    fun `the title stands in for an empty album, and the artist may be left out`() {
        assertEquals("Yesterday", Url(ITunesSearch.url(CoverArtQuery(artist = "", album = " ", title = "Yesterday"))!!).parameters["term"])
    }

    @Test
    fun `there is nothing to search by without an album or a title`() {
        assertNull(ITunesSearch.url(CoverArtQuery(artist = "Queen", album = "", title = "")))
    }

    @Test
    fun `a query outside ASCII survives the address`() {
        val url = Url(ITunesSearch.url(CoverArtQuery(artist = "Kispál és a Borz", album = "Ágy, asztal, tévé", title = ""))!!)

        assertEquals("Kispál és a Borz Ágy, asztal, tévé", url.parameters["term"])
    }

    @Test
    fun `songs become the albums they are on, each once, with the artwork at the cover size`() {
        val body = """
            {"resultCount":5,"results":[
              {"wrapperType":"track","kind":"song","collectionId":1,"collectionName":"Nimrod","artistName":"Green Day",
               "releaseDate":"1997-10-14T07:00:00Z","artworkUrl100":"https://is1-ssl.mzstatic.com/image/a/b.jpg/100x100bb.jpg","unknown":1},
              {"wrapperType":"track","collectionId":2,"collectionName":"Good Riddance - Single","artistName":"Green Day",
               "collectionArtistName":"Green Day & Friends","releaseDate":"1998-01-01T08:00:00Z",
               "artworkUrl100":"https://is1-ssl.mzstatic.com/image/c/d.png/100x100bb.png"},
              {"wrapperType":"track","collectionId":1,"collectionName":"Nimrod","artistName":"Green Day",
               "releaseDate":"1996-01-01T08:00:00Z","artworkUrl100":"https://is1-ssl.mzstatic.com/image/a/b.jpg/100x100bb.jpg"},
              {"wrapperType":"track","collectionId":3,"collectionName":"No artwork","artistName":"X"},
              {"wrapperType":"artist","collectionId":4,"collectionName":"Not a song","artworkUrl100":"https://x/100x100bb.jpg"}
            ]}
        """.trimIndent()

        assertEquals(
            listOf(
                CoverArtCandidate(
                    service = CoverArtService.ITUNES,
                    id = "1",
                    title = "Nimrod",
                    artist = "Green Day",
                    year = "1996",
                    type = null,
                    coverArtUrl = "https://is1-ssl.mzstatic.com/image/a/b.jpg/250x250bb.jpg",
                ),
                CoverArtCandidate(
                    service = CoverArtService.ITUNES,
                    id = "2",
                    title = "Good Riddance",
                    artist = "Green Day & Friends",
                    year = "1998",
                    type = "Single",
                    coverArtUrl = "https://is1-ssl.mzstatic.com/image/c/d.png/250x250bb.jpg",
                ),
            ),
            ITunesSearch.candidates(body),
        )
    }

    @Test
    fun `an artwork address in an unexpected form is not guessed at`() {
        assertNull(ITunesSearch.coverArtUrl("https://is1-ssl.mzstatic.com/image/a/b.jpg"))
    }

    @Test
    fun `a refusal is a failure rather than something to wait out`() = runTest {
        var requestCount = 0
        val source = ITunesCoverArtSearchRemoteSource(
            httpClientHolder = HttpClientHolder { HttpClient(MockEngine { requestCount++; respond("", HttpStatusCode.Forbidden) }) { expectSuccess = false } },
        )

        assertFailsWith<CoverArtSearchException> { source.searchCoverArt(CoverArtQuery(artist = "", album = "Nimrod", title = ""), onBusy = {}) }
        assertEquals(1, requestCount)
    }
}
