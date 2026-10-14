/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.catalogue.CatalogueSearchResults
import com.pandulapeter.campfire.data.model.catalogue.SongCreditsState
import com.pandulapeter.campfire.data.model.catalogue.SongDetailsCandidate
import com.pandulapeter.campfire.data.model.catalogue.SongDetailsQuery
import kotlinx.coroutines.flow.Flow

/**
 * The song details lookup: the recordings a song's title names in the music catalogues, and who wrote the one the user
 * picks. Nothing is kept — not an answer, not a question — so asking again is asking again.
 */
interface SongDetailsRepository {

    /**
     * Asks every catalogue about [query] at once, and emits where the search is: first with every catalogue pending,
     * then again whenever one of them has to wait or answers. A catalogue that fails is reported in
     * [CatalogueSearchResults.failed] rather than thrown, so the flow only ever completes, once the last one has
     * answered; collecting it is what runs the search, and cancelling the collection stops it.
     */
    fun searchSongDetails(query: SongDetailsQuery): Flow<CatalogueSearchResults<SongDetailsCandidate>>

    /**
     * Asks the catalogue that found [candidate] who wrote it, emitting [SongCreditsState.Loading] first, then
     * [SongCreditsState.Busy] whenever the catalogue has to be waited for, and last the answer: what it says, that it
     * never names songwriters, or that it could not be asked. Never throws; cancelling the collection stops the request.
     */
    fun loadSongCredits(candidate: SongDetailsCandidate): Flow<SongCreditsState>
}
