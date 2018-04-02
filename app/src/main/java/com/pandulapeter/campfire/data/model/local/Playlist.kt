package com.pandulapeter.campfire.data.model.local

data class Playlist(
    val id: String = FAVORITES_ID,
    val title: String? = null,
    val order: Int = 0,
    val songIds: List<String> = listOf()
) {
    companion object {
        const val FAVORITES_ID = "favorites"
    }
}