package com.pandulapeter.campfire.data.network

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Wrapper for communicating with the server.
 */
class NetworkManager {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://campfire-test1.herokuapp.com")
        .client(OkHttpClient.Builder().build())
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
        .build()

    fun getService(): CampfireService = retrofit.create(CampfireService::class.java)
}