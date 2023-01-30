package com.pandulapeter.campfire.data.source.remote.implementationJvm.source

import com.pandulapeter.campfire.data.source.remote.api.SongDetailsRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementationJvm.networking.NetworkManager

internal class SongDetailsRemoteSourceImpl(
    private val networkManager: NetworkManager
) : SongDetailsRemoteSource {

    override suspend fun loadSongDetails(url: String) = networkManager.songDetailsService.downloadFile(url).string()
}