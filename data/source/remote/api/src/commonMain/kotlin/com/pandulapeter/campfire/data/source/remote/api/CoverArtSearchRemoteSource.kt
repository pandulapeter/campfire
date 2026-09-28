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

import com.pandulapeter.campfire.data.model.domain.CoverArtCandidate
import com.pandulapeter.campfire.data.model.domain.CoverArtQuery
import com.pandulapeter.campfire.data.model.domain.CoverArtService

/**
 * Finds the records a song may have come out on in one catalogue, [service], each with the address of its front cover,
 * for the user to pick one from. The contract is shaped by the strictest of them: MusicBrainz allows one request a
 * second from the whole app and answers faster ones with a refusal to be waited out, so an implementation spaces its
 * own requests where its service asks for that and retries a refusal by itself, telling the caller through [onBusy]
 * each time it has to wait. Several of these are asked side by side, so none may assume it is the only one.
 */
interface CoverArtSearchRemoteSource {

    /** The catalogue this source asks, which is also what every candidate it finds carries. */
    val service: CoverArtService

    /**
     * The candidates for [query], most relevant first, or an empty list for a query that is not
     * [CoverArtQuery.isSearchable] or matches nothing. Throws [CoverArtSearchException] when the service cannot be
     * reached or keeps refusing; a cancellation is thrown as it is.
     */
    suspend fun searchCoverArt(query: CoverArtQuery, onBusy: () -> Unit): List<CoverArtCandidate>
}

/**
 * Every cover search source, as one dependency, in the order their candidates are preferred where two answer at once.
 * A type of its own rather than a `List<CoverArtSearchRemoteSource>` for the reason given on [SyncProviders].
 */
class CoverArtSearchRemoteSources(
    val all: List<CoverArtSearchRemoteSource>,
)

/** The cover search could not be answered: no network, or the service kept refusing past every retry. */
class CoverArtSearchException(message: String, cause: Throwable? = null) : Exception(message, cause)
