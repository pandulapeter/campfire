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
import com.pandulapeter.campfire.domain.api.useCases.DeleteSongUseCase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory

@Factory
class DeleteSongUseCaseImpl internal constructor(
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : DeleteSongUseCase {

    /**
     * A deleted song must not stay behind as a dangling entry: every setlist that held it and its saved transposition
     * are cleaned up too, so that nothing refers to a file that is no longer there. Once the file is gone that walk is
     * not cancellable, so a screen going away half way through cannot leave the rest of it undone, and it is the
     * rename's walk (`followSongReferences`), so one reference that cannot be removed does not stop the others.
     */
    override suspend operator fun invoke(fileName: String): Boolean {
        songRepository.deleteSong(fileName)
        return withContext(NonCancellable) {
            followSongReferences(setlistRepository, userPreferencesRepository, fileName = fileName, newFileName = null)
        }
    }
}
