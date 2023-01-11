package com.pandulapeter.campfire.data.source.remote.implementation

import com.pandulapeter.campfire.data.source.remote.api.SongRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.remote.implementation.networking.NetworkManager

internal class SongRemoteSourceImpl(
    private val networkManager: NetworkManager
) : SongRemoteSource {

    override suspend fun loadSongs(databaseUrl: String) = networkManager.getNetworkingService(databaseUrl)
        .getSongs()
        .mapNotNull { it.toModel() }
        .distinctBy { it.id }
}