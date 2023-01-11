package com.pandulapeter.campfire.data.source.remote.implementationJvm.networking

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

internal class NetworkManager(
    private val okHttpClient: OkHttpClient,
    private val moshiConverterFactory: MoshiConverterFactory
) {
    private val networkingServices = mutableMapOf<String, NetworkingService>()

    fun getNetworkingService(databaseUrl: String) = networkingServices[databaseUrl] ?: createNetworkingService(databaseUrl).also {
        networkingServices[databaseUrl] = it
    }

    private fun createNetworkingService(databaseUrl: String): NetworkingService = Retrofit.Builder()
        .baseUrl(databaseUrl)
        .client(okHttpClient)
        .addConverterFactory(moshiConverterFactory)
        .build()
        .create(NetworkingService::class.java)
}