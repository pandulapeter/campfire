package com.pandulapeter.campfire.old.data.model

import com.google.gson.annotations.SerializedName

/**
 * Describes a single playlist that references an ordered list of songs.
 */
data class Playlist(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String? = null,
    @SerializedName("songIds") val songIds: List<String> = listOf()
) {

    companion object {
        const val FAVORITES_ID = 0
        const val MAXIMUM_PLAYLIST_COUNT = 16
    }
}