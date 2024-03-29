package com.pandulapeter.campfire.data.source.local.implementationDesktop.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

internal class UserPreferencesEntity : RealmObject {

    @PrimaryKey var id: String = TABLE_NAME
    var shouldShowExplicitSongs: Boolean = false
    var shouldShowSongsWithoutChords: Boolean = false
    var showOnlyDownloadedSongs: Boolean = false
    var unselectedDatabaseUrls: String = ""
    var sortingMode: String = ""
    var uiMode: String = ""
    var language: String = ""

    companion object {
        const val TABLE_NAME = "userPreferences"
    }
}