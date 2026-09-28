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
import com.pandulapeter.campfire.data.source.remote.implementation.musicBrainz.MusicBrainzSearch.quoted
import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MusicBrainzSearchTest {

    @Test
    fun `an album is searched for among the release groups`() {
        val request = MusicBrainzSearch.request(CoverArtQuery(artist = "Green Day", album = "Dookie", title = "Basket Case"))!!
        val url = Url(request.url)

        assertEquals(MusicBrainzSearch.Entity.RELEASE_GROUP, request.entity)
        assertEquals("/ws/2/release-group", url.encodedPath)
        assertEquals("releasegroup:\"Dookie\" AND artist:\"Green Day\"", url.parameters["query"])
        assertEquals("json", url.parameters["fmt"])
    }

    @Test
    fun `a title alone is searched for among the recordings`() {
        val request = MusicBrainzSearch.request(CoverArtQuery(artist = " ", album = "", title = " Yesterday "))!!

        assertEquals(MusicBrainzSearch.Entity.RECORDING, request.entity)
        assertEquals("recording:\"Yesterday\"", Url(request.url).parameters["query"])
    }

    @Test
    fun `there is nothing to search by without an album or a title`() {
        assertNull(MusicBrainzSearch.request(CoverArtQuery(artist = "Queen", album = "", title = "")))
    }

    @Test
    fun `a phrase keeps Lucene's operators and escapes its quotes`() {
        assertEquals("\"AC/DC (Live) [1992]! -x\"", "AC/DC (Live) [1992]! -x".quoted())
        assertEquals("\"The \\\"Blue\\\" Album \\\\ 2\"", "The \"Blue\" Album \\ 2".quoted())
    }

    @Test
    fun `a query outside ASCII survives the address`() {
        val request = MusicBrainzSearch.request(CoverArtQuery(artist = "Kispál és a Borz", album = "Ágy, asztal, tévé", title = ""))!!

        assertEquals("releasegroup:\"Ágy, asztal, tévé\" AND artist:\"Kispál és a Borz\"", Url(request.url).parameters["query"])
    }

    @Test
    fun `release groups become candidates with the archive's front cover`() {
        val request = MusicBrainzSearch.request(CoverArtQuery(artist = "", album = "Dookie", title = ""))!!
        val body = """
            {"created":"x","count":2,"release-groups":[
              {"id":"rg1","title":"Dookie","primary-type":"Album","first-release-date":"1994-02-01",
               "artist-credit":[{"name":"Green Day","artist":{"id":"a"}}],"unknown":{"nested":true}},
              {"id":"rg2","title":"Dookie","first-release-date":"",
               "artist-credit":[{"name":"A","joinphrase":" feat. "},{"name":"B"}]},
              {"id":"","title":"No id"},
              {"id":"rg1","title":"Repeated"}
            ]}
        """.trimIndent()

        assertEquals(
            listOf(
                CoverArtCandidate(
                    service = CoverArtService.MUSIC_BRAINZ,
                    id = "rg1",
                    title = "Dookie",
                    artist = "Green Day",
                    year = "1994",
                    type = "Album",
                    coverArtUrl = "https://coverartarchive.org/release-group/rg1/front-250",
                ),
                CoverArtCandidate(
                    service = CoverArtService.MUSIC_BRAINZ,
                    id = "rg2",
                    title = "Dookie",
                    artist = "A feat. B",
                    year = null,
                    type = null,
                    coverArtUrl = "https://coverartarchive.org/release-group/rg2/front-250",
                ),
            ),
            MusicBrainzSearch.candidates(request, body),
        )
    }

    @Test
    fun `recordings become the release groups they came out on, each once and dated by its earliest release`() {
        val request = MusicBrainzSearch.request(CoverArtQuery(artist = "", album = "", title = "Good Riddance"))!!
        val body = """
            {"recordings":[
              {"artist-credit":[{"name":"Green Day"}],"releases":[
                {"date":"1998-10-14","release-group":{"id":"single","title":"Good Riddance","primary-type":"Single"}},
                {"date":"1997-10-14","artist-credit":[{"name":"Green Day"}],"release-group":{"id":"nimrod","title":"Nimrod","primary-type":"Album"}}
              ]},
              {"artist-credit":[{"name":"Green Day"}],"releases":[
                {"date":"1997","release-group":{"id":"single","title":"Good Riddance","primary-type":"Single"}},
                {"date":"2001","artist-credit":[{"name":"Various Artists"}],"release-group":{"id":"hits","title":"Hits","primary-type":"Album"}},
                {"date":"2002"}
              ]}
            ]}
        """.trimIndent()

        assertEquals(
            listOf(
                "single" to "1997",
                "nimrod" to "1997",
                "hits" to "2001",
            ),
            MusicBrainzSearch.candidates(request, body).map { it.id to it.year },
        )
        assertEquals("Various Artists", MusicBrainzSearch.candidates(request, body).last().artist)
    }
}
