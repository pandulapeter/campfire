package com.pandulapeter.campfire.data.model

import com.google.gson.annotations.SerializedName

/**
 * Contains the ID of the song and the raw String that needs to be parsed for the lyrics and the chords.
 */
data class SongDetail(
    @SerializedName("id") val id: String,
    @SerializedName("song") val song: String)