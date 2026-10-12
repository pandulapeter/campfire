/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api

import com.pandulapeter.campfire.data.model.domain.Catalogue
import com.pandulapeter.campfire.data.model.domain.SongCredits
import com.pandulapeter.campfire.data.model.domain.SongDetailsCandidate
import com.pandulapeter.campfire.data.model.domain.SongDetailsQuery

/**
 * Finds the recordings of a song in one catalogue, [catalogue], each with what it would fill the song's header with,
 * for the user to pick one from — and, for the one picked, who wrote it. The same catalogues answer the cover search,
 * and the same rules hold as on [CoverArtSearchRemoteSource]: an implementation spaces its own requests where its
 * catalogue asks for that, and shares that pace with the cover search of the same catalogue, since the catalogue counts
 * the app's requests and not one search's; it retries a refusal by itself and tells the caller through `onBusy` each
 * time it has to wait. Several of these are asked side by side, so none may assume it is the only one.
 */
interface SongDetailsSearchRemoteSource {

    /** The catalogue this source asks, which is also what every candidate it finds carries. */
    val catalogue: Catalogue

    /**
     * The recordings [query] finds, most relevant first, or an empty list for a query that is not
     * [SongDetailsQuery.isSearchable] or matches nothing. Throws [CatalogueSearchException] when the catalogue cannot
     * be reached or keeps refusing; a cancellation is thrown as it is.
     */
    suspend fun searchSongDetails(query: SongDetailsQuery, onBusy: () -> Unit): List<SongDetailsCandidate>

    /**
     * Who wrote [candidate], one of this source's own, or null where the catalogue never names songwriters — answered
     * without a request, since there is nothing to ask. Throws as [searchSongDetails] does.
     */
    suspend fun loadSongCredits(candidate: SongDetailsCandidate, onBusy: () -> Unit): SongCredits?
}

/**
 * Every song details source, as one dependency, in the order their candidates are preferred where two answer at once.
 * A type of its own rather than a `List<SongDetailsSearchRemoteSource>` for the reason given on [SyncProviders].
 */
class SongDetailsSearchRemoteSources(
    val all: List<SongDetailsSearchRemoteSource>,
)
