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

import com.pandulapeter.campfire.data.model.domain.Catalogue
import com.pandulapeter.campfire.data.model.domain.CoverArtCandidate
import com.pandulapeter.campfire.data.model.domain.CoverArtQuery
import com.pandulapeter.campfire.data.model.domain.SongDetailsCandidate
import com.pandulapeter.campfire.data.model.domain.SongDetailsQuery
import com.pandulapeter.campfire.data.source.remote.api.catalogue.CatalogueSearchException
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
import kotlin.time.Duration.Companion.milliseconds

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
                    catalogue = Catalogue.ITUNES,
                    id = "1",
                    title = "Nimrod",
                    artist = "Green Day",
                    year = "1996",
                    type = null,
                    coverArtUrl = "https://is1-ssl.mzstatic.com/image/a/b.jpg/250x250bb.jpg",
                ),
                CoverArtCandidate(
                    catalogue = Catalogue.ITUNES,
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
        val source = ITunesRemoteSource(
            httpClientHolder = HttpClientHolder { HttpClient(MockEngine { requestCount++; respond("", HttpStatusCode.Forbidden) }) { expectSuccess = false } },
        )

        assertFailsWith<CatalogueSearchException> { source.searchCoverArt(CoverArtQuery(artist = "", album = "Nimrod", title = ""), onBusy = {}) }
        assertEquals(1, requestCount)
    }

    @Test
    fun `a details lookup searches by the artist and the title, never the album`() {
        val url = Url(ITunesSearch.songDetailsUrl(SongDetailsQuery(title = "Wonderwall", artist = "Oasis", album = "Morning Glory"))!!)

        assertEquals("Oasis Wonderwall", url.parameters["term"])
        assertEquals("song", url.parameters["entity"])
        assertNull(ITunesSearch.songDetailsUrl(SongDetailsQuery(title = "", artist = "Oasis", album = "Morning Glory")))
    }

    @Test
    fun `songs become details candidates, a single's name not taken for an album and repeats dropped`() {
        val body = """
            {"results":[
              {"wrapperType":"track","trackId":1,"trackName":"Wonderwall","artistName":"Oasis","collectionName":"(What's the Story) Morning Glory?",
               "releaseDate":"1995-10-02T07:00:00Z","trackTimeMillis":258907},
              {"wrapperType":"track","trackId":2,"trackName":"Wonderwall","artistName":"Oasis","collectionName":"(What's the Story) Morning Glory?",
               "releaseDate":"1995-10-02T12:00:00Z","trackTimeMillis":258773},
              {"wrapperType":"track","trackId":3,"trackName":"Wonderwall","artistName":"Oasis","collectionName":"Wonderwall - Single","releaseDate":"1995-10-30T12:00:00Z"},
              {"wrapperType":"track","trackId":4,"trackName":"Wonderwall","artistName":"Oasis","collectionName":"Wonderwall - EP","releaseDate":"1995-10-30T12:00:00Z"},
              {"wrapperType":"collection","collectionId":5,"collectionName":"Something else"}
            ]}
        """.trimIndent()

        val candidates = ITunesSearch.songDetailsCandidates(body)

        assertEquals(listOf("1", "3", "4"), candidates.map { it.id })
        assertEquals(Catalogue.ITUNES, candidates[0].catalogue)
        assertEquals("(What's the Story) Morning Glory?", candidates[0].album)
        assertEquals("1995", candidates[0].year)
        assertEquals(258_907.milliseconds, candidates[0].duration)
        assertNull(candidates[1].album)
        assertNull(candidates[1].duration)
        assertEquals("Wonderwall", candidates[2].album)
    }

    @Test
    fun `the Search API is never asked for songwriters`() = runTest {
        var requestCount = 0
        val source = ITunesRemoteSource(
            httpClientHolder = HttpClientHolder { HttpClient(MockEngine { requestCount++; respond("", HttpStatusCode.OK) }) { expectSuccess = false } },
        )
        val candidate = SongDetailsCandidate(
            catalogue = Catalogue.ITUNES,
            id = "1",
            title = "Wonderwall",
            artist = "Oasis",
            album = null,
            year = null,
            duration = null,
        )

        assertNull(source.loadSongCredits(candidate, onBusy = {}))
        assertEquals(0, requestCount)
    }
}
