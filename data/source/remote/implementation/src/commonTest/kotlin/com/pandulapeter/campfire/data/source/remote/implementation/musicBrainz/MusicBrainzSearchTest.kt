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

import com.pandulapeter.campfire.data.model.domain.Catalogue
import com.pandulapeter.campfire.data.model.domain.CoverArtCandidate
import com.pandulapeter.campfire.data.model.domain.CoverArtQuery
import com.pandulapeter.campfire.data.model.domain.SongCredits
import com.pandulapeter.campfire.data.model.domain.SongDetailsQuery
import com.pandulapeter.campfire.data.source.remote.implementation.musicBrainz.MusicBrainzSearch.quoted
import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

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
                    catalogue = Catalogue.MUSIC_BRAINZ,
                    id = "rg1",
                    title = "Dookie",
                    artist = "Green Day",
                    year = "1994",
                    type = "Album",
                    coverArtUrl = "https://coverartarchive.org/release-group/rg1/front-250",
                ),
                CoverArtCandidate(
                    catalogue = Catalogue.MUSIC_BRAINZ,
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

    @Test
    fun `a details lookup searches the recordings by title, narrowed by the artist and the album`() {
        val url = Url(MusicBrainzSearch.songDetailsUrl(SongDetailsQuery(title = "Help!", artist = "The Beatles", album = "Help"))!!)

        assertEquals("/ws/2/recording", url.encodedPath)
        assertEquals("""recording:"Help!" AND artist:"The Beatles" AND release:"Help"""", url.parameters["query"])
        assertEquals(
            """recording:"Help!"""",
            Url(MusicBrainzSearch.songDetailsUrl(SongDetailsQuery(title = "Help!", artist = " ", album = ""))!!).parameters["query"],
        )
    }

    @Test
    fun `a details lookup without a title asks nothing`() {
        assertNull(MusicBrainzSearch.songDetailsUrl(SongDetailsQuery(title = " ", artist = "Queen", album = "")))
    }

    @Test
    fun `recordings become candidates with their year, duration and studio album`() {
        val body = """
            {"recordings":[
              {"id":"r1","score":100,"title":"Kicsi gesztenye","length":276000,"first-release-date":"2001-12",
               "artist-credit":[{"name":"TNT","joinphrase":" feat. "},{"name":"Somebody"}],"releases":[
                {"date":"2003","release-group":{"title":"Best of","primary-type":"Album","secondary-types":["Compilation"]}},
                {"date":"2001-12","release-group":{"title":"Másképpen","primary-type":"Album"}},
                {"date":"2002","release-group":{"title":"Kicsi gesztenye","primary-type":"Single"}}
              ]}
            ]}
        """.trimIndent()

        val candidate = MusicBrainzSearch.songDetailsCandidates(body).single()

        assertEquals(Catalogue.MUSIC_BRAINZ, candidate.catalogue)
        assertEquals("Kicsi gesztenye", candidate.title)
        assertEquals("TNT feat. Somebody", candidate.artist)
        assertEquals("Másképpen", candidate.album)
        assertEquals("2001", candidate.year)
        assertEquals(276_000.milliseconds, candidate.duration)
    }

    @Test
    fun `a recording only on singles, live records and compilations has no album`() {
        val body = """
            {"recordings":[{"id":"r1","score":100,"title":"Wonderwall","releases":[
              {"date":"1995","release-group":{"title":"Wonderwall","primary-type":"Single"}},
              {"date":"1996","release-group":{"title":"Live","primary-type":"Album","secondary-types":["Live"]}}
            ]}]}
        """.trimIndent()

        val candidate = MusicBrainzSearch.songDetailsCandidates(body).single()

        assertNull(candidate.album)
        assertNull(candidate.duration)
        assertEquals("1995", candidate.year)
    }

    @Test
    fun `recordings below the score are other songs, and among one score the studio ones come first`() {
        val body = """
            {"recordings":[
              {"id":"live","score":100,"title":"Wonderwall","first-release-date":"1996","releases":[{"release-group":{"title":"Live","primary-type":"Album","secondary-types":["Live"]}}]},
              {"id":"single","score":100,"title":"Wonderwall","releases":[{"release-group":{"title":"Wonderwall","primary-type":"Single"}}]},
              {"id":"studio","score":100,"title":"Wonderwall","releases":[{"release-group":{"title":"Morning Glory","primary-type":"Album"}}]},
              {"id":"edit","score":78,"title":"Wonderwall (radio edit)","releases":[{"release-group":{"title":"Morning Glory","primary-type":"Album"}}]},
              {"id":"other","score":60,"title":"Wonder","releases":[{"release-group":{"title":"Morning Glory","primary-type":"Album"}}]}
            ]}
        """.trimIndent()

        assertEquals(listOf("studio", "single", "live", "edit"), MusicBrainzSearch.songDetailsCandidates(body).map { it.id })
    }

    @Test
    fun `recordings that would fill in the same values are offered once`() {
        val body = """
            {"recordings":[
              {"id":"a","score":100,"title":"Wonderwall","length":258907,"first-release-date":"1995"},
              {"id":"b","score":100,"title":"Wonderwall","length":258773,"first-release-date":"1995"},
              {"id":"c","score":100,"title":"Wonderwall","length":251026,"first-release-date":"1995"}
            ]}
        """.trimIndent()

        assertEquals(listOf("a", "c"), MusicBrainzSearch.songDetailsCandidates(body).map { it.id })
    }

    @Test
    fun `the songwriters are read from the work the recording performs`() {
        val body = """
            {"relations":[
              {"type":"producer","artist":{"name":"Owen Morris"}},
              {"type":"performance","work":{"languages":["eng"],"relations":[
                {"type":"composer","artist":{"name":"Noel Gallagher"}},
                {"type":"lyricist","artist":{"name":"Noel Gallagher"}},
                {"type":"lyricist","artist":{"name":"Liam Gallagher"}},
                {"type":"other version","work":{}}
              ]}}
            ]}
        """.trimIndent()

        assertEquals(SongCredits(composer = "Noel Gallagher", lyricist = "Noel Gallagher, Liam Gallagher"), MusicBrainzSearch.songCredits(body))
    }

    @Test
    fun `a writer stands in for the composer and the lyricist, but not for the lyricist of a work without lyrics`() {
        fun body(language: String) = """
            {"relations":[{"type":"performance","work":{"languages":["$language"],"relations":[
              {"type":"writer","artist":{"name":"Joseph Kosma"}}
            ]}}]}
        """.trimIndent()

        assertEquals(SongCredits(composer = "Joseph Kosma", lyricist = "Joseph Kosma"), MusicBrainzSearch.songCredits(body("fra")))
        assertEquals(SongCredits(composer = "Joseph Kosma", lyricist = null), MusicBrainzSearch.songCredits(body("zxx")))
    }

    @Test
    fun `a recording with no work names nobody`() {
        assertEquals(SongCredits(composer = null, lyricist = null), MusicBrainzSearch.songCredits("""{"relations":[]}"""))
    }
}
