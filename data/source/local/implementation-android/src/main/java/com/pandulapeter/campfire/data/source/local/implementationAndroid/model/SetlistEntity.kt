package com.pandulapeter.campfire.data.source.local.implementationAndroid.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = SetlistEntity.TABLE_NAME)
internal data class SetlistEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "songIds") val songIds: String,
    @ColumnInfo(name = "priority") val priority: Int
) {

    companion object {
        const val TABLE_NAME = "setlists"
    }
}