package com.pandulapeter.campfire.data.source.remote.implementation

import com.pandulapeter.campfire.data.source.remote.api.CollectionRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.remote.implementation.networking.NetworkManager

internal class CollectionRemoteSourceImpl(
    private val networkManager: NetworkManager
) : CollectionRemoteSource {

    override suspend fun loadCollections(databaseUrl: String) = networkManager.getNetworkingService(databaseUrl)
        .getCollections()
        .mapNotNull { it.toModel() }
        .distinctBy { it.id }
}