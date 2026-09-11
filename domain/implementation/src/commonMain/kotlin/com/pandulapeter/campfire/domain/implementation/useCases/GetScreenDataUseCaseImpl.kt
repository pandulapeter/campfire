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

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class GetScreenDataUseCaseImpl internal constructor(
    private val normalizeText: NormalizeTextUseCase,
    setlistRepository: SetlistRepository,
    songRepository: SongRepository,
    userPreferencesRepository: UserPreferencesRepository,
) : GetScreenDataUseCase {

    override operator fun invoke() = screenDataFlow

    private var cache: ScreenData? = null
    private val screenDataFlow = combine(
        setlistRepository.setlists,
        songRepository.songs,
        // Only the two preferences the list is built from: a preference that changes on every step of a transposition
        // (or on every frame of a pinch, once the debounce lets it through) must not have the whole library filtered
        // and sorted again for it.
        userPreferencesRepository.userPreferences.map { state -> state.mapData { it.toListPreferences() } }.distinctUntilChanged(),
    ) { setlistsDataState, songsDataState, listPreferencesDataState ->

        fun createScreenData() = setlistsDataState.data?.sortedByDescending { it.priority }?.let { setlists ->
            songsDataState.data?.let { songs ->
                listPreferencesDataState.data?.let { listPreferences ->
                    ScreenData(
                        setlists = setlists,
                        songs = songs
                            .filterHasChords(listPreferences)
                            .sort(listPreferences),
                        songFileNames = songs.mapTo(mutableSetOf()) { it.fileName },
                    ).also {
                        cache = it
                    }
                }
            }
        }

        val dataStates = arrayOf(setlistsDataState, songsDataState, listPreferencesDataState)
        if (dataStates.any { it is DataState.Failure }) {
            DataState.Failure(createScreenData() ?: cache)
        } else if (dataStates.any { it is DataState.Loading }) {
            DataState.Loading(createScreenData() ?: cache)
        } else {
            DataState.Idle(createScreenData() ?: cache ?: throw IllegalStateException("No data available while all data states are idle."))
        }
    }.distinctUntilChanged()

    private fun List<Song>.filterHasChords(listPreferences: ListPreferences) = if (listPreferences.shouldShowSongsWithoutChords) this else filter { it.hasChords }

    /**
     * The selector of a comparator runs on every comparison, so sorting this way used to normalize each title and
     * artist a logarithmic number of times over. The keys are computed once per song here instead.
     */
    private fun List<Song>.sort(listPreferences: ListPreferences): List<Song> {
        val comparator = when (listPreferences.sortingMode) {
            UserPreferences.SortingMode.BY_ARTIST -> compareBy<SortableSong>({ it.artist }, { it.title })
            UserPreferences.SortingMode.BY_TITLE -> compareBy<SortableSong>({ it.title }, { it.artist })
        }
        return map { SortableSong(song = it, artist = normalizeText(it.artist), title = normalizeText(it.title)) }
            .sortedWith(comparator)
            .map { it.song }
    }

    private class SortableSong(
        val song: Song,
        val artist: String,
        val title: String,
    )

    /** The part of the preferences the song list depends on. */
    private data class ListPreferences(
        val shouldShowSongsWithoutChords: Boolean,
        val sortingMode: UserPreferences.SortingMode,
    )

    private fun UserPreferences.toListPreferences() = ListPreferences(
        shouldShowSongsWithoutChords = shouldShowSongsWithoutChords,
        sortingMode = sortingMode,
    )

    private fun <T, R> DataState<T>.mapData(transform: (T) -> R): DataState<R> = when (this) {
        is DataState.Idle -> DataState.Idle(transform(data))
        is DataState.Loading -> DataState.Loading(data?.let(transform))
        is DataState.Failure -> DataState.Failure(data?.let(transform))
    }
}
