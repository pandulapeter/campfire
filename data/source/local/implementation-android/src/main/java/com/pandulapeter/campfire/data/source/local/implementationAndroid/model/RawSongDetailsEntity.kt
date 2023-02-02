package com.pandulapeter.campfire.data.source.local.implementationAndroid.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = RawSongDetailsEntity.TABLE_NAME)
internal data class RawSongDetailsEntity(
    @PrimaryKey @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "rawData") val rawData: String,
) {

    companion object {
        const val TABLE_NAME = "rawSongDetails"
    }
}