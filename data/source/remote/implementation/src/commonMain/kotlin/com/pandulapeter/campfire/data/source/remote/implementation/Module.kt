package com.pandulapeter.campfire.data.source.remote.implementation

import com.pandulapeter.campfire.data.source.remote.api.RawSongDetailsRemoteSource
import com.pandulapeter.campfire.data.source.remote.api.SongRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementation.model.SongResponse
import com.pandulapeter.campfire.data.source.remote.implementation.networking.NetworkManager
import com.pandulapeter.campfire.data.source.remote.implementation.source.RawSongDetailsRemoteSourceImpl
import com.pandulapeter.campfire.data.source.remote.implementation.source.SongRemoteSourceImpl
import io.github.theapache64.retrosheet.core.RetrosheetConfig
import io.github.theapache64.retrosheet.core.createRetrosheetPlugin
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import org.koin.dsl.module

val dataRemoteSourceModule = module {
    single {
        RetrosheetConfig.Builder().run {
            SongResponse.addSheet(this)
        }.build()
    }
    single {
        HttpClient {
            install(createRetrosheetPlugin(get<RetrosheetConfig>()))
            install(ContentNegotiation) {
                json()
            }
        }
    }
    single { NetworkManager(get(), get()) }
    factory<SongRemoteSource> { SongRemoteSourceImpl(get()) }
    factory<RawSongDetailsRemoteSource> { RawSongDetailsRemoteSourceImpl(get()) }
}
