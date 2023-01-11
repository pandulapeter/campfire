package com.pandulapeter.campfire.data.source.localImpl.implementation.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = UserPreferencesEntity.TABLE_NAME)
internal data class UserPreferencesEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = TABLE_NAME,
    @ColumnInfo(name = "unselectedDatabaseUrls") val unselectedDatabaseUrls: String,
) {

    companion object {
        const val TABLE_NAME = "userPreferences"
    }
}