package com.pandulapeter.campfire.data.source.remote.implementation.source

import com.pandulapeter.campfire.data.source.remote.api.RawSongDetailsRemoteSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

internal class RawSongDetailsRemoteSourceImpl(
    private val httpClient: HttpClient
) : RawSongDetailsRemoteSource {

    override suspend fun loadRawSongDetails(url: String) = httpClient.get(url).bodyAsText()
}
