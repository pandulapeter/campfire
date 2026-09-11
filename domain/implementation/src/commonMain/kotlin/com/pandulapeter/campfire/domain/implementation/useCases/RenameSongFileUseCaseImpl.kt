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
import com.pandulapeter.campfire.domain.api.useCases.RenameSongFileUseCase

class RenameSongFileUseCaseImpl internal constructor(
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : RenameSongFileUseCase {

    /**
     * The mirror image of `DeleteSongUseCaseImpl`: the same two places refer to a song by its file name, and where a
     * deletion drops those references a rename follows them. The file moves first, so that nothing is ever pointed
     * at a name that does not exist yet - and if the move fails there is nothing to undo.
     */
    override suspend operator fun invoke(song: Song): String? {
        val renamed = songRepository.renameSong(song)?.fileName ?: return null
        setlistRepository.loadSetlistsIfNeeded().orEmpty()
            .filter { setlist -> setlist.entries.any { it.songFileName == song.fileName } }
            .forEach { setlist ->
                setlistRepository.saveSetlist(
                    setlist.copy(
                        entries = setlist.entries.map { entry ->
                            if (entry.songFileName == song.fileName) entry.copy(songFileName = renamed) else entry
                        },
                    )
                )
            }
        userPreferencesRepository.loadUserPreferencesIfNeeded()
            ?.takeIf { song.fileName in it.transpositions }
            ?.let { preferences ->
                val transposition = preferences.transpositions.getValue(song.fileName)
                userPreferencesRepository.saveUserPreferences(
                    preferences.copy(transpositions = preferences.transpositions - song.fileName + (renamed to transposition))
                )
            }
        return renamed
    }
}
