package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Playlist
import com.pandulapeter.campfire.data.repository.api.PlaylistRepository
import com.pandulapeter.campfire.data.repository.implementation.base.BaseLocalDataRepository
import com.pandulapeter.campfire.data.source.local.api.PlaylistLocalSource

internal class PlaylistRepositoryImpl(
    playlistLocalSource: PlaylistLocalSource
) : BaseLocalDataRepository<List<Playlist>>(
    loadDataFromLocalSource = playlistLocalSource::loadPlaylists,
    saveDataToLocalSource = playlistLocalSource::savePlaylists
), PlaylistRepository {

    override val playlists = dataState

    override suspend fun loadPlaylistsIfNeeded() = loadDataIfNeeded()

    override suspend fun savePlaylists(playlists: List<Playlist>) = saveData(playlists)
}