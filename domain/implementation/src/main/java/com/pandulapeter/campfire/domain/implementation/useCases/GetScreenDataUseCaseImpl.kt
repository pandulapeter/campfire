package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.repository.api.DatabaseRepository
import com.pandulapeter.campfire.data.repository.api.PlaylistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class GetScreenDataUseCaseImpl internal constructor(
    normalizeText: NormalizeTextUseCase,
    databaseRepository: DatabaseRepository,
    playlistRepository: PlaylistRepository,
    songRepository: SongRepository,
    userPreferencesRepository: UserPreferencesRepository
) : GetScreenDataUseCase {

    override operator fun invoke() = screenDataFlow

    private var cache: ScreenData? = null
    private val screenDataFlow = combine(
        databaseRepository.databases,
        playlistRepository.playlists,
        songRepository.songs,
        userPreferencesRepository.userPreferences
    ) { databasesDataState,
        playlistsDataState,
        songsDataState,
        userPreferencesDataState ->

        fun createScreenData() = databasesDataState.data?.sortedBy { it.priority }?.let { databases ->
            playlistsDataState.data?.sortedBy { it.priority }?.let { playlists ->
                songsDataState.data?.let { songs ->
                    userPreferencesDataState.data?.let { userPreferences ->
                        val filteredDatabases = databases.filter { it.isEnabled }.filter { !userPreferences.unselectedDatabaseUrls.contains(it.url) }
                        ScreenData(
                            databases = databases,
                            playlists = playlists,
                            songs = filteredDatabases.flatMap { songs[it.url].orEmpty() }
                                .distinctBy { it.id }
                                .filter { it.isPublic }
                                .filterExplicit(userPreferences)
                                .filterHasChords(userPreferences)
                                .sortedBy { normalizeText(it.title) }
                                .sortedBy { normalizeText(it.artist) },
                            userPreferences = userPreferences
                        ).also {
                            cache = it
                        }
                    }
                }
            }
        }

        val dataStates = arrayOf(
            databasesDataState,
            playlistsDataState,
            songsDataState,
            userPreferencesDataState
        )
        if (dataStates.any { it is DataState.Failure }) {
            DataState.Failure(createScreenData() ?: cache)
        } else if (dataStates.any { it is DataState.Loading }) {
            DataState.Loading(createScreenData() ?: cache)
        } else {
            DataState.Idle(createScreenData() ?: cache ?: throw IllegalStateException("No data available while all data states are idle."))
        }
    }.distinctUntilChanged()

    private fun List<Song>.filterExplicit(userPreferences: UserPreferences) = if (userPreferences.shouldShowExplicitSongs) this else filterNot { it.isExplicit }

    private fun List<Song>.filterHasChords(userPreferences: UserPreferences) = if (userPreferences.shouldShowSongsWithoutChords) this else filterNot { !it.hasChords }
}