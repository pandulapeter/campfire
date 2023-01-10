package com.pandulapeter.campfire.data.source.localImpl.implementation.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = DatabaseEntity.TABLE_NAME)
internal data class DatabaseEntity(
    @PrimaryKey @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "isActive") val isActive: Boolean,
    @ColumnInfo(name = "priority") val priority: Int,
    @ColumnInfo(name = "isAddedByUser") val isAddedByUser: Boolean
) {

    companion object {
        const val TABLE_NAME = "sheets"
    }
}