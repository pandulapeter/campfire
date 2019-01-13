package com.pandulapeter.campfire.data.networking

import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

interface CampfireService {

    @GET("${NetworkManager.API_VERSION}library")
    fun getSongs(): Call<List<Song>>

    @GET("${NetworkManager.API_VERSION}collections")
    fun getCollections(): Call<List<Collection>>

    @GET("${NetworkManager.API_VERSION}song")
    fun getSong(@Query("id") songId: String): Call<SongDetail>

    @PUT("${NetworkManager.API_VERSION}songOpened")
    fun openSong(@Query("id") songId: String): Call<Unit>

    @PUT("${NetworkManager.API_VERSION}collectionOpened")
    fun openCollection(@Query("id") collectionId: String): Call<Unit>
}