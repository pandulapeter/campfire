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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class LoadScreenDataUseCaseImpl internal constructor(
    private val setlistRepository: SetlistRepository,
    private val songRepository: SongRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : LoadScreenDataUseCase {

    /**
     * Every source is loaded even if another one has already failed, so that one broken part of the screen does not
     * keep the rest of it empty: the repositories never throw for a failed read, each reports its own failure through
     * its `DataState`. The three run side by side in the caller's own scope, so a caller that goes away takes the
     * reads with it rather than leaving them running in a scope nothing ever cancels.
     */
    override suspend operator fun invoke(isRescan: Boolean) {
        coroutineScope {
            listOf(
                async { if (isRescan) songRepository.rescan() else songRepository.loadSongsIfNeeded() },
                async { if (isRescan) setlistRepository.rescan() else setlistRepository.loadSetlistsIfNeeded() },
                async { userPreferencesRepository.loadUserPreferencesIfNeeded() },
            ).awaitAll()
        }
    }
}
