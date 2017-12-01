package com.pandulapeter.campfire.data.model

import com.google.gson.annotations.SerializedName

/**
 * Contains basic metadata about a downloaded song.
 */
data class DownloadedSong(
    @SerializedName("id") val id: String,
    @SerializedName("version") val version: Int = 0)