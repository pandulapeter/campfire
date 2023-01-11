package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Playlist
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {

    val playlists: Flow<DataState<List<Playlist>>>

    suspend fun loadPlaylistsIfNeeded() : List<Playlist>

    suspend fun savePlaylists(playlists: List<Playlist>)
}