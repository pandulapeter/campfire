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

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.models.SongFileRename
import com.pandulapeter.campfire.domain.api.useCases.RenameSongFileUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory

@Factory
class RenameSongFileUseCaseImpl internal constructor(
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : RenameSongFileUseCase {

    /**
     * The mirror image of `DeleteSongUseCaseImpl`: the same two places refer to a song by its file name, and where a
     * deletion drops those references a rename follows them. The file moves first, so that nothing is ever pointed
     * at a name that does not exist yet - and if the move fails there is nothing to undo.
     *
     * Once the file has moved there is no going back, so every reference is attempted even when an earlier one could
     * not be written, and whether any failed is reported together at the end, in the result rather than as an
     * exception: the move has happened, and the caller has to follow it either way. One setlist that cannot be saved
     * would otherwise leave every setlist after it, and the transposition, pointing at a name that is gone. For the
     * same reason the walk is not cancellable once the file has moved: a screen going away half way through would
     * leave the rest of the references on the old name just the same.
     */
    override suspend operator fun invoke(song: Song): SongFileRename? {
        val renamed = songRepository.renameSong(song)?.fileName ?: return null
        val haveReferencesFollowed = withContext(NonCancellable) { updateReferences(song = song, renamed = renamed) }
        return SongFileRename(fileName = renamed, haveReferencesFollowed = haveReferencesFollowed)
    }

    private suspend fun updateReferences(song: Song, renamed: String): Boolean {
        val failures = mutableListOf<Exception>()
        setlistRepository.loadSetlistsIfNeeded().orEmpty()
            .filter { setlist -> setlist.entries.any { it.songFileName == song.fileName } }
            .forEach { setlist ->
                attempt(failures) {
                    setlistRepository.updateSetlist(setlist.fileName) { latest ->
                        latest.copy(
                            entries = latest.entries.map { entry ->
                                if (entry.songFileName == song.fileName) entry.copy(songFileName = renamed) else entry
                            },
                        )
                    }
                }
            }
        attempt(failures) {
            userPreferencesRepository.loadUserPreferencesIfNeeded()
                ?.takeIf { song.fileName in it.transpositions }
                ?.let { preferences ->
                    val transposition = preferences.transpositions.getValue(song.fileName)
                    userPreferencesRepository.saveUserPreferences(
                        preferences.copy(transpositions = preferences.transpositions - song.fileName + (renamed to transposition))
                    )
                }
        }
        failures.forEach { println("A reference to the renamed song could not be updated: ${it.message}") }
        return failures.isEmpty()
    }

    private suspend fun attempt(failures: MutableList<Exception>, block: suspend () -> Unit) {
        try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            failures += exception
        }
    }
}
