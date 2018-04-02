package com.pandulapeter.campfire.data.networking

import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

interface CampfireService {

    @GET("/v1/library")
    fun getLibrary(): Call<List<Song>>

    @GET("/v1/song")
    fun getSong(@Query("id") id: String): Call<SongDetail>

    @PUT("/v1/opened")
    fun openSong(@Query("id") id: String): Call<Unit>
}