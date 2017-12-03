package com.pandulapeter.campfire.data.model

import com.google.gson.annotations.SerializedName

/**
 * Describes a single playlist that references an ordered list of songs.
 */
sealed class Playlist(
    @SerializedName("id") val id: Int,
    @SerializedName("songIds") val songIds: MutableList<String>) {

    class Favorites(songIds: MutableList<String>) : Playlist(FAVORITES_ID, songIds)
    class Custom(id: Int, @SerializedName("name") val name: String, songIds: MutableList<String>) : Playlist(id, songIds)

    companion object {
        const val FAVORITES_ID = 0
    }
}