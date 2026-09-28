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

/**
 * Finds the records a song may have come out on, each with the address of its front cover, for the user to pick one
 * from. MusicBrainz is the one implementation, and its rules shape the contract: it allows one request a second from
 * the whole app and answers faster ones with a refusal to be waited out, so an implementation spaces its own requests
 * and retries a refusal by itself, telling the caller through [onBusy] each time it has to wait.
 */
interface CoverArtSearchRemoteSource {

    /**
     * The candidates for [query], most relevant first, or an empty list for a query that is not
     * [CoverArtQuery.isSearchable] or matches nothing. Throws [CoverArtSearchException] when the service cannot be
     * reached or keeps refusing; a cancellation is thrown as it is.
     */
    suspend fun searchCoverArt(query: CoverArtQuery, onBusy: () -> Unit): List<CoverArtCandidate>
}

/** The cover search could not be answered: no network, or the service kept refusing past every retry. */
class CoverArtSearchException(message: String, cause: Throwable? = null) : Exception(message, cause)
