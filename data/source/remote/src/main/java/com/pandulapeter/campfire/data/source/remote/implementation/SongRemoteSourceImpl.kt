package com.pandulapeter.campfire.data.source.remote.implementation

import com.pandulapeter.campfire.data.source.remote.api.SongRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementation.mapper.toModel
import com.pandulapeter.campfire.data.source.remote.implementation.networking.NetworkingService

internal class SongRemoteSourceImpl(
    private val networkingService: NetworkingService
) : SongRemoteSource {

    override suspend fun getSongs() = networkingService.getSongs().mapNotNull { it.toModel() }.distinctBy { it.id }
}