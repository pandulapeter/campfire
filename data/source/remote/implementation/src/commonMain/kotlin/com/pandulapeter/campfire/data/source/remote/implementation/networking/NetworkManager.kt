package com.pandulapeter.campfire.data.source.remote.implementation.networking

import de.jensklingenberg.ktorfit.Ktorfit
import io.github.theapache64.retrosheet.core.RetrosheetConfig
import io.github.theapache64.retrosheet.core.RetrosheetConverter
import io.ktor.client.HttpClient

internal class NetworkManager(
    val httpClient: HttpClient,
    private val retrosheetConfig: RetrosheetConfig
) {
    private val songServices = mutableMapOf<String, SongService>()

    fun getSongService(databaseUrl: String) = songServices[databaseUrl] ?: createSongService(databaseUrl).also {
        songServices[databaseUrl] = it
    }

    private fun createSongService(databaseUrl: String): SongService = Ktorfit.Builder()
        .baseUrl(if (databaseUrl.endsWith("/")) databaseUrl else "$databaseUrl/")
        .httpClient(httpClient)
        .converterFactories(RetrosheetConverter(retrosheetConfig))
        .build()
        .createSongService()
}
