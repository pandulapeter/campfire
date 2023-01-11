package com.pandulapeter.campfire.data.source.remote.implementationJvm.source

import com.pandulapeter.campfire.data.source.remote.api.CollectionRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementationJvm.mapper.toModel
import com.pandulapeter.campfire.data.source.remote.implementationJvm.networking.NetworkManager

internal class CollectionRemoteSourceImpl(
    private val networkManager: NetworkManager
) : CollectionRemoteSource {

    override suspend fun loadCollections(databaseUrl: String) = networkManager.getNetworkingService(databaseUrl)
        .getCollections()
        .mapNotNull { it.toModel() }
        .distinctBy { it.id }
}