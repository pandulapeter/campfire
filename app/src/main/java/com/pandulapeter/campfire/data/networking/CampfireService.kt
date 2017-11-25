package com.pandulapeter.campfire.data.networking

import com.pandulapeter.campfire.data.model.SongDetail
import com.pandulapeter.campfire.data.model.SongInfo
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface CampfireService {

    @GET("/library")
    fun getLibrary(): Call<List<SongInfo>>

    @GET("/song")
    fun getSong(@Query("id") id: String): Call<SongDetail>
}