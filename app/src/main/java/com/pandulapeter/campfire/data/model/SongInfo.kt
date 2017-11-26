package com.pandulapeter.campfire.data.model

import com.google.gson.annotations.SerializedName

/**
 * Contains basic metadata about a song.
 */
data class SongInfo(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("language") val language: String = "en",
    @SerializedName("version") val version: Int = 0,
    @SerializedName("isExplicit") val isExplicit: Boolean = false)