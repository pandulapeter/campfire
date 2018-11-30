package com.pandulapeter.campfire.data.networking

import androidx.annotation.Keep
import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Keep
class NetworkManager(gson: Gson) {

    val service: CampfireService = Retrofit.Builder()
        .baseUrl("https://campfire-test1.herokuapp.com/")
        .client(OkHttpClient.Builder().build())
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(CampfireService::class.java)
}