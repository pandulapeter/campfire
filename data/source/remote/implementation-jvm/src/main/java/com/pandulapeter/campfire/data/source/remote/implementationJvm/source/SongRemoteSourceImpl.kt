package com.pandulapeter.campfire.data.source.remote.implementationJvm.source

import com.pandulapeter.campfire.data.source.remote.api.SongRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementationJvm.mapper.toModel
import com.pandulapeter.campfire.data.source.remote.implementationJvm.networking.NetworkManager

internal class SongRemoteSourceImpl(
    private val networkManager: NetworkManager
) : SongRemoteSource {

    override suspend fun loadSongs(databaseUrl: String) = networkManager.getSongService(databaseUrl)
        .getSongs()
        .mapNotNull { it.toModel() }
        .distinctBy { it.id }
}