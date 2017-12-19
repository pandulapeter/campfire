package com.pandulapeter.campfire.data.model

import com.google.gson.annotations.SerializedName

/**
 * Describes a single playlist that references an ordered list of songs.
 */
data class Playlist(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String? = null,
    @SerializedName("songIds") val songIds: MutableList<String> = mutableListOf()) {

    companion object {
        const val FAVORITES_ID = 0
    }
}