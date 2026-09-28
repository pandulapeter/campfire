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

import com.pandulapeter.campfire.chordpro.ChordProCoverArt
import com.pandulapeter.campfire.data.model.domain.CoverArtQuery
import com.pandulapeter.campfire.data.repository.api.CoverArtRepository
import com.pandulapeter.campfire.domain.api.useCases.GetCoverArtUseCase
import com.pandulapeter.campfire.domain.api.useCases.SearchCoverArtUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProCoverArtUseCase
import org.koin.core.annotation.Factory

@Factory
class SetChordProCoverArtUseCaseImpl internal constructor() : SetChordProCoverArtUseCase {

    override operator fun invoke(text: String, url: String?) = ChordProCoverArt.set(text, url)
}

@Factory
class GetCoverArtUseCaseImpl internal constructor(
    private val coverArtRepository: CoverArtRepository,
) : GetCoverArtUseCase {

    override suspend operator fun invoke(url: String) = coverArtRepository.getCoverArt(url)
}

@Factory
class SearchCoverArtUseCaseImpl internal constructor(
    private val coverArtRepository: CoverArtRepository,
) : SearchCoverArtUseCase {

    override operator fun invoke(query: CoverArtQuery) = coverArtRepository.searchCoverArt(query)
}
