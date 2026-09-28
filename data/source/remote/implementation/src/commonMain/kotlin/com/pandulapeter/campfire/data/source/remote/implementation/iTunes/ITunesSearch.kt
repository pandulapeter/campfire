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
import com.pandulapeter.campfire.data.source.remote.implementation.network.urlEncode
import kotlinx.serialization.json.Json

/**
 * The question the cover search asks the iTunes Search API and how its answer becomes candidates. Pure, like
 * `MusicBrainzSearch`, and tested.
 *
 * The songs are what is searched, by the artist and the album — or the title where the album is empty — as one free
 * text term, and the albums they are on are the candidates, each once, in the order their songs are ranked in. The
 * API's own album search does not do: it matches a term against the album's name alone, so the artist in the term
 * finds nothing, and an album searched for without its artist is lost among everybody else's of that name. Apple
 * names a single `Title - Single` and an EP `Title - EP`, so that suffix is the only kind of record it tells apart.
 */
internal object ITunesSearch {

    /** The address [query] is answered at, or null where there is nothing to search by. */
    fun url(query: CoverArtQuery): String? {
        val record = query.album.trim().ifEmpty { query.title.trim() }
        if (record.isEmpty()) return null
        val term = listOf(query.artist.trim(), record).filter { it.isNotEmpty() }.joinToString(" ")
        return "$BASE_URL?term=${term.urlEncode()}&media=music&entity=song&limit=$SONG_LIMIT"
    }

    /** The albums the songs of the answer [body] are on, without repeats and in the order the songs were ranked. */
    fun candidates(body: String): List<CoverArtCandidate> = json.decodeFromString<ITunesSearchResponse>(body).results
        .filter { it.wrapperType == "track" && it.collectionId != 0L }
        .groupBy { it.collectionId }
        .mapNotNull { (collectionId, tracks) ->
            val track = tracks.first()
            val artworkUrl = track.artworkUrl100?.let(::coverArtUrl) ?: return@mapNotNull null
            val (title, type) = RECORD_TYPE_SUFFIXES.firstNotNullOfOrNull { (suffix, type) ->
                track.collectionName.takeIf { it.endsWith(suffix) }?.let { it.removeSuffix(suffix) to type }
            } ?: (track.collectionName to null)
            CoverArtCandidate(
                service = CoverArtService.ITUNES,
                id = collectionId.toString(),
                title = title,
                artist = track.collectionArtistName?.takeIf { it.isNotBlank() } ?: track.artistName,
                year = tracks.mapNotNull { it.releaseDate.year() }.minOrNull(),
                type = type,
                coverArtUrl = artworkUrl,
            )
        }
        .take(CANDIDATE_LIMIT)

    /**
     * The artwork [artworkUrl100] names at the size every cover is written at. Apple's image server renders the size
     * the last path segment asks for, so the 100 px thumbnail the answer names is the same image at another size — but
     * only in the form it hands out, which is why an address that does not end that way is not guessed at.
     */
    fun coverArtUrl(artworkUrl100: String) = ARTWORK_SIZE.find(artworkUrl100)?.let { match ->
        artworkUrl100.replaceRange(match.range, "/${COVER_SIZE}x${COVER_SIZE}bb.jpg")
    }

    /** The year of an iTunes date, which is a whole ISO timestamp. */
    private fun String?.year() = this?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val RECORD_TYPE_SUFFIXES = listOf(" - Single" to "Single", " - EP" to "EP")
    private val ARTWORK_SIZE = Regex("""/\d+x\d+bb\.(jpg|png)$""")
    private const val BASE_URL = "https://itunes.apple.com/search"
    private const val COVER_SIZE = 250

    /** Enough songs to find a couple dozen records among, since the same album comes back once for every song on it. */
    private const val SONG_LIMIT = 100
    private const val CANDIDATE_LIMIT = 25
}
