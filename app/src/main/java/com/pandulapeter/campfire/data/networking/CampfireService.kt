package com.pandulapeter.campfire.data.networking

import retrofit2.Call
import retrofit2.http.GET

interface CampfireService {

    @GET("/library")
    fun getLibrary(): Call<Unit>

    @GET("/song")
    fun getSong(): Call<Unit>
}