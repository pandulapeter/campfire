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
 * The parts of MusicBrainz's search answers Campfire reads. Everything is defaulted and unknown keys are ignored, like
 * the Dropbox models, so that a field added or dropped on the other side never turns into a search the user sees fail.
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
    @SerialName("first-release-date") val firstReleaseDate: String? = null,
    @SerialName("artist-credit") val artistCredit: List<MusicBrainzArtistCredit> = emptyList(),
)

@Serializable
internal data class MusicBrainzRecording(
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
