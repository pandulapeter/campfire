package com.pandulapeter.campfire.data.source.remote.implementation.networking

import app.softwork.serialization.csv.CSVFormat
import com.pandulapeter.campfire.data.source.remote.implementation.model.SongResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer

/**
 * Requests the same `gviz` CSV export Retrosheet would, and decodes it with the same CSV serialization format, so the
 * web build sees exactly the data the other platforms do.
 */
internal class NetworkManagerImpl(
    private val httpClient: HttpClient
) : NetworkManager {

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun loadSongs(databaseUrl: String) = CSVFormat.decodeFromString(
        deserializer = ListSerializer(SongResponse.serializer()),
        string = httpClient.get(databaseUrl.toSongSheetUrl()).bodyAsText()
    )

    private fun String.toSongSheetUrl() = "${trimEnd('/')}/gviz/tq?tqx=out:csv&sheet=${SongResponse.SHEET_NAME}"
}
