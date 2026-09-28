/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.CoverArtQuery
import com.pandulapeter.campfire.data.model.domain.CoverArtSearchResults
import kotlinx.coroutines.flow.Flow

interface SearchCoverArtUseCase {

    /**
     * Asks every cover search service about [query] at once, emitting where the search is each time one of them has
     * to wait or answers — the records found so far, each with the address of its front cover, and which services are
     * still being waited for or could not be reached — and completing once they all have. Nothing is thrown for a
     * service that fails; collecting the flow runs the search and cancelling it stops the search.
     */
    operator fun invoke(query: CoverArtQuery): Flow<CoverArtSearchResults>
}
