package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.model.domain.Playlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.repository.api.CollectionRepository
import com.pandulapeter.campfire.data.repository.api.DatabaseRepository
import com.pandulapeter.campfire.data.repository.api.PlaylistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.models.ScreenData
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class GetScreenDataUseCase internal constructor(
    collectionRepository: CollectionRepository,
    databaseRepository: DatabaseRepository,
    playlistRepository: PlaylistRepository,
    songRepository: SongRepository
) {
    operator fun invoke() = screenDataFlow

    private var cache: ScreenData? = null
    private val screenDataFlow = combine(
        collectionRepository.collections,
        databaseRepository.databases,
        playlistRepository.playlists,
        songRepository.songs
    ) { collectionsDataState,
        databasesDataState,
        playlistsDataState,
        songsDataState ->
        if (
            collectionsDataState is DataState.Failure ||
            databasesDataState is DataState.Failure ||
            playlistsDataState is DataState.Failure ||
            songsDataState is DataState.Failure
        ) {
            DataState.Failure(
                createScreenData(
                    collectionsDataState = collectionsDataState,
                    databasesDataState = databasesDataState,
                    playlistsDataState = playlistsDataState,
                    songsDataState = songsDataState
                ) ?: cache
            )
        } else if (
            collectionsDataState is DataState.Loading ||
            databasesDataState is DataState.Loading ||
            playlistsDataState is DataState.Loading ||
            songsDataState is DataState.Loading
        ) {
            DataState.Loading(
                createScreenData(
                    collectionsDataState = collectionsDataState,
                    databasesDataState = databasesDataState,
                    playlistsDataState = playlistsDataState,
                    songsDataState = songsDataState
                ) ?: cache
            )
        } else {
            DataState.Idle(
                createScreenData(
                    collectionsDataState = collectionsDataState,
                    databasesDataState = databasesDataState,
                    playlistsDataState = playlistsDataState,
                    songsDataState = songsDataState
                )
            )
        }
    }.distinctUntilChanged()

    private fun createScreenData(
        collectionsDataState: DataState<List<Collection>>,
        databasesDataState: DataState<List<Database>>,
        playlistsDataState: DataState<List<Playlist>>,
        songsDataState: DataState<List<Song>>
    ) = collectionsDataState.data?.let { collections ->
        databasesDataState.data?.let { databases ->
            playlistsDataState.data?.let { playlists ->
                songsDataState.data?.let { songs ->
                    ScreenData(
                        collections = collections,
                        databases = databases,
                        playlists = playlists,
                        songs = songs
                    ).also {
                        cache = it
                    }
                }
            }
        }
    }
}