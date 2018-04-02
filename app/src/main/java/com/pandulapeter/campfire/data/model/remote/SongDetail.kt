package com.pandulapeter.campfire.data.model.remote

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = SongDetail.TABLE_NAME)
data class SongDetail(
    @PrimaryKey() @ColumnInfo(name = Song.ID) val id: String,
    @ColumnInfo(name = VERSION) var version: Int = 0,
    @SerializedName("song") val text: String = ""
) {
    companion object {
        const val TABLE_NAME = "songDetails"
        const val VERSION = "version"
    }
}