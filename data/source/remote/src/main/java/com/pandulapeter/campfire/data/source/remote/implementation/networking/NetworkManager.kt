package com.pandulapeter.campfire.data.source.remote.implementation.networking

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

internal class NetworkManager(
    private val okHttpClient: OkHttpClient,
    private val moshiConverterFactory: MoshiConverterFactory
) {
    private val networkingServices = mutableMapOf<String, NetworkingService>()

    fun getNetworkingService(sheetUrl: String) = networkingServices[sheetUrl] ?: createNetworkingService(sheetUrl).also {
        networkingServices[sheetUrl] = it
    }

    private fun createNetworkingService(sheetUrl: String): NetworkingService = Retrofit.Builder()
        .baseUrl(sheetUrl)
        .client(okHttpClient)
        .addConverterFactory(moshiConverterFactory)
        .build()
        .create(NetworkingService::class.java)
}