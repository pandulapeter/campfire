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

import com.pandulapeter.campfire.data.model.catalogue.Catalogue
import com.pandulapeter.campfire.data.model.catalogue.CoverArtCandidate
import com.pandulapeter.campfire.data.model.catalogue.CoverArtQuery
import com.pandulapeter.campfire.data.model.catalogue.SongCredits
import com.pandulapeter.campfire.data.model.catalogue.SongDetailsCandidate
import com.pandulapeter.campfire.data.model.catalogue.SongDetailsQuery
import com.pandulapeter.campfire.data.source.remote.implementation.network.urlEncode
import com.pandulapeter.campfire.data.source.remote.implementation.songDetails.withoutRepeats
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

/**
 * The questions the cover search and the song details lookup ask MusicBrainz, and how its answers become candidates. Pure, so that the part that
 * is easy to get wrong without noticing — the query syntax, and which record an answer stands for — is tested.
 *
 * With an album, the release groups of that name are searched for directly: a release group is every edition of one
 * record, which is what a cover is picked for. With only a title, the recordings of that name are, and the release
 * groups they came out on are the candidates, in the order the recordings are ranked in; that is where a single, the
 * album it was taken from and the compilations it ended up on are all found. Either is narrowed by the artist where
 * there is one. Every candidate's cover is the Cover Art Archive's front image of its release group, which the
 * archive picks from the group's releases, so a group with no cover at all is only found out when its thumbnail
 * answers 404.
 *
 * The details lookup searches the recordings by title, narrowed by the artist and the album, and each recording is a
 * candidate. MusicBrainz scores a live bootleg of a hit as high as its studio recording and lists it first as often,
 * so among recordings of one score the ones on a studio album come first, then the ones on a single or an EP. The
 * songwriters are the work's the recording is a performance of, which one lookup of the recording brings along.
 */
internal object MusicBrainzSearch {

    /** The request [query] is answered by, or null where there is nothing to search by. */
    fun request(query: CoverArtQuery): Request? {
        val artist = query.artist.trim()
        val album = query.album.trim()
        val title = query.title.trim()
        val (entity, lucene) = when {
            album.isNotEmpty() -> Entity.RELEASE_GROUP to buildString {
                append("releasegroup:").append(album.quoted())
                if (artist.isNotEmpty()) append(" AND artist:").append(artist.quoted())
            }
            title.isNotEmpty() -> Entity.RECORDING to recordingQuery(title = title, artist = artist, album = "")
            else -> return null
        }
        return Request(
            entity = entity,
            url = "$BASE_URL/${entity.path}?query=${lucene.urlEncode()}&fmt=json&limit=$RESULT_LIMIT",
        )
    }

    /** The address the details lookup of [query] is answered at, or null where there is nothing to search by. */
    fun songDetailsUrl(query: SongDetailsQuery): String? {
        if (!query.isSearchable) return null
        val lucene = recordingQuery(title = query.title.trim(), artist = query.artist.trim(), album = query.album.trim())
        return "$BASE_URL/${Entity.RECORDING.path}?query=${lucene.urlEncode()}&fmt=json&limit=$SONG_DETAILS_RESULT_LIMIT"
    }

    /** The Lucene expression for the recordings of [title], narrowed by [artist] and [album] where they are not empty. */
    fun recordingQuery(title: String, artist: String, album: String) = buildString {
        append("recording:").append(title.quoted())
        if (artist.isNotEmpty()) append(" AND artist:").append(artist.quoted())
        if (album.isNotEmpty()) append(" AND release:").append(album.quoted())
    }

    /**
     * The recordings in the answer [body] to a details lookup worth offering, in the order described on this object,
     * one for every set of values they would fill in.
     */
    fun songDetailsCandidates(body: String): List<SongDetailsCandidate> = json.decodeFromString<MusicBrainzRecordingSearchResponse>(body)
        .recordings
        .filter { it.score >= MINIMUM_SCORE && it.id.isNotBlank() }
        .sortedWith(compareByDescending<MusicBrainzRecording> { it.score }.thenBy { it.releaseRank() })
        .map { recording ->
            SongDetailsCandidate(
                catalogue = Catalogue.MUSIC_BRAINZ,
                id = recording.id,
                title = recording.title,
                artist = recording.artistCredit.joined(),
                album = recording.albumTitle(),
                year = recording.firstReleaseDate.year() ?: recording.releases.mapNotNull { it.date.year() }.minOrNull(),
                duration = recording.length?.takeIf { it > 0 }?.milliseconds,
            )
        }
        .withoutRepeats()

