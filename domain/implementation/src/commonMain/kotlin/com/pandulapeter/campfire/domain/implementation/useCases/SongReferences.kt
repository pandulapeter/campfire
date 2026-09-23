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
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import kotlinx.coroutines.CancellationException

/**
 * Everything in the library that refers to a song by its file name - the setlists holding it and its saved
 * transposition - moved to [newFileName], or dropped where that is null. The walk a rename and a deletion both make
 * once the file itself has moved or gone, which is why it is shared: neither can be undone at that point, so every
 * reference is attempted even after one fails, and whether all of them followed is the answer rather than an
 * exception. The caller runs it [kotlinx.coroutines.NonCancellable], for the same reason.
 *
 * Which setlists name the song is asked of the files ([SetlistRepository.loadSetlistFileNamesNaming]) rather than of
 * the cache, which does not know a setlist sync or an import wrote since the last rescan; not being able to ask counts
 * as a reference that did not follow.
 */
internal suspend fun followSongReferences(
    setlistRepository: SetlistRepository,
    userPreferencesRepository: UserPreferencesRepository,
    fileName: String,
    newFileName: String?,
): Boolean {
    val failures = mutableListOf<Exception>()
    suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        failures += exception
        null
    }
    attempt { setlistRepository.loadSetlistFileNamesNaming(fileName) }.orEmpty().forEach { setlistFileName ->
        attempt {
            setlistRepository.updateSetlist(setlistFileName) { latest ->
                latest.copy(
                    entries = if (newFileName == null) {
                        latest.entries.filterNot { it.songFileName == fileName }
                    } else {
                        latest.entries.map { entry -> if (entry.songFileName == fileName) entry.copy(songFileName = newFileName) else entry }
                    },
                )
            }
        }
    }
    attempt {
        userPreferencesRepository.loadUserPreferencesIfNeeded()
            ?.takeIf { fileName in it.transpositions }
            ?.let { preferences ->
                val transpositions = preferences.transpositions - fileName
                userPreferencesRepository.saveUserPreferences(
                    preferences.copy(
                        transpositions = if (newFileName == null) {
                            transpositions
                        } else {
                            transpositions + (newFileName to preferences.transpositions.getValue(fileName))
                        },
                    ),
                )
            }
    }
    failures.forEach { println("A reference to \"$fileName\" could not be updated: ${it.message}") }
    return failures.isEmpty()
}
