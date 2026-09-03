package com.pandulapeter.campfire.data.source.remote.implementation.source

import com.pandulapeter.campfire.data.source.remote.api.RawSongDetailsRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementation.networking.NetworkManager
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

internal class RawSongDetailsRemoteSourceImpl(
    private val networkManager: NetworkManager
) : RawSongDetailsRemoteSource {

    override suspend fun loadRawSongDetails(url: String) = networkManager.httpClient.get(url).bodyAsText()
}
