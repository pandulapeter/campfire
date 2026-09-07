package com.pandulapeter.campfire.data.source.remote.implementation

import com.pandulapeter.campfire.data.source.remote.api.RawSongDetailsRemoteSource
import com.pandulapeter.campfire.data.source.remote.api.SongRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementation.networking.NetworkManager
import com.pandulapeter.campfire.data.source.remote.implementation.networking.NetworkManagerImpl
import com.pandulapeter.campfire.data.source.remote.implementation.source.RawSongDetailsRemoteSourceImpl
import com.pandulapeter.campfire.data.source.remote.implementation.source.SongRemoteSourceImpl
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import org.koin.core.module.Module
import org.koin.dsl.module

actual val dataRemoteSourceModule: Module = module {
    single {
        HttpClient {
            install(ContentNegotiation) {
                json()
            }
        }
    }
    single<NetworkManager> { NetworkManagerImpl(get()) }
    factory<SongRemoteSource> { SongRemoteSourceImpl(get()) }
    factory<RawSongDetailsRemoteSource> { RawSongDetailsRemoteSourceImpl(get()) }
}
