package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.repository.api.CollectionRepository
import com.pandulapeter.campfire.data.repository.api.DatabaseRepository
import com.pandulapeter.campfire.data.repository.api.PlaylistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class GetScreenDataUseCaseImpl internal constructor(
    collectionRepository: CollectionRepository,
    databaseRepository: DatabaseRepository,
    playlistRepository: PlaylistRepository,
    songRepository: SongRepository,
    userPreferencesRepository: UserPreferencesRepository
) : GetScreenDataUseCase {

    override operator fun invoke() = screenDataFlow

    private var cache: ScreenData? = null
    private val screenDataFlow = combine(
        collectionRepository.collections,
        databaseRepository.databases,
        playlistRepository.playlists,
        songRepository.songs,
        userPreferencesRepository.userPreferences
    ) { collectionsDataState,
        databasesDataState,
        playlistsDataState,
        songsDataState,
        userPreferencesDataState ->

        fun createScreenData() = collectionsDataState.data?.let { collections ->
            databasesDataState.data?.sortedBy { it.priority }?.let { databases ->
                playlistsDataState.data?.sortedBy { it.priority }?.let { playlists ->
                    songsDataState.data?.let { songs ->
                        userPreferencesDataState.data?.let { userPreferences ->
                            val filteredDatabases = databases.filter { it.isEnabled }.filter { !userPreferences.unselectedDatabaseUrls.contains(it.url) }
                            ScreenData(
                                collections = filteredDatabases.flatMap { collections[it.url].orEmpty() }.distinctBy { it.id }.filter { it.isPublic },
                                databases = databases,
                                playlists = playlists,
                                songs = filteredDatabases.flatMap { songs[it.url].orEmpty() }.distinctBy { it.id }.filter { it.isPublic },
                                userPreferences = userPreferences
                            ).also {
                                cache = it
                            }
                        }
                    }
                }
            }
        }

        val dataStates = arrayOf(
            collectionsDataState,
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
}