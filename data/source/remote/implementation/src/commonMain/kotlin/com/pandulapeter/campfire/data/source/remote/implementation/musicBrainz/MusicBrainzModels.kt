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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The parts of MusicBrainz's search and lookup answers Campfire reads. Everything is defaulted and unknown keys are
 * ignored, so that a field added or dropped on the other side never turns into a search the user sees fail.
 */
@Serializable
internal data class MusicBrainzReleaseGroupSearchResponse(
    @SerialName("release-groups") val releaseGroups: List<MusicBrainzReleaseGroup> = emptyList(),
)

@Serializable
internal data class MusicBrainzRecordingSearchResponse(
    val recordings: List<MusicBrainzRecording> = emptyList(),
)

@Serializable
internal data class MusicBrainzReleaseGroup(
    val id: String = "",
    val title: String = "",
    @SerialName("primary-type") val primaryType: String? = null,
    @SerialName("secondary-types") val secondaryTypes: List<String> = emptyList(),
    @SerialName("first-release-date") val firstReleaseDate: String? = null,
    @SerialName("artist-credit") val artistCredit: List<MusicBrainzArtistCredit> = emptyList(),
)

@Serializable
internal data class MusicBrainzRecording(
    val id: String = "",
    val title: String = "",
    val score: Int = 0,
    /** In milliseconds. */
    val length: Long? = null,
    @SerialName("first-release-date") val firstReleaseDate: String? = null,
    @SerialName("artist-credit") val artistCredit: List<MusicBrainzArtistCredit> = emptyList(),
    val releases: List<MusicBrainzRelease> = emptyList(),
)

@Serializable
internal data class MusicBrainzRelease(
    val date: String? = null,
    @SerialName("artist-credit") val artistCredit: List<MusicBrainzArtistCredit> = emptyList(),
    @SerialName("release-group") val releaseGroup: MusicBrainzReleaseGroup? = null,
)

@Serializable
internal data class MusicBrainzArtistCredit(
    val name: String = "",
    @SerialName("joinphrase") val joinPhrase: String = "",
)

/** A recording looked up with its work relations, and the work's own relations with them. */
@Serializable
internal data class MusicBrainzRecordingLookup(
    val relations: List<MusicBrainzRelation> = emptyList(),
)

@Serializable
internal data class MusicBrainzRelation(
    val type: String = "",
    val artist: MusicBrainzArtist? = null,
    val work: MusicBrainzWork? = null,
)

@Serializable
internal data class MusicBrainzArtist(
    val name: String = "",
)

@Serializable
internal data class MusicBrainzWork(
    val languages: List<String> = emptyList(),
    val relations: List<MusicBrainzRelation> = emptyList(),
)
