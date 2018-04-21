package com.pandulapeter.campfire.data.networking

import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

interface CampfireService {

    @GET("/v1/library")
    fun getLibrary(): Call<List<Song>>

    @GET("/v1/collections")
    fun getCollections(): Call<List<Collection>>

    @GET("/v1/song")
    fun getSong(@Query("id") id: String): Call<SongDetail>

    @PUT("/v1/songOpened")
    fun openSong(@Query("id") id: String): Call<Unit>

    @PUT("/v1/collectionOpened")
    fun openCollection(@Query("id") id: String): Call<Unit>
}