/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.SongDetailsCandidate
import com.pandulapeter.campfire.data.repository.api.SongDetailsRepository
import com.pandulapeter.campfire.domain.api.useCases.LoadSongCreditsUseCase
import org.koin.core.annotation.Factory

@Factory
internal class LoadSongCreditsUseCaseImpl(
    private val songDetailsRepository: SongDetailsRepository,
) : LoadSongCreditsUseCase {

    override operator fun invoke(candidate: SongDetailsCandidate) = songDetailsRepository.loadSongCredits(candidate)
}
