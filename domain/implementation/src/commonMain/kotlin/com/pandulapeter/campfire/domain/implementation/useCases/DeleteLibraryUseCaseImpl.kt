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

import com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.SyncRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.useCases.DeleteLibraryUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory

@Factory
class DeleteLibraryUseCaseImpl internal constructor(
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val syncRepository: SyncRepository,
) : DeleteLibraryUseCase {

    /**
     * The setlists go whether or not every song could, and the preferences are cleaned up whether or not every setlist
     * could, so that a failure part of the way leaves as little behind as it can. Not cancellable once it has started,
     * for the same reason: a library half deleted by a screen going away would be the worst of both.
     *
     * The sync run starts whatever could be deleted, since what is gone from here is gone either way and the folder
     * should follow it; a file that could not be deleted is still here, so the run leaves its copy in the folder alone.
     */
    override suspend operator fun invoke() = withContext(NonCancellable) {
        // A run that is going would keep the deletion's own run from starting, and its answer with it; what it had
        // already moved stays moved, and the run below carries the rest.
        syncRepository.cancelSynchronization()
        val songsFailure = runCatchingFailure { songRepository.deleteAllSongs() }
        val setlistsFailure = runCatchingFailure { setlistRepository.deleteAllSetlists() }
        userPreferencesRepository.updateUserPreferences { preferences ->
            preferences.copy(transpositions = emptyMap(), tempos = emptyMap(), foldedSections = emptyMap())
        }
        if (!syncRepository.synchronize(SyncDeletionPolicy.DELETE_REMOTELY)) {
            println("The deletion's sync run could not be started, a run was already going.")
        }
        (songsFailure ?: setlistsFailure)?.let { throw it } ?: Unit
    }

    private suspend fun runCatchingFailure(block: suspend () -> Unit): Exception? = try {
        block()
        null
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        exception
    }
}
