package com.pandulapeter.campfire.data.source.localImpl.implementation.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = CollectionEntity.TABLE_NAME)
internal data class CollectionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "thumbnailUrl") val thumbnailUrl: String,
    @ColumnInfo(name = "songIds") val songIds: String,
    @ColumnInfo(name = "isPublic") val isPublic: Boolean
) {

    companion object {
        const val TABLE_NAME = "collections"
    }
}