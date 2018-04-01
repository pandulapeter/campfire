package com.pandulapeter.campfire.data.model

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity(tableName = SongDetail.TABLE_NAME)
data class SongDetail(
    @PrimaryKey() @ColumnInfo(name = Song.ID) val id: String,
    val version: Int = 0,
    val text: String = ""
) {
    companion object {
        const val TABLE_NAME = "songDetails"
    }
}