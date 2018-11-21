package com.pandulapeter.campfire.data.model.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = Playlist.TABLE_NAME)
data class Playlist(
    @PrimaryKey() @ColumnInfo(name = ID) var id: String = FAVORITES_ID,
    @ColumnInfo(name = "title") var title: String? = null,
    @ColumnInfo(name = ORDER) var order: Int = 0,
    @ColumnInfo(name = "songIds") var songIds: MutableList<String> = mutableListOf()
) {
    companion object {
        const val FAVORITES_ID = "favorites"
        const val TABLE_NAME = "playlists"
        const val ID = "id"
        const val ORDER = "order"
        const val MAXIMUM_PLAYLIST_COUNT = 20
    }
}