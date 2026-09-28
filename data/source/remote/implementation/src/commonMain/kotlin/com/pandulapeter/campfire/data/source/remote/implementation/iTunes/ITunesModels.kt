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

import kotlinx.serialization.Serializable

/**
 * The parts of the iTunes Search API's answer Campfire reads, defaulted and read with unknown keys ignored like the
 * MusicBrainz models.
 */
@Serializable
internal data class ITunesSearchResponse(
    val results: List<ITunesTrack> = emptyList(),
)

@Serializable
internal data class ITunesTrack(
    val wrapperType: String = "",
    val collectionId: Long = 0,
    val collectionName: String = "",
    val artistName: String = "",
    val collectionArtistName: String? = null,
    val releaseDate: String? = null,
    val artworkUrl100: String? = null,
)
