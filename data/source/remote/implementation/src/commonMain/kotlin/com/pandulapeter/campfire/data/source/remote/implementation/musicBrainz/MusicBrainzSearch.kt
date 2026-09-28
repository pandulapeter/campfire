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
import com.pandulapeter.campfire.data.source.remote.implementation.network.urlEncode
import kotlinx.serialization.json.Json

/**
 * The questions the cover search asks MusicBrainz and how its answers become candidates. Pure, so that the part that
 * is easy to get wrong without noticing — the query syntax, and which record an answer stands for — is tested.
 *
 * With an album, the release groups of that name are searched for directly: a release group is every edition of one
 * record, which is what a cover is picked for. With only a title, the recordings of that name are, and the release
 * groups they came out on are the candidates, in the order the recordings are ranked in; that is where a single, the
 * album it was taken from and the compilations it ended up on are all found. Either is narrowed by the artist where
 * there is one. Every candidate's cover is the Cover Art Archive's front image of its release group, which the
 * archive picks from the group's releases, so a group with no cover at all is only found out when its thumbnail
 * answers 404.
 */
internal object MusicBrainzSearch {

    /** The request [query] is answered by, or null where there is nothing to search by. */
    fun request(query: CoverArtQuery): Request? {
        val artist = query.artist.trim()
        val album = query.album.trim()
        val title = query.title.trim()
        val (entity, field, value) = when {
            album.isNotEmpty() -> Triple(Entity.RELEASE_GROUP, "releasegroup", album)
            title.isNotEmpty() -> Triple(Entity.RECORDING, "recording", title)
            else -> return null
        }
        val lucene = buildString {
            append(field).append(':').append(value.quoted())
            if (artist.isNotEmpty()) append(" AND artist:").append(artist.quoted())
        }
        return Request(
            entity = entity,
            url = "$BASE_URL/${entity.path}?query=${lucene.urlEncode()}&fmt=json&limit=$RESULT_LIMIT",
        )
    }

    /** The candidates in the answer [body] to [request], without repeats, in the order MusicBrainz ranked them. */
    fun candidates(request: Request, body: String): List<CoverArtCandidate> = when (request.entity) {
        Entity.RELEASE_GROUP -> json.decodeFromString<MusicBrainzReleaseGroupSearchResponse>(body).releaseGroups
            .mapNotNull { group -> group.toCandidate(artistCredit = group.artistCredit, year = group.firstReleaseDate.year()) }

        Entity.RECORDING -> {
            val releases = json.decodeFromString<MusicBrainzRecordingSearchResponse>(body).recordings.flatMap { recording ->
                recording.releases.map { release -> release to release.artistCredit.ifEmpty { recording.artistCredit } }
            }
            // A group is found once for every release of it the recordings list, and its year is its earliest one's.
            releases.groupBy { (release, _) -> release.releaseGroup?.id.orEmpty() }
                .mapNotNull { (_, groupReleases) ->
                    val (release, artistCredit) = groupReleases.first()
                    release.releaseGroup?.toCandidate(
                        artistCredit = artistCredit,
                        year = groupReleases.mapNotNull { it.first.date.year() }.minOrNull(),
                    )
                }
        }
    }.distinctBy { it.id }

    /** The address of the front cover the Cover Art Archive keeps for the release group [id], at the size used everywhere. */
    fun coverArtUrl(id: String) = "https://coverartarchive.org/release-group/$id/front-250"

    private fun MusicBrainzReleaseGroup.toCandidate(artistCredit: List<MusicBrainzArtistCredit>, year: String?) =
        id.takeIf { it.isNotBlank() }?.let {
            CoverArtCandidate(
                id = id,
                title = title,
                artist = artistCredit.joinToString("") { credit -> credit.name + credit.joinPhrase }.trim(),
                year = year,
                type = primaryType?.takeIf { type -> type.isNotBlank() },
                coverArtUrl = coverArtUrl(id),
            )
        }

    /** The year of a MusicBrainz date, which may be a whole date, a month or a year, or nothing at all. */
    private fun String?.year() = this?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }

    /**
     * [this] as a Lucene phrase. Everything inside the quotes is taken literally except the quote itself and the
     * backslash that escapes it, so a title full of Lucene's operators (`AC/DC`, `Help!`, `(I Can't Get No)`) is
     * searched for as it is written.
     */
    internal fun String.quoted() = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    enum class Entity(val path: String) {
        RELEASE_GROUP("release-group"),
        RECORDING("recording"),
    }

    data class Request(
        val entity: Entity,
        val url: String,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private const val BASE_URL = "https://musicbrainz.org/ws/2"
    private const val RESULT_LIMIT = 25
}
