package com.pandulapeter.campfire.old.networking

import com.google.gson.Gson
import com.pandulapeter.campfire.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Wrapper for communicating with the server.
 */
class NetworkManager(gson: Gson) {

    val service: CampfireService = Retrofit.Builder()
            .baseUrl("https://campfire-test1.herokuapp.com/")
            .client(OkHttpClient.Builder().apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
                }
            }.build())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(CampfireService::class.java)
}