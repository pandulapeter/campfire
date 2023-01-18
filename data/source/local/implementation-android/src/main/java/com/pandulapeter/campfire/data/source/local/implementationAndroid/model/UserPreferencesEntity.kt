package com.pandulapeter.campfire.data.source.local.implementationAndroid.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = UserPreferencesEntity.TABLE_NAME)
internal data class UserPreferencesEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = TABLE_NAME,
    @ColumnInfo(name = "unselectedDatabaseUrls") val unselectedDatabaseUrls: String,
    @ColumnInfo(name = "shouldShowExplicitSongs") val shouldShowExplicitSongs: Boolean
) {

    companion object {
        const val TABLE_NAME = "userPreferences"
    }
}