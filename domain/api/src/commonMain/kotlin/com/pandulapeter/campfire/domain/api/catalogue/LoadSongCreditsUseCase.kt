/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.catalogue

import com.pandulapeter.campfire.data.model.catalogue.SongCreditsState
import com.pandulapeter.campfire.data.model.catalogue.SongDetailsCandidate
import kotlinx.coroutines.flow.Flow

interface LoadSongCreditsUseCase {

    /**
     * Asks the catalogue that found [candidate] who wrote it, emitting where the question is and ending with its
     * answer, see [SongCreditsState]. Never throws; cancelling the collection stops the request.
     */
    operator fun invoke(candidate: SongDetailsCandidate): Flow<SongCreditsState>
}
