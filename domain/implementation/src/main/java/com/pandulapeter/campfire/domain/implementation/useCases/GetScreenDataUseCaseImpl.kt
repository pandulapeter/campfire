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
        userPreferencesState ->

        fun createScreenData() = collectionsDataState.data?.let { collections ->
            databasesDataState.data?.let { databases ->
                playlistsDataState.data?.let { playlists ->
                    songsDataState.data?.let { songs ->
                        userPreferencesState.data?.let { userPreferences ->
                            ScreenData(
                                collections = collections.distinctBy { it.id }.filter { it.isPublic },
                                databases = databases.sortedBy { it.priority },
                                playlists = playlists.sortedBy { it.priority },
                                songs = songs.distinctBy { it.id }.filter { it.isPublic },
                                userPreferences = userPreferences
                            ).also {
                                cache = it
                            }
                        }
                    }
                }
            }
        }

        if (
            collectionsDataState is DataState.Failure ||
            databasesDataState is DataState.Failure ||
            playlistsDataState is DataState.Failure ||
            songsDataState is DataState.Failure ||
            userPreferencesState is DataState.Failure
        ) {
            DataState.Failure(createScreenData() ?: cache)
        } else if (
            collectionsDataState is DataState.Loading ||
            databasesDataState is DataState.Loading ||
            playlistsDataState is DataState.Loading ||
            songsDataState is DataState.Loading ||
            userPreferencesState is DataState.Loading
        ) {
            DataState.Loading(createScreenData() ?: cache)
        } else {
            DataState.Idle(createScreenData()!!)
        }
    }.distinctUntilChanged()
}