    /** The address of the recording [recordingId] looked up with its work and the work's songwriters. */
    fun songCreditsUrl(recordingId: String) = "$BASE_URL/${Entity.RECORDING.path}/${recordingId.urlEncode()}?inc=$CREDITS_INCLUDES&fmt=json"

    /**
     * The songwriters of the works the recording looked up in [body] is a performance of. The composer is credited as
     * one, or where nobody is, as a writer; the lyricist the same way, as a lyricist or a librettist — except that a
     * work without lyrics (language `zxx`) has no lyricist, however its writer is credited.
     */
    fun songCredits(body: String): SongCredits {
        val works = json.decodeFromString<MusicBrainzRecordingLookup>(body).relations
            .filter { it.type == RELATION_PERFORMANCE }
            .mapNotNull { it.work }
        fun namesOf(types: Set<String>, inWorks: List<MusicBrainzWork> = works) = inWorks
            .flatMap { work -> work.relations.filter { it.type in types }.mapNotNull { it.artist?.name?.trim()?.takeIf(String::isNotEmpty) } }
            .distinct()
        val composers = namesOf(COMPOSER_TYPES).ifEmpty { namesOf(WRITER_TYPES) }
        val lyricists = namesOf(LYRICIST_TYPES).ifEmpty { namesOf(WRITER_TYPES, works.filter { NO_LYRICS !in it.languages }) }
        return SongCredits(
            composer = composers.joinToString(", ").takeIf { it.isNotEmpty() },
            lyricist = lyricists.joinToString(", ").takeIf { it.isNotEmpty() },
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
                catalogue = Catalogue.MUSIC_BRAINZ,
                id = id,
                title = title,
                artist = artistCredit.joined(),
                year = year,
                type = primaryType?.takeIf { type -> type.isNotBlank() },
                coverArtUrl = coverArtUrl(id),
            )
        }

    private fun List<MusicBrainzArtistCredit>.joined() = joinToString("") { credit -> credit.name + credit.joinPhrase }.trim()

    /** How far down its score's recordings [this] is put: on a studio album first, then on a single or an EP. */
    private fun MusicBrainzRecording.releaseRank() = when {
        releases.any { it.releaseGroup?.isStudioAlbum == true } -> 0
        releases.any { it.releaseGroup?.isSingleOrEp == true } -> 1
        else -> 2
    }

    /**
     * The record [this] is best known from: its earliest studio album, else its earliest EP, else none — a single is
     * named after the song, and a live record or a compilation is not where a songbook files it.
     */
    private fun MusicBrainzRecording.albumTitle(): String? {
        val groups = releases.sortedBy { it.date?.takeIf(String::isNotBlank) ?: UNKNOWN_DATE }.mapNotNull { it.releaseGroup }
        return (groups.firstOrNull { it.isStudioAlbum } ?: groups.firstOrNull { it.primaryType == TYPE_EP && it.secondaryTypes.isEmpty() })
            ?.title?.takeIf { it.isNotBlank() }
    }

    private val MusicBrainzReleaseGroup.isStudioAlbum get() = primaryType == TYPE_ALBUM && secondaryTypes.isEmpty()

    private val MusicBrainzReleaseGroup.isSingleOrEp get() = (primaryType == TYPE_SINGLE || primaryType == TYPE_EP) && secondaryTypes.isEmpty()

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

    /** More than the cover search's: the same recording comes back once per edition, and the repeats are dropped. */
    private const val SONG_DETAILS_RESULT_LIMIT = 50

    /** MusicBrainz's own relevance, 100 for an exact match; below this the list is other songs. */
    private const val MINIMUM_SCORE = 70
    private const val CREDITS_INCLUDES = "artist-rels+work-rels+work-level-rels"
    private const val RELATION_PERFORMANCE = "performance"
    private val COMPOSER_TYPES = setOf("composer")
    private val LYRICIST_TYPES = setOf("lyricist", "librettist")
    private val WRITER_TYPES = setOf("writer")
    private const val NO_LYRICS = "zxx"
    private const val TYPE_ALBUM = "Album"
    private const val TYPE_SINGLE = "Single"
    private const val TYPE_EP = "EP"

    /** Sorts after every date, which MusicBrainz writes as digits and dashes. */
    private const val UNKNOWN_DATE = "~"
}
