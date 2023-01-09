package com.pandulapeter.campfire.data.source.remote

import com.github.theapache64.retrosheet.RetrosheetInterceptor
import com.pandulapeter.campfire.data.source.remote.api.CollectionRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementation.CollectionRemoteSourceImpl
import com.pandulapeter.campfire.data.source.remote.implementation.model.CollectionResponse
import com.pandulapeter.campfire.data.source.remote.implementation.networking.NetworkManager
import okhttp3.OkHttpClient
import org.koin.dsl.module
import retrofit2.converter.moshi.MoshiConverterFactory

val dataRemoteSourceModule = module {
    single {
        RetrosheetInterceptor.Builder()
            .run {
                CollectionResponse.addSheet(this)
            }
            .build()
    }
    single {
        OkHttpClient.Builder()
            .addInterceptor(get<RetrosheetInterceptor>())
            .build()
    }
    single { MoshiConverterFactory.create() }
    single { NetworkManager(get(), get()).networkingService }
    factory<CollectionRemoteSource> { CollectionRemoteSourceImpl(get()) }
}