package com.pandulapeter.campfire.data.source.remote.implementationJvm.networking

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

internal class NetworkManager(
    private val okHttpClient: OkHttpClient,
    private val moshiConverterFactory: MoshiConverterFactory
) {
    private val songServices = mutableMapOf<String, SongService>()
    val songDetailsService: SongDetailsService by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.pandulapeter.com/") // Will never be used
            .client(okHttpClient)
            .build()
            .create(SongDetailsService::class.java)
    }

    fun getSongService(databaseUrl: String) = songServices[databaseUrl] ?: createSongService(databaseUrl).also {
        songServices[databaseUrl] = it
    }

    private fun createSongService(databaseUrl: String): SongService = Retrofit.Builder()
        .baseUrl(databaseUrl)
        .client(okHttpClient)
        .addConverterFactory(moshiConverterFactory)
        .build()
        .create(SongService::class.java)
}