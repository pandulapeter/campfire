package com.pandulapeter.campfire.data.source.localImpl.implementation

import com.pandulapeter.campfire.data.model.domain.Playlist
import com.pandulapeter.campfire.data.source.local.PlaylistLocalSource
import com.pandulapeter.campfire.data.source.localImpl.implementation.mapper.toEntity
import com.pandulapeter.campfire.data.source.localImpl.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.localImpl.implementation.storage.dao.PlaylistDao

internal class PlaylistLocalSourceImpl(
    private val playlistDao: PlaylistDao
) : PlaylistLocalSource {

    override suspend fun loadPlaylists() = playlistDao.getAll().map { it.toModel() }

    override suspend fun savePlaylists(playlists: List<Playlist>) = playlistDao.updateAll(playlists.map { it.toEntity() })
}