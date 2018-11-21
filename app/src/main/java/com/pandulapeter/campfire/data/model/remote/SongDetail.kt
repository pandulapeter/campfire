package com.pandulapeter.campfire.data.model.remote

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = SongDetail.TABLE_NAME)
data class SongDetail(
    @PrimaryKey() @ColumnInfo(name = Song.ID) @SerializedName(Song.ID) val id: String,
    @ColumnInfo(name = VERSION) var version: Int = 0,
    @ColumnInfo(name = SONG) @SerializedName(SONG) val text: String = ""
) {
    companion object {
        const val TABLE_NAME = "songDetails"
        const val VERSION = "version"
        private const val SONG = "song"
    }
}