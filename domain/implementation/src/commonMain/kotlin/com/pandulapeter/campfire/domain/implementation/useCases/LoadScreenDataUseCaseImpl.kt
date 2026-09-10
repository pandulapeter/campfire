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

import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class LoadScreenDataUseCaseImpl internal constructor(
    private val setlistRepository: SetlistRepository,
    private val songRepository: SongRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : LoadScreenDataUseCase {

    private val scope = object : CoroutineScope {
        override val coroutineContext = SupervisorJob() + Dispatchers.Default
    }

    /**
     * Every source is loaded even if another one has already failed, so that one broken part of the screen does not
     * keep the rest of it empty; each repository reports its own failure through its `DataState`.
     */
    override suspend operator fun invoke(isRescan: Boolean) {
        with(scope) {
            listOf(
                async { if (isRescan) songRepository.rescan() else songRepository.loadSongsIfNeeded() },
                async { if (isRescan) setlistRepository.rescan() else setlistRepository.loadSetlistsIfNeeded() },
                async { userPreferencesRepository.loadUserPreferencesIfNeeded() }
            ).awaitAll()
        }
    }
}
