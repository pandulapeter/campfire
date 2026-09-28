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

import com.pandulapeter.campfire.data.model.domain.CoverArtCandidate
import com.pandulapeter.campfire.data.model.domain.CoverArtQuery

interface SearchCoverArtUseCase {

    /**
     * The records [query] may name, each with the address of its front cover, most relevant first; null when the
     * search could not be answered at all. [onBusy] is called every time the service asks to be given a moment and the
     * search waits before asking again, which can add up to a while.
     */
    suspend operator fun invoke(query: CoverArtQuery, onBusy: () -> Unit): List<CoverArtCandidate>?
}
