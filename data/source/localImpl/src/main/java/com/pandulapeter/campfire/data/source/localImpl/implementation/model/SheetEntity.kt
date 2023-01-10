package com.pandulapeter.campfire.data.source.localImpl.implementation.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = SheetEntity.TABLE_NAME)
internal data class SheetEntity(
    @PrimaryKey @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "isActive") val isActive: Boolean,
    @ColumnInfo(name = "priority") val priority: Int
) {

    companion object {
        const val TABLE_NAME = "sheets"
    }
}