package com.pandulapeter.campfire.data.source.remote.implementationJvm.source

import com.pandulapeter.campfire.data.source.remote.api.RawSongDetailsRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementationJvm.networking.NetworkManager

internal class RawSongDetailsRemoteSourceImpl(
    private val networkManager: NetworkManager
) : RawSongDetailsRemoteSource {

    override suspend fun loadRawSongDetails(url: String) = networkManager.rawSongDetailsService.downloadFile(url).string()
}