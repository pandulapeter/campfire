/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.catalogue

import com.pandulapeter.campfire.data.model.domain.SongDetailsQuery
import com.pandulapeter.campfire.data.repository.api.SongDetailsRepository
import com.pandulapeter.campfire.domain.api.catalogue.SearchSongDetailsUseCase
import org.koin.core.annotation.Factory

@Factory
internal class SearchSongDetailsUseCaseImpl(
    private val songDetailsRepository: SongDetailsRepository,
) : SearchSongDetailsUseCase {

    override operator fun invoke(query: SongDetailsQuery) = songDetailsRepository.searchSongDetails(query)
}
