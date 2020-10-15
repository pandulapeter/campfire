package com.pandulapeter.campfire.data.networking

import com.google.gson.Gson
import com.pandulapeter.beagle.logOkHttp.BeagleOkHttpLogger
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class NetworkManager(gson: Gson) {

    val service: CampfireService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(
            OkHttpClient.Builder()
                .apply { (BeagleOkHttpLogger.logger as? Interceptor?)?.let { addInterceptor(it) } }
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(CampfireService::class.java)

    companion object {
        const val BASE_URL = "https://campfire-test1.herokuapp.com/"
        const val API_VERSION = "v1/"
    }
}