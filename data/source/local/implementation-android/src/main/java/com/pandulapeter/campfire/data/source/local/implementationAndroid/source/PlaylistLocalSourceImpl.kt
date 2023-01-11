package com.pandulapeter.campfire.data.source.local.implementationAndroid.source

import com.pandulapeter.campfire.data.model.domain.Playlist
import com.pandulapeter.campfire.data.source.local.api.PlaylistLocalSource
import com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper.toEntity
import com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper.toModel
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao.PlaylistDao

internal class PlaylistLocalSourceImpl(
    private val playlistDao: PlaylistDao
) : PlaylistLocalSource {

    override suspend fun loadPlaylists() = playlistDao.getAll().map { it.toModel() }

    override suspend fun savePlaylists(playlists: List<Playlist>) = playlistDao.updateAll(playlists.map { it.toEntity() })
}