package com.pandulapeter.campfire.data.source.remote.implementationJvm

import com.github.theapache64.retrosheet.RetrosheetInterceptor
import com.pandulapeter.campfire.data.source.remote.api.SongRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementationJvm.model.SongResponse
import com.pandulapeter.campfire.data.source.remote.implementationJvm.networking.NetworkManager
import com.pandulapeter.campfire.data.source.remote.implementationJvm.source.SongRemoteSourceImpl
import okhttp3.OkHttpClient
import org.koin.dsl.module
import retrofit2.converter.moshi.MoshiConverterFactory

val dataRemoteSourceJvmModule = module {
    single {
        RetrosheetInterceptor.Builder().run {
            SongResponse.addSheet(this)
        }.build()
    }
    single {
        OkHttpClient.Builder()
            .addInterceptor(get<RetrosheetInterceptor>())
            .build()
    }
    single { MoshiConverterFactory.create() }
    single { NetworkManager(get(), get()) }
    factory<SongRemoteSource> { SongRemoteSourceImpl(get()) }
}