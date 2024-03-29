package com.pandulapeter.campfire.data.source.local.implementationAndroid.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = UserPreferencesEntity.TABLE_NAME)
internal data class UserPreferencesEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = TABLE_NAME,
    @ColumnInfo(name = "shouldShowExplicitSongs") val shouldShowExplicitSongs: Boolean,
    @ColumnInfo(name = "shouldShowSongsWithoutChords") val shouldShowSongsWithoutChords: Boolean,
    @ColumnInfo(name = "showOnlyDownloadedSongs") val showOnlyDownloadedSongs: Boolean,
    @ColumnInfo(name = "unselectedDatabaseUrls") val unselectedDatabaseUrls: String,
    @ColumnInfo(name = "sortingMode") val sortingMode: String,
    @ColumnInfo(name = "uiMode") val uiMode: String,
    @ColumnInfo(name = "language") val language: String
) {

    companion object {
        const val TABLE_NAME = "userPreferences"
    }
}