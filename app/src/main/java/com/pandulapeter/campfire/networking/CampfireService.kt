package com.pandulapeter.campfire.networking

import com.pandulapeter.campfire.data.model.SongDetail
import com.pandulapeter.campfire.data.model.SongInfo
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Defines the backend endpoints used by the application.
 */
interface CampfireService {

    /**
     * Returns the list of all the songs in the database.
     */
    @GET("/library")
    fun getLibrary(): Call<List<SongInfo>>

    /**
     * Returns the full, detailed object related to a single song.
     *
     * @param id - The ID of the song.
     */
    @GET("/song")
    fun getSong(@Query("id") id: String): Call<SongDetail>
}