package com.pandulapeter.campfire.data.networking

import com.google.gson.GsonBuilder
import com.pandulapeter.campfire.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Wrapper for communicating with the server.
 */
class NetworkingManager {

    @Suppress("ConstantConditionIf")
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://localhost:8080")
        .client(OkHttpClient.Builder()
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
                }
            }
            .build())
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
        .build()

    fun getService() = retrofit.create(CampfireService::class.java)
}