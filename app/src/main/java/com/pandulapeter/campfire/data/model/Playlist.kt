package com.pandulapeter.campfire.data.model

/**
 * Describes a single playlist that references an ordered list of songs.
 */
sealed class Playlist(val songIds: List<String>) {

    class Favorites(songIds: List<String>) : Playlist(songIds)
    class Custom(val name: String, songIds: List<String>) : Playlist(songIds)
}