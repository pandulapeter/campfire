package com.pandulapeter.campfire.networking

import com.pandulapeter.campfire.data.model.SongDetail
import com.pandulapeter.campfire.data.model.SongInfo
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * Defines the backend endpoints used by the application.
 */
interface CampfireService {

    /**
     * Returns the list of all the songs in the database.
     */
    @GET("/v1/library")
    fun getLibrary(): Call<List<SongInfo>>

    /**
     * Returns the full, detailed object related to a single song.
     *
     * @param id - The ID of the song.
     */
    @GET("/v1/song")
    fun getSong(@Query("id") id: String): Call<SongDetail>

    /**
     * Notifies the server that a song has been opened so it can track its popularity.
     *
     * @param id - The ID of the song.
     */
    @PUT("/v1/opened")
    fun openSong(@Query("id") id: String): Call<Unit> //TODO: Use this endpoint.
